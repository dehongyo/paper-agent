package com.paperagent.service;

import com.paperagent.dto.PaperUpdateRequest;
import com.paperagent.entity.Paper;
import com.paperagent.entity.Paper.PaperStatus;
import com.paperagent.repository.PaperRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaperService {

    private final PaperRepository paperRepository;
    private final PdfParserService pdfParserService;
    private final EmbeddingService embeddingService;
    private final ChatService chatService;

    private final Path uploadBasePath;

    private static final int MIN_AUTO_TAGS = 3;
    private static final int MAX_AUTO_TAGS = 4;
    private static final List<String> ACADEMIC_PHRASES = List.of(
            "retrieval augmented generation",
            "graph neural networks",
            "large language models",
            "natural language processing",
            "machine learning",
            "deep learning",
            "reinforcement learning",
            "computer vision",
            "information retrieval",
            "knowledge graph",
            "question answering",
            "semantic search",
            "transformer",
            "attention mechanism",
            "citation analysis"
    );
    private static final Set<String> STOP_WORDS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "been", "being", "by", "can", "for", "from",
            "has", "have", "in", "into", "is", "it", "its", "may", "of", "on", "or", "our", "that",
            "the", "their", "this", "to", "using", "via", "was", "we", "were", "with", "within",
            "paper", "study", "studies", "method", "methods", "model", "models", "result", "results",
            "approach", "proposed", "based", "show", "shows", "used"
    );

    public PaperService(
            PaperRepository paperRepository,
            PdfParserService pdfParserService,
            EmbeddingService embeddingService,
            ChatService chatService
    ) {
        this.paperRepository = paperRepository;
        this.pdfParserService = pdfParserService;
        this.embeddingService = embeddingService;
        this.chatService = chatService;

        // Resolve upload directory to absolute path based on user.dir
        String userDir = System.getProperty("user.dir");
        Path resolved = Paths.get(userDir, "uploads", "papers").toAbsolutePath().normalize();
        // Also check if there's a paper-agent-backend subdirectory with uploads
        Path backendPath = Paths.get(userDir, "paper-agent-backend", "uploads", "papers").toAbsolutePath().normalize();
        if (Files.exists(backendPath)) {
            this.uploadBasePath = backendPath;
        } else if (Files.exists(resolved)) {
            this.uploadBasePath = resolved;
        } else {
            // Default: use the resolved path from user.dir; will be created on first upload
            this.uploadBasePath = resolved;
        }
        log.info("Upload base path resolved to: {}", this.uploadBasePath);
    }

    /**
     * 上传并处理论文：保存文件 → 解析文本 → 分块 → 向量化 → 生成摘要
     */
    @Transactional
    public Paper uploadAndProcess(MultipartFile file) throws IOException {
        // 1. 保存文件
        Path uploadPath = uploadBasePath;
        Files.createDirectories(uploadPath);
        String storedFilename = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(storedFilename);
        file.transferTo(filePath);

        // 2. 创建 Paper 记录
        Paper paper = Paper.builder()
                .title(pdfParserService.extractTitle(filePath.toString()))
                .filename(file.getOriginalFilename())
                .filePath(filePath.toString())
                .pageCount(pdfParserService.getPageCount(filePath.toString()))
                .status(PaperStatus.UPLOADED)
                .build();
        paper = paperRepository.save(paper);

        // 3. 处理
        try {
            processPaper(paper);
        } catch (Exception e) {
            log.error("Failed to process paper {}: {}", paper.getId(), e.getMessage(), e);
            paper.setStatus(PaperStatus.ERROR);
            paperRepository.save(paper);
        }

        return paper;
    }

    /**
     * 处理论文：解析 → 分块 → 向量化 → 摘要
     */
    private void processPaper(Paper paper) throws IOException {
        Long paperId = paper.getId();

        // Parse
        paper.setStatus(PaperStatus.PARSING);
        paperRepository.save(paper);

        String fullText = pdfParserService.extractFullText(paper.getFilePath());
        List<String> chunks = pdfParserService.chunkText(fullText);
        log.info("Paper {} parsed: {} chars, {} chunks", paperId, fullText.length(), chunks.size());

        // Embed
        paper.setStatus(PaperStatus.EMBEDDING);
        paperRepository.save(paper);

        embeddingService.embedAndStore(paperId, chunks);

        // Summarize
        String summary = chatService.generateSummary(fullText);
        paper.setSummary(summary);
        autoTag(paper, fullText);
        paper.setStatus(PaperStatus.READY);
        paperRepository.save(paper);

        log.info("Paper {} processing complete", paperId);
    }

    private void autoTag(Paper paper, String fullText) {
        if (paper.getTags() != null && !paper.getTags().isBlank()) return;
        List<String> tags = generateKeywordTags(paper.getTitle(), paper.getSummary(), fullText);
        if (!tags.isEmpty()) {
            paper.setTags(serializeTags(tags));
        }
    }

    static List<String> generateKeywordTags(String title, String summary, String fullText) {
        String weightedTitle = blankToNull(title) == null ? "" : (title + " " + title + " ");
        String weightedSummary = blankToNull(summary) == null ? "" : (summary + " ");
        String body = blankToNull(fullText) == null ? "" : fullText;
        String text = (weightedTitle + weightedSummary + body).toLowerCase(Locale.ROOT);

        Map<String, Integer> scores = new LinkedHashMap<>();
        for (String phrase : ACADEMIC_PHRASES) {
            int occurrences = countOccurrences(text, phrase);
            if (occurrences > 0) {
                scores.put(phrase, occurrences * 20 + (weightedTitle.toLowerCase(Locale.ROOT).contains(phrase) ? 25 : 0));
            }
        }

        String[] terms = text.split("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
        for (String term : terms) {
            if (!isUsefulKeyword(term)) {
                continue;
            }
            scores.merge(term, 1 + (weightedTitle.toLowerCase(Locale.ROOT).contains(term) ? 4 : 0), Integer::sum);
        }

        List<String> tags = new ArrayList<>();
        scores.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .filter(candidate -> tags.stream().noneMatch(existing -> overlaps(existing, candidate)))
                .limit(MAX_AUTO_TAGS)
                .forEach(tags::add);

        for (String fallback : List.of("research", "analysis", "literature")) {
            if (tags.size() >= MIN_AUTO_TAGS) {
                break;
            }
            if (!tags.contains(fallback)) {
                tags.add(fallback);
            }
        }

        return tags;
    }

    private static boolean isUsefulKeyword(String term) {
        return term.length() >= 3
                && !STOP_WORDS.contains(term)
                && !term.chars().allMatch(Character::isDigit);
    }

    private static int countOccurrences(String text, String phrase) {
        int count = 0;
        int index = text.indexOf(phrase);
        while (index >= 0) {
            count++;
            index = text.indexOf(phrase, index + phrase.length());
        }
        return count;
    }

    private static boolean overlaps(String existing, String candidate) {
        return existing.contains(candidate) || candidate.contains(existing);
    }

    /**
     * 获取所有论文（按上传时间倒序）
     */
    public List<Paper> getAllPapers() {
        return paperRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<Paper> searchPapers(String query, PaperStatus status, String tag) {
        String normalizedQuery = blankToNull(query);
        String normalizedTag = blankToNull(tag);
        if (normalizedQuery == null && status == null && normalizedTag == null) {
            return paperRepository.findAllByOrderByCreatedAtDesc();
        }
        return paperRepository.search(normalizedQuery, status, normalizedTag);
    }

    public List<String> getAllTags() {
        return paperRepository.findAll().stream()
                .flatMap(paper -> parseTags(paper.getTags()).stream())
                .distinct()
                .sorted()
                .toList();
    }

    public List<Paper> getReadyPapersForWriting(List<Long> paperIds) {
        List<Paper> papers = paperIds == null || paperIds.isEmpty()
                ? paperRepository.findByStatus(PaperStatus.READY)
                : paperRepository.findByIdIn(paperIds);
        return papers.stream()
                .filter(paper -> paper.getStatus() == PaperStatus.READY)
                .toList();
    }

    /**
     * 获取单篇论文
     */
    public Paper getPaper(Long id) {
        return paperRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Paper not found: " + id));
    }

    @Transactional
    public Paper updatePaper(Long id, PaperUpdateRequest request) {
        Paper paper = getPaper(id);
        paper.setTitle(request.title().trim());
        paper.setAuthors(blankToNull(request.authors()));
        paper.setDoi(blankToNull(request.doi()));
        paper.setSourceUrl(blankToNull(request.sourceUrl()));
        paper.setPublishedAt(request.publishedAt());
        paper.setSummary(blankToNull(request.summary()));
        paper.setNotes(blankToNull(request.notes()));
        paper.setTags(serializeTags(request.tags()));
        return paperRepository.save(paper);
    }

    @Transactional
    public void deletePaper(Long id) {
        Paper paper = getPaper(id);
        embeddingService.deleteByPaperId(id);
        paperRepository.delete(paper);
        try {
            Files.deleteIfExists(Paths.get(paper.getFilePath()));
        } catch (IOException e) {
            log.warn("Failed to delete file for paper {}: {}", id, e.getMessage());
        }
    }

    public static List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .toList();
    }

    public static String serializeTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String value = tags.stream()
                .map(String::trim)
                .filter(tag -> !tag.isEmpty())
                .distinct()
                .collect(Collectors.joining(","));
        return value.isBlank() ? null : value;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Resolve a URL or DOI to a direct PDF download URL
     */
    private String resolvePdfUrl(String urlOrDoi) {
        String trimmed = urlOrDoi.trim();

        // arXiv abstract URL → PDF URL
        if (trimmed.contains("arxiv.org/abs/")) {
            return trimmed.replace("arxiv.org/abs/", "arxiv.org/pdf/") + ".pdf";
        }

        // arXiv PDF URL — use as-is
        if (trimmed.contains("arxiv.org/pdf/")) {
            return trimmed;
        }

        // DOI → resolve via doi.org
        if (trimmed.startsWith("10.") || trimmed.startsWith("https://doi.org/")) {
            String doi = trimmed.startsWith("https://doi.org/")
                    ? trimmed.substring("https://doi.org/".length())
                    : trimmed;
            return "https://doi.org/" + doi;
        }

        // Assume it's a direct PDF URL
        return trimmed;
    }

    private String extractFilenameFromUrl(String url) {
        String name = url.substring(url.lastIndexOf('/') + 1);
        // Strip query params
        int queryIdx = name.indexOf('?');
        if (queryIdx >= 0) name = name.substring(0, queryIdx);
        // Ensure .pdf extension
        if (!name.toLowerCase().endsWith(".pdf")) {
            name = name + ".pdf";
        }
        return name;
    }

    /**
     * 从 URL 或 DOI 导入论文
     */
    @Transactional
    public Paper importFromUrl(String urlOrDoi) throws IOException {
        String resolvedUrl = resolvePdfUrl(urlOrDoi.trim());
        String filename = extractFilenameFromUrl(resolvedUrl);

        // Download PDF
        Path uploadPath = uploadBasePath;
        Files.createDirectories(uploadPath);
        String storedFilename = UUID.randomUUID() + "_" + filename;
        Path filePath = uploadPath.resolve(storedFilename);

        try (InputStream in = new URL(resolvedUrl).openStream()) {
            Files.copy(in, filePath);
        }

        // Create Paper record
        Paper paper = Paper.builder()
                .title(pdfParserService.extractTitle(filePath.toString()))
                .filename(filename)
                .filePath(filePath.toString())
                .pageCount(pdfParserService.getPageCount(filePath.toString()))
                .status(PaperStatus.UPLOADED)
                .build();
        paper = paperRepository.save(paper);

        // Process
        try {
            processPaper(paper);
        } catch (Exception e) {
            log.error("Failed to process imported paper {}: {}", paper.getId(), e.getMessage(), e);
            paper.setStatus(PaperStatus.ERROR);
            paperRepository.save(paper);
        }

        return paper;
    }

    /**
     * Get paper status (for polling)
     */
    public Paper getPaperStatus(Long id) {
        return getPaper(id);
    }

    /**
     * 获取论文全文
     */
    public String getFullText(Long id) throws IOException {
        Paper paper = getPaper(id);
        return pdfParserService.extractFullText(paper.getFilePath());
    }
}
