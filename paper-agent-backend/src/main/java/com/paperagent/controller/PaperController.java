package com.paperagent.controller;

import com.paperagent.dto.PaperListItem;
import com.paperagent.dto.PaperSummaryResponse;
import com.paperagent.dto.PaperUpdateRequest;
import com.paperagent.entity.Paper;
import com.paperagent.service.PaperService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/papers")
@RequiredArgsConstructor
public class PaperController {

    private final PaperService paperService;

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
