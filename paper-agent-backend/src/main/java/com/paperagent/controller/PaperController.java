package com.paperagent.controller;

import com.paperagent.dto.BatchImportRequest;
import com.paperagent.dto.PaperImportRequest;
import com.paperagent.dto.PaperListItem;
import com.paperagent.dto.PaperSummaryResponse;
import com.paperagent.dto.PaperUpdateRequest;
import com.paperagent.entity.Paper;
import com.paperagent.repository.PaperRepository;
import com.paperagent.service.ExportService;
import com.paperagent.service.PaperService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/papers")
@RequiredArgsConstructor
public class PaperController {

    private final PaperService paperService;
    private final PaperRepository paperRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ExportService exportService;

    /**
     * 上传 PDF
     * POST /api/papers/upload
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PaperSummaryResponse> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty() || !"application/pdf".equals(file.getContentType())) {
            return ResponseEntity.badRequest().build();
        }

        try {
            Paper paper = paperService.uploadAndProcess(file);
            return ResponseEntity.ok(toSummaryResponse(paper));
        } catch (IOException e) {
            log.error("Upload failed", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取论文列表
     * GET /api/papers
     */
    @GetMapping
    public List<PaperListItem> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Paper.PaperStatus status,
            @RequestParam(required = false) String tag
    ) {
        return paperService.searchPapers(query, status, tag).stream()
                .map(PaperListItem::from)
                .toList();
    }

    @GetMapping("/tags")
    public List<String> tags() {
        return paperService.getAllTags();
    }

    /**
     * 获取论文详情（含摘要）
     * GET /api/papers/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaperSummaryResponse> get(@PathVariable Long id) {
        try {
            Paper paper = paperService.getPaper(id);
            return ResponseEntity.ok(toSummaryResponse(paper));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}")
    public ResponseEntity<PaperSummaryResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PaperUpdateRequest request
    ) {
        try {
            Paper paper = paperService.updatePaper(id, request);
            return ResponseEntity.ok(toSummaryResponse(paper));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        paperService.deletePaper(id);
    }

    /**
     * 获取论文状态（轻量轮询）
     * GET /api/papers/{id}/status
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<java.util.Map<String, String>> getStatus(@PathVariable Long id) {
        try {
            Paper paper = paperService.getPaperStatus(id);
            return ResponseEntity.ok(java.util.Map.of("status", paper.getStatus().name()));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * 从 URL 或 DOI 导入论文
     * POST /api/papers/import
     */
    @PostMapping("/import")
    public ResponseEntity<PaperSummaryResponse> importFromUrl(@Valid @RequestBody PaperImportRequest request) {
        try {
            Paper paper = paperService.importFromUrl(request.safeUrlOrDoi());
            return ResponseEntity.ok(toSummaryResponse(paper));
        } catch (IOException e) {
            log.error("Import from URL failed", e);
            return ResponseEntity.internalServerError().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 批量从 URL 或 DOI 导入论文
     * POST /api/papers/import/batch
     */
    @PostMapping("/import/batch")
    public ResponseEntity<List<PaperSummaryResponse>> batchImport(@Valid @RequestBody BatchImportRequest request) {
        List<PaperSummaryResponse> results = new ArrayList<>();
        for (String url : request.urlsOrDois()) {
            try {
                Paper paper = paperService.importFromUrl(url.trim());
                results.add(toSummaryResponse(paper));
            } catch (Exception e) {
                log.warn("Batch import failed for URL {}: {}", url, e.getMessage());
            }
        }
        return ResponseEntity.ok(results);
    }

    /**
     * 获取论文原始 PDF 文件
     * GET /api/papers/{id}/file
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> getPdfFile(@PathVariable Long id) {
        try {
            Paper paper = paperService.getPaper(id);
            String storedPath = paper.getFilePath();
            java.nio.file.Path resolved = resolveFilePath(storedPath);

            if (resolved == null || !Files.exists(resolved)) {
                log.warn("PDF file not found for paper {}: stored={}", id, storedPath);
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + paper.getFilename() + "\"")
                    .body(new FileSystemResource(resolved));
        } catch (RuntimeException e) {
            log.warn("Failed to serve PDF for paper {}: {}", id, e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Resolve a stored file path to an absolute path, trying multiple base directories.
     * Handles both absolute paths and relative paths from different working directories.
     */
    private java.nio.file.Path resolveFilePath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) return null;

        java.nio.file.Path path = Paths.get(storedPath);

        // If already absolute and exists, use it directly
        if (path.isAbsolute() && Files.exists(path)) return path;

        // Candidate base directories to try (in order)
        String userDir = System.getProperty("user.dir");
        java.nio.file.Path[] bases = {
            Paths.get(userDir),
            Paths.get(userDir, "paper-agent-backend"),
            Paths.get(userDir, "..", "paper-agent-backend").normalize(),
        };

        for (java.nio.file.Path base : bases) {
            java.nio.file.Path candidate = path.isAbsolute() ? path : base.resolve(storedPath).normalize();
            if (Files.exists(candidate)) return candidate;
        }

        // Last resort: try the stored path as-is (maybe it's a valid relative path from cwd)
        if (Files.exists(path)) return path.toAbsolutePath().normalize();

        return null;
    }

    /**
     * 获取论文全文
     * GET /api/papers/{id}/fulltext
     */
    @GetMapping("/{id}/fulltext")
    public ResponseEntity<String> getFullText(@PathVariable Long id) {
        try {
            String text = paperService.getFullText(id);
            return ResponseEntity.ok(text);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 获取论文引用格式
     * GET /api/papers/{id}/cite
     */
    @GetMapping("/{id}/cite")
    public ResponseEntity<java.util.Map<String, String>> getCitation(
            @PathVariable Long id,
            @RequestParam(defaultValue = "bibtex") String format) {
        try {
            Paper paper = paperService.getPaper(id);
            String citation = switch (format.toLowerCase()) {
                case "bibtex" -> exportService.toBibtex(paper);
                case "endnote" -> exportService.toEndnote(paper);
                case "markdown" -> exportService.toMarkdown(paper.getTitle(), "", "", List.of());
                default -> exportService.toBibtex(paper);
            };
            return ResponseEntity.ok(java.util.Map.of("citation", citation));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalPapers = paperRepository.count();
        long readyPapers = paperRepository.findByStatus(Paper.PaperStatus.READY).size();
        long processingPapers = totalPapers - readyPapers - paperRepository.findByStatus(Paper.PaperStatus.ERROR).size();
        long errorPapers = paperRepository.findByStatus(Paper.PaperStatus.ERROR).size();
        long totalChunks = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM paper_chunks", Long.class);
        long totalChatSessions = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_sessions", Long.class);
        long totalChatMessages = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chat_messages", Long.class);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalPapers", totalPapers);
        stats.put("readyPapers", readyPapers);
        stats.put("processingPapers", processingPapers);
        stats.put("errorPapers", errorPapers);
        stats.put("totalTags", paperRepository.findAll().stream()
                .flatMap(p -> PaperService.parseTags(p.getTags()).stream())
                .distinct()
                .count());
        stats.put("uniqueTags", paperService.getAllTags().size());
        stats.put("totalChunks", totalChunks);
        stats.put("totalChatSessions", totalChatSessions);
        stats.put("totalChatMessages", totalChatMessages);
        return ResponseEntity.ok(stats);
    }

    private PaperSummaryResponse toSummaryResponse(Paper paper) {
        return new PaperSummaryResponse(
                paper.getId(),
                paper.getTitle(),
                paper.getAuthors(),
                paper.getFilename(),
                paper.getPageCount(),
                paper.getSummary(),
                paper.getStatus().name(),
                paper.getDoi(),
                paper.getSourceUrl(),
                paper.getPublishedAt(),
                paper.getNotes(),
                PaperService.parseTags(paper.getTags()),
                paper.getCreatedAt()
        );
    }
}
