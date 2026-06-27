package com.paperagent.service;

import com.paperagent.dto.TemplateResponse;
import com.paperagent.entity.ReviewTemplate;
import com.paperagent.repository.ReviewTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TemplateService {

    private final ReviewTemplateRepository templateRepository;

    private static final String BUILTIN_THESIS = """
            1. 论文结构与格式
               - 标题、摘要、关键词是否完整规范
               - 章节结构是否合理，标题层级是否清晰
               - 图表、公式编号和引用是否规范
               - 参考文献格式是否一致

            2. 文献综述
               - 是否覆盖了关键相关文献
               - 文献的时效性和权威性
               - 对现有工作的总结和不足分析是否到位

            3. 研究方法
               - 研究方法选择是否合理
               - 方法描述是否清晰可复现
               - 实验/数据设计是否严谨

            4. 论证逻辑
               - 研究问题和结论的呼应程度
               - 论证链条是否完整无跳跃
               - 数据分析是否支撑结论

            5. 创新性与贡献
               - 创新点是否明确
               - 与现有工作的对比分析
               - 研究贡献是否清晰陈述

            6. 写作质量
               - 学术用语是否准确
               - 段落衔接是否流畅
               - 是否存在歧义或模糊表达
            """;

    private static final String BUILTIN_IEEE = """
            1. 创新性与贡献
               - 研究问题的原创性和价值
               - 与 state-of-the-art 的对比和差异
               - 对领域的潜在影响

            2. 技术正确性
               - 理论推导是否正确
               - 算法设计是否合理
               - 假设条件是否明确

            3. 实验设计
               - 数据集选择是否合理
               - 基线对比是否充分
               - 消融实验是否完整
               - 评估指标是否合适

            4. 可复现性
               - 实验设置描述是否详细
               - 代码/数据是否可用
               - 参数设置是否公开

            5. 文献综述
               - 相关工作覆盖是否全面
               - 对前沿工作的把握
               - 对比分析是否客观

            6. 表述质量
               - 论文结构是否符合会议/期刊惯例
               - 图表是否清晰有效
               - 英文表达是否流畅（如为英文论文）
               - 术语使用是否一致
            """;

    @Transactional
    public void seedBuiltinTemplates() {
        if (templateRepository.countByType("builtin") == 0) {
            templateRepository.save(ReviewTemplate.builder()
                    .name("学位论文评审")
                    .type("builtin")
                    .contentText(BUILTIN_THESIS)
                    .build());
            templateRepository.save(ReviewTemplate.builder()
                    .name("IEEE / 期刊论文评审")
                    .type("builtin")
                    .contentText(BUILTIN_IEEE)
                    .build());
            log.info("Seeded 2 built-in review templates");
        }
    }

    public List<TemplateResponse> listTemplates() {
        return templateRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public TemplateResponse getTemplate(Long id) {
        ReviewTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        return toResponse(t);
    }

    public String getTemplateContent(Long id) {
        return templateRepository.findById(id)
                .map(ReviewTemplate::getContentText)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
    }

    @Transactional
    public TemplateResponse uploadTemplate(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String content = extractText(file, filename);
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Failed to extract text from uploaded file");
        }

        String name = filename != null && filename.contains(".")
                ? filename.substring(0, filename.lastIndexOf('.'))
                : (filename != null ? filename : "custom-template");

        ReviewTemplate template = templateRepository.save(ReviewTemplate.builder()
                .name(name)
                .type("custom")
                .sourceFilename(filename)
                .contentText(content)
                .build());

        log.info("Uploaded review template: {} ({} chars)", name, content.length());
        return toResponse(template);
    }

    @Transactional
    public void deleteTemplate(Long id) {
        ReviewTemplate t = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Template not found: " + id));
        if ("builtin".equals(t.getType())) {
            throw new IllegalArgumentException("Cannot delete built-in templates");
        }
        templateRepository.deleteById(id);
        log.info("Deleted review template: {}", t.getName());
    }

    private String extractText(MultipartFile file, String filename) {
        try {
            String lower = filename != null ? filename.toLowerCase() : "";
            if (lower.endsWith(".pdf")) {
                try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
                    return new PDFTextStripper().getText(doc);
                }
            } else if (lower.endsWith(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
                    return new XWPFWordExtractor(doc).getText();
                }
            } else if (lower.endsWith(".doc")) {
                // Old .doc format — POI HWPF
                try (var fis = file.getInputStream()) {
                    org.apache.poi.hwpf.HWPFDocument doc = new org.apache.poi.hwpf.HWPFDocument(fis);
                    return doc.getText().toString();
                }
            } else {
                throw new IllegalArgumentException(
                        "Unsupported file type: " + filename + ". Only PDF, DOC, and DOCX are accepted.");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse file: " + filename, e);
        }
    }

    private TemplateResponse toResponse(ReviewTemplate t) {
        return new TemplateResponse(
                t.getId(),
                t.getName(),
                t.getType(),
                t.getSourceFilename(),
                t.getContentText() != null ? t.getContentText().length() : 0,
                t.getCreatedAt()
        );
    }
}
