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
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int MAX_SECTION_TEXT_CHARS = 5_000;
    private static final int MAX_PRIOR_REVIEW_CHARS = 2_400;
    private static final int MAX_FINAL_REVIEW_CONTEXT_CHARS = 18_000;

    private final ChatClient chatClient;
    private final ReviewSessionRepository sessionRepository;
    private final PaperRepository paperRepository;
    private final PaperChunkRepository chunkRepository;
    private final TemplateService templateService;

    @Transactional
    public Flux<ReviewStartEvent> startReview(ReviewStartRequest request) {
        Long paperId = request.paperId();
        Long templateId = request.templateId();

        Paper paper = paperRepository.findById(paperId)
                .orElseThrow(() -> new IllegalArgumentException("Paper not found: " + paperId));
        List<PaperChunk> chunks = chunkRepository.findByPaperIdOrderByChunkIndex(paperId);
        String fullText = rebuildFullTextFromChunks(chunks);

        if (fullText.isBlank()) {
            return Flux.just(ReviewStartEvent.error("The paper has no parsed content. Please wait for processing to complete."));
        }

        String templateContent = templateService.getTemplateContent(templateId);
        ReviewSession session = sessionRepository.save(ReviewSession.builder()
                .paperId(paperId)
                .templateId(templateId)
                .status("in_progress")
                .build());
        List<ReviewSection> sections = extractReviewSections(fullText);

        return Flux.<ReviewStartEvent>create(sink -> {
            StringBuilder resultBuffer = new StringBuilder();
            List<String> priorReviewSummaries = new ArrayList<>();
            try {
                sink.next(ReviewStartEvent.status("Review request accepted.", "accepted", null, null, sections.size()));
                sink.next(ReviewStartEvent.status(
                        "识别到 " + sections.size() + " 个论文章节，准备逐节评审。",
                        "outline",
                        null,
                        0,
                        sections.size()
                ));

                for (int i = 0; i < sections.size(); i++) {
                    if (sink.isCancelled()) {
                        return;
                    }
                    ReviewSection section = sections.get(i);
                    int current = i + 1;
                    sink.next(ReviewStartEvent.status(
                            "正在评审：" + section.title() + " (" + current + "/" + sections.size() + ")",
                            "section_review",
                            section.title(),
                            current,
                            sections.size()
                    ));

                    String review = generateSectionReview(templateContent, paper.getTitle(), section, priorReviewSummaries);
                    appendSectionReview(resultBuffer, section.title(), review);
                    priorReviewSummaries.add(summarizeForNextSection(section.title(), review));
                    sink.next(ReviewStartEvent.text("\n\n## " + section.title() + "\n" + review.trim() + "\n"));
                }

                if (sink.isCancelled()) {
                    return;
                }
                sink.next(ReviewStartEvent.status(
                        "正在生成总结性评审。",
                        "final_summary",
                        null,
                        sections.size(),
                        sections.size()
                ));
                String finalReview = generateFinalReview(templateContent, paper.getTitle(), resultBuffer.toString());
                resultBuffer.append("\n\n# 总结性评审\n").append(finalReview.trim()).append("\n");
                sink.next(ReviewStartEvent.text("\n\n# 总结性评审\n" + finalReview.trim() + "\n"));

                session.setResultText(resultBuffer.toString());
                session.setStatus("completed");
                sessionRepository.save(session);
                log.info("Review session {} completed for paper {}", session.getId(), paperId);
                sink.next(ReviewStartEvent.done());
                sink.complete();
            } catch (Throwable err) {
                session.setStatus("error");
                session.setResultText(resultBuffer.toString());
                sessionRepository.save(session);
                log.error("Review session {} failed", session.getId(), err);
                sink.next(ReviewStartEvent.error(modelErrorMessage(err)));
                sink.complete();
            }
        }).subscribeOn(Schedulers.boundedElastic());
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
                1. Answer strictly based on the paper content referenced in the review above.
                2. Every point must cite specific passages that already appear in the review.
                3. If the existing review does not contain enough evidence, say "（当前评审证据不足，无法判断）".
                4. Use Chinese, professional and constructive tone.
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

    private String generateSectionReview(
            String templateContent,
            String paperTitle,
            ReviewSection section,
            List<String> priorReviewSummaries
    ) {
        String previousSummary = priorReviewSummaries.isEmpty()
                ? "无。"
                : truncate(String.join("\n", priorReviewSummaries), MAX_PRIOR_REVIEW_CHARS);
        String prompt = buildSectionReviewPrompt(templateContent, paperTitle, section, previousSummary);
        return requireContent(chatClient.prompt().user(prompt).call().content());
    }

    private String generateFinalReview(String templateContent, String paperTitle, String sectionReviews) {
        String prompt = """
                你是一名严谨的学术论文评审专家。请基于下面已经逐节完成的评审，给出总结性评审。

                ## 论文标题
                %s

                ## 评审模板
                %s

                ## 已完成的逐节评审
                %s

                ## 要求
                1. 只能总结、归纳已完成逐节评审中的证据和判断，不能新增论文中没有出现的事实。
                2. 明确列出主要优点、主要问题、修改建议和总体结论。
                3. 如果某个总体判断缺少足够证据，必须写“（当前文本无法判断）”。
                4. 使用中文，具体、克制、专业。
                """.formatted(
                paperTitle,
                templateContent,
                truncate(sectionReviews, MAX_FINAL_REVIEW_CONTEXT_CHARS)
        );
        return requireContent(chatClient.prompt().user(prompt).call().content());
    }

    private String buildSectionReviewPrompt(
            String templateContent,
            String paperTitle,
            ReviewSection section,
            String previousSummary
    ) {
        return """
                你是一名严谨的学术论文评审专家。请按评审模板只评审当前章节。

                ## 论文标题
                %s

                ## 当前章节
                %s

                ## 前面章节评审摘要
                %s

                ## 评审模板
                %s

                ## 当前章节原文
                %s

                ## 要求
                1. 结合“前面章节评审摘要”理解上下文，但本节的具体评价必须以“当前章节原文”为证据。
                2. 每个优点或问题都必须引用当前章节原文，格式为【原文：...】；引用要逐字摘取，不要改写。
                3. 如果当前章节没有足够信息判断某个模板要求，必须写“（当前章节文本无法判断）”。
                4. 不要捏造实验、数据、方法、结论或论文没有写出的内容。
                5. 输出包括：本节作用概述、具体优点、具体问题、修改建议、与前文评审的衔接判断。
                6. 使用中文，具体、克制、专业。
                """.formatted(
                paperTitle,
                section.title(),
                previousSummary,
                templateContent,
                truncate(section.content(), MAX_SECTION_TEXT_CHARS)
        );
    }

    private String rebuildFullTextFromChunks(List<PaperChunk> chunks) {
        StringBuilder fullText = new StringBuilder();
        for (PaperChunk chunk : chunks) {
            String content = chunk.getContent();
            if (content == null || content.isBlank()) {
                continue;
            }
            content = content.trim();
            if (fullText.isEmpty()) {
                fullText.append(content);
                continue;
            }

            int overlap = suffixPrefixOverlap(fullText, content);
            if (overlap > 0) {
                fullText.append(content.substring(overlap));
            } else {
                fullText.append("\n\n").append(content);
            }
        }
        return fullText.toString();
    }

    private int suffixPrefixOverlap(StringBuilder existing, String next) {
        int max = Math.min(existing.length(), next.length());
        for (int length = max; length >= 12; length--) {
            int start = existing.length() - length;
            if (startsWithRange(next, existing, start, length)) {
                return length;
            }
        }
        return 0;
    }

    private boolean startsWithRange(String text, StringBuilder source, int sourceStart, int length) {
        for (int i = 0; i < length; i++) {
            if (text.charAt(i) != source.charAt(sourceStart + i)) {
                return false;
            }
        }
        return true;
    }

    private List<ReviewSection> extractReviewSections(String fullText) {
        List<ReviewSection> sections = new ArrayList<>();
        String[] lines = fullText.replace("\r\n", "\n").replace('\r', '\n').split("\n");
        String currentTitle = null;
        StringBuilder currentContent = new StringBuilder();

        for (String rawLine : lines) {
            String heading = normalizeHeading(rawLine);
            if (isTerminalHeading(heading)) {
                break;
            }
            if (isSectionHeading(heading)) {
                addSectionIfPresent(sections, currentTitle == null ? "论文开头" : currentTitle, currentContent);
                currentTitle = displayTitle(heading);
                currentContent.setLength(0);
            } else {
                currentContent.append(rawLine).append('\n');
            }
        }
        addSectionIfPresent(sections, currentTitle == null ? "全文" : currentTitle, currentContent);

        if (sections.isEmpty()) {
            sections.add(new ReviewSection("全文", fullText));
        }
        return sections;
    }

    private void addSectionIfPresent(List<ReviewSection> sections, String title, StringBuilder content) {
        String text = content.toString().trim();
        if (!text.isBlank()) {
            sections.add(new ReviewSection(title, text));
        }
    }

    private boolean isSectionHeading(String candidate) {
        if (candidate.isBlank() || candidate.length() > 90) {
            return false;
        }
        String lower = candidate.toLowerCase(Locale.ROOT);
        if (lower.matches("^(abstract|introduction|related work|background|methodology|methods?|model|system model|experiments?|results?|discussion|conclusions?)\\b.*")) {
            return true;
        }
        if (candidate.matches("^(摘要|引言|绪论|相关工作|研究背景|问题定义|系统建模|系统模型|模型|方法|方法设计|算法|算法设计|理论分析|实验|实验结果|结果|讨论|结论)(\\s|：|:|$).*")) {
            return true;
        }
        if (candidate.matches("^第[一二三四五六七八九十百0-9]+[章节]\\s*\\S.{0,80}$")) {
            return true;
        }
        return candidate.matches("^\\d{1,2}(?:\\.\\d{1,2}){0,2}\\s+[^。；;.!?]{2,80}$");
    }

    private boolean isTerminalHeading(String candidate) {
        String lower = candidate.toLowerCase(Locale.ROOT);
        return lower.matches("^(references|bibliography|acknowledg(e)?ments?|appendix)\\b.*")
                || candidate.matches("^(参考文献|致谢|附录)(\\s|：|:|$).*");
    }

    private String normalizeHeading(String line) {
        return line == null ? "" : line.trim().replaceFirst("^#{1,6}\\s*", "");
    }

    private String displayTitle(String heading) {
        return heading
                .replaceFirst("^\\d{1,2}(?:\\.\\d{1,2}){0,2}\\s+", "")
                .replaceFirst("^第[一二三四五六七八九十百0-9]+[章节]\\s*", "")
                .trim();
    }

    private void appendSectionReview(StringBuilder resultBuffer, String title, String review) {
        resultBuffer.append("\n\n## ").append(title).append("\n")
                .append(review.trim()).append("\n");
    }

    private String summarizeForNextSection(String title, String review) {
        return "- " + title + "：" + truncate(review.replaceAll("\\s+", " "), 700);
    }

    private String requireContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalStateException("Model returned empty content");
        }
        return content;
    }

    private String modelErrorMessage(Throwable err) {
        String detail = err.getMessage();
        if (detail == null || detail.isBlank()) {
            detail = err.getClass().getSimpleName();
        }
        return "Review generation failed: " + detail;
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

    private record ReviewSection(String title, String content) {
    }
}