package com.paperagent.service;

import com.paperagent.dto.ReviewContinueRequest;
import com.paperagent.dto.ReviewSessionResponse;
import com.paperagent.dto.ReviewStartEvent;
import com.paperagent.dto.ReviewStartRequest;
import com.paperagent.entity.Paper;
import com.paperagent.entity.PaperChunk;
import com.paperagent.entity.ReviewSession;
import com.paperagent.repository.PaperChunkRepository;
import com.paperagent.repository.PaperRepository;
import com.paperagent.repository.ReviewSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ChatClient chatClient;
    private final ReviewSessionRepository sessionRepository;
    private final PaperRepository paperRepository;
    private final PaperChunkRepository chunkRepository;
    private final TemplateService templateService;

    @Transactional
    public Flux<ReviewStartEvent> startReview(ReviewStartRequest request) {
        Long paperId = request.paperId();
        Long templateId = request.templateId();

        // Load paper full text from chunks
        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new IllegalArgumentException("Paper not found: " + paperId));
        List<PaperChunk> chunks = chunkRepository.findByPaperIdOrderByChunkIndex(paperId);
        String fullText = chunks.stream()
                .map(PaperChunk::getContent)
                .collect(Collectors.joining("\n\n"));

        if (fullText.isBlank()) {
            return Flux.just(ReviewStartEvent.error("The paper has no parsed content. Please wait for processing to complete."));
        }

        // Load template content
        String templateContent = templateService.getTemplateContent(templateId);

        // Create session
        ReviewSession session = sessionRepository.save(ReviewSession.builder()
                .paperId(paperId)
                .templateId(templateId)
                .status("in_progress")
                .build());

        // Build review prompt
        String prompt = buildReviewPrompt(templateContent, fullText, paper.getTitle());

        // Stream review
        StringBuilder resultBuffer = new StringBuilder();
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .map(chunk -> {
                    resultBuffer.append(chunk);
                    return ReviewStartEvent.text(chunk);
                })
                .concatWithValues(ReviewStartEvent.done())
                .doOnComplete(() -> {
                    session.setResultText(resultBuffer.toString());
                    session.setStatus("completed");
                    sessionRepository.save(session);
                    log.info("Review session {} completed for paper {}", session.getId(), paperId);
                })
                .doOnError(err -> {
                    session.setStatus("error");
                    session.setResultText(resultBuffer.toString());
                    sessionRepository.save(session);
                    log.error("Review session {} failed", session.getId(), err);
                });
    }

    public String continueReview(ReviewContinueRequest request) {
        ReviewSession session = sessionRepository.findById(request.sessionId())
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + request.sessionId()));

        String prompt = """
                You are a rigorous academic paper reviewer. The user has a follow-up question about your previous review.

                Previous review:
                %s

                User question:
                %s

                Rules:
                1. Answer strictly based on the paper content referenced in the review above
                2. Every point must cite specific passages from the paper
                3. Use 【原文段落X：...】format for citations
                4. If the paper doesn't contain enough information, say "（当前文本无法判断）"
                5. Use Chinese, professional and constructive tone
                """.formatted(
                        truncate(session.getResultText(), 6000),
                        request.message()
                );

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    public List<ReviewSessionResponse> listSessions() {
        return sessionRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public ReviewSessionResponse getSession(Long id) {
        ReviewSession s = sessionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + id));
        return toResponse(s);
    }

    @Transactional
    public void deleteSession(Long id) {
        sessionRepository.deleteById(id);
    }

    private String buildReviewPrompt(String templateContent, String fullText, String paperTitle) {
        // Truncate paper text if excessively long (keep first ~25000 chars for review)
        String paperText = fullText.length() > 25000
                ? fullText.substring(0, 25000) + "\n\n... (truncated for review)"
                : fullText;

        return """
                You are a rigorous academic paper reviewer. Please review the following paper according to the review criteria below.

                ## Paper Title
                %s

                ## Review Criteria (review template)
                %s

                ## Paper Content
                %s

                ## Instructions
                1. Review the paper item by item according to the criteria above.
                2. **Every review point — positive or negative — MUST quote the relevant passage from the paper** using the format 【原文段落X：...】. Literally copy the passage.
                3. For each criterion item, state your assessment clearly, then provide the text evidence.
                4. If the paper does not contain enough information to evaluate a criterion, write "（当前文本无法判断）" and explain what is missing.
                5. Be specific and constructive. Do not make vague claims.
                6. Use Chinese for all commentary. Keep a professional, helpful tone.
                7. At the end, provide an overall assessment paragraph.

                Start your review now. Follow the criteria order as listed above.
                """.formatted(paperTitle, templateContent, paperText);
    }

    private ReviewSessionResponse toResponse(ReviewSession s) {
        Paper paper = paperRepository.findById(s.getPaperId()).orElse(null);
        String templateName;
        try {
            templateName = templateService.getTemplate(s.getTemplateId()).name();
        } catch (Exception e) {
            templateName = "(deleted)";
        }
        return new ReviewSessionResponse(
                s.getId(),
                s.getPaperId(),
                paper != null ? paper.getTitle() : "(deleted)",
                s.getTemplateId(),
                templateName,
                s.getStatus(),
                s.getResultText(),
                s.getCreatedAt()
        );
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
