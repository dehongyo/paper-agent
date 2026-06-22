package com.paperagent.service;

import com.paperagent.dto.EvidenceChunk;
import com.paperagent.dto.ReferenceItem;
import com.paperagent.dto.WritingEvidence;
import com.paperagent.dto.WritingRequest;
import com.paperagent.dto.WritingResponse;
import com.paperagent.entity.Paper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WritingOrchestratorService {

    private static final int EVIDENCE_LIMIT = 8;
    private static final String INSUFFICIENT_EVIDENCE = "当前本地文献证据不足，无法生成可靠内容。请先上传或选择更多相关论文。";

    private final PaperService paperService;
    private final EvidenceSearchService evidenceSearchService;
    private final WritingService writingService;
    private final CitationService citationService;
    private final ExportService exportService;

    public WritingResponse generateOutline(WritingRequest request) {
        WritingContext context = prepareContext(request);
        String outline = context.evidence().isEmpty()
                ? INSUFFICIENT_EVIDENCE
                : writingService.generateOutline(
                        request.safeTopic(),
                        request.safeWritingType(),
                        request.safeLanguage(),
                        request.safeLength(),
                        context.evidence()
                );
        return buildResponse(request.safeTopic(), outline, "", context.references(), context.evidence());
    }

    public WritingResponse generateDraft(WritingRequest request) {
        WritingContext context = prepareContext(request);
        String outline = normalize(request.outline());
        if (outline == null && !context.evidence().isEmpty()) {
            outline = writingService.generateOutline(
                    request.safeTopic(),
                    request.safeWritingType(),
                    request.safeLanguage(),
                    request.safeLength(),
                    context.evidence()
            );
        }
        String draft = context.evidence().isEmpty()
                ? INSUFFICIENT_EVIDENCE
                : writingService.generateDraft(
                        request.safeTopic(),
                        outline,
                        request.safeWritingType(),
                        request.safeLanguage(),
                        request.safeLength(),
                        context.evidence()
                );
        return buildResponse(request.safeTopic(), outline, draft, context.references(), context.evidence());
    }

    public WritingResponse export(WritingRequest request) {
        String topic = request.safeTopic().isBlank() ? "Untitled" : request.safeTopic();
        List<Paper> papers = paperService.getReadyPapersForWriting(request.paperIds());
        List<ReferenceItem> references = citationService.formatReferences(papers, request.safeCitationStyle());
        String outline = normalize(request.outline());
        String draft = normalize(request.draft());
        if (draft == null) {
            throw new IllegalArgumentException("Draft cannot be empty when exporting.");
        }
        return buildResponse(topic, outline == null ? "" : outline, draft, references, List.of());
    }

    private WritingContext prepareContext(WritingRequest request) {
        String topic = request.safeTopic();
        if (topic.isBlank()) {
            throw new IllegalArgumentException("Topic cannot be empty.");
        }

        List<Paper> papers = paperService.getReadyPapersForWriting(request.paperIds());
        if (papers.isEmpty()) {
            throw new IllegalArgumentException("No ready local papers are available for writing.");
        }

        List<WritingEvidence> evidence = collectEvidence(topic, request.paperIds());
        List<Paper> referencedPapers = papersForReferences(papers, evidence);
        List<ReferenceItem> references = citationService.formatReferences(referencedPapers, request.safeCitationStyle());
        return new WritingContext(evidence, references);
    }

    private List<WritingEvidence> collectEvidence(String topic, List<Long> paperIds) {
        List<EvidenceChunk> chunks;
        if (paperIds == null || paperIds.isEmpty()) {
            chunks = evidenceSearchService.search(topic, null, EVIDENCE_LIMIT);
        } else {
            chunks = paperIds.stream()
                    .flatMap(paperId -> evidenceSearchService.search(topic, paperId, Math.max(3, EVIDENCE_LIMIT / paperIds.size())).stream())
                    .sorted(Comparator.comparing(EvidenceChunk::similarity).reversed())
                    .limit(EVIDENCE_LIMIT)
                    .toList();
        }

        Set<Long> seen = new HashSet<>();
        List<EvidenceChunk> distinct = chunks.stream()
                .filter(chunk -> seen.add(chunk.chunkId()))
                .limit(EVIDENCE_LIMIT)
                .toList();

        java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger(1);
        return distinct.stream()
                .map(chunk -> new WritingEvidence(
                        index.getAndIncrement(),
                        chunk.chunkId(),
                        chunk.paperId(),
                        chunk.paperTitle(),
                        chunk.chunkIndex(),
                        chunk.content(),
                        chunk.similarity()
                ))
                .toList();
    }

    private List<Paper> papersForReferences(List<Paper> papers, List<WritingEvidence> evidence) {
        if (evidence.isEmpty()) {
            return papers;
        }
        Map<Long, Paper> byId = papers.stream()
                .collect(Collectors.toMap(Paper::getId, Function.identity(), (left, right) -> left));
        return evidence.stream()
                .map(item -> byId.get(item.paperId()))
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
    }

    private WritingResponse buildResponse(
            String topic,
            String outline,
            String draft,
            List<ReferenceItem> references,
            List<WritingEvidence> evidence
    ) {
        return new WritingResponse(
                topic,
                outline == null ? "" : outline,
                draft == null ? "" : draft,
                references,
                evidence,
                exportService.toMarkdown(topic, outline, draft, references),
                exportService.toLatex(topic, outline, draft, references)
        );
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record WritingContext(List<WritingEvidence> evidence, List<ReferenceItem> references) {}
}
