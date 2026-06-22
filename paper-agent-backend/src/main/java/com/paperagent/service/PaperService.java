package com.paperagent.service;

import com.paperagent.dto.PaperUpdateRequest;
import com.paperagent.entity.Paper;
import com.paperagent.entity.Paper.PaperStatus;
import com.paperagent.repository.PaperRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaperService {

    private final PaperRepository paperRepository;
    private final PdfParserService pdfParserService;
    private final EmbeddingService embeddingService;
    private final ChatService chatService;

    private static final String UPLOAD_DIR = "uploads/papers/";

    /**
     * 上传并处理论文：保存文件 → 解析文本 → 分块 → 向量化 → 生成摘要
     */
    @Transactional
    public Paper uploadAndProcess(MultipartFile file) throws IOException {
        // 1. 保存文件
        Path uploadPath = Paths.get(UPLOAD_DIR);
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
        paper.setStatus(PaperStatus.READY);
        paperRepository.save(paper);

        log.info("Paper {} processing complete", paperId);
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
     * 获取论文全文
     */
    public String getFullText(Long id) throws IOException {
        Paper paper = getPaper(id);
        return pdfParserService.extractFullText(paper.getFilePath());
    }
}
