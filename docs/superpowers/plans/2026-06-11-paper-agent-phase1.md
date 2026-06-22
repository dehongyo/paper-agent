# Paper Agent Phase 1 — 基础搭建 + 核心对话 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 跑通 Paper Agent 最小闭环 — 上传 PDF → 自动总结 → 基于论文内容的智能问答，同时搭建前端对话 UI 和文献库页面。

**Architecture:** 单体 Spring Boot 后端 + React SPA 前端。后端使用 Spring AI Alibaba 接入通义千问，pgvector 做向量存储实现 RAG，PDFBox 解析 PDF。前端使用 Vite + React 18 + shadcn/ui + TailwindCSS。前后端通过 SSE 实现流式对话。

**Tech Stack:** Java 17, Spring Boot 3.3, Spring AI 1.0, Spring AI Alibaba, PostgreSQL 16 + pgvector, Apache PDFBox, React 18, TypeScript 5, Vite 5, shadcn/ui, TailwindCSS 3, Zustand

**前置要求:** Java 17 JDK（当前环境为 Java 8，需升级），Node.js v24+（已满足），Docker Desktop（用于 PostgreSQL + pgvector）

---

## 文件结构总览

```
paperagent/
├── paper-agent-backend/                    # Spring Boot 后端
│   ├── pom.xml
│   ├── docker-compose.yml                  # PostgreSQL + pgvector
│   ├── src/main/java/com/paperagent/
│   │   ├── PaperAgentApplication.java
│   │   ├── config/
│   │   │   ├── AiConfig.java               # ChatClient + ChatModel
│   │   │   ├── VectorStoreConfig.java      # PgVectorStore
│   │   │   └── WebConfig.java              # CORS
│   │   ├── controller/
│   │   │   ├── ChatController.java         # SSE 流式对话 API
│   │   │   └── PaperController.java        # 论文上传/列表/问答 API
│   │   ├── service/
│   │   │   ├── ChatService.java            # 对话编排（含 RAG）
│   │   │   ├── PdfParserService.java       # PDF 文本提取
│   │   │   ├── EmbeddingService.java       # 向量化 + 存储
│   │   │   └── PaperService.java           # 论文管理编排
│   │   ├── entity/
│   │   │   ├── Paper.java                  # 论文实体
│   │   │   └── PaperChunk.java             # 文档块实体
│   │   ├── repository/
│   │   │   ├── PaperRepository.java
│   │   │   └── PaperChunkRepository.java
│   │   └── dto/
│   │       ├── ChatRequest.java
│   │       ├── ChatResponse.java
│   │       ├── PaperSummaryResponse.java
│   │       └── PaperListItem.java
│   └── src/main/resources/
│       ├── application.yml
│       └── application-dev.yml
│
└── paper-agent-frontend/                   # React 前端
    ├── package.json
    ├── vite.config.ts
    ├── tsconfig.json
    ├── tailwind.config.js
    ├── postcss.config.js
    ├── index.html
    ├── components.json                     # shadcn/ui 配置
    └── src/
        ├── main.tsx
        ├── App.tsx
        ├── index.css                       # Tailwind 入口
        ├── types/
        │   └── index.ts                    # 共享类型定义
        ├── api/
        │   └── client.ts                   # API 请求封装
        ├── store/
        │   └── chatStore.ts                # Zustand 聊天状态
        ├── components/
        │   ├── layout/
        │   │   ├── Sidebar.tsx
        │   │   └── Layout.tsx
        │   ├── chat/
        │   │   ├── ChatWindow.tsx           # 聊天主窗口
        │   │   ├── ChatMessage.tsx          # 单条消息
        │   │   └── ChatInput.tsx            # 输入框
        │   └── library/
        │       ├── LibraryPage.tsx          # 文献库页面
        │       ├── PaperCard.tsx            # 论文卡片
        │       └── PaperUpload.tsx          # 上传组件
        └── pages/
            ├── ChatPage.tsx
            └── LibraryPage.tsx
```

---

## 后端任务

### Task 1: 后端项目脚手架 + Docker Compose

**Files:**

- Create: `paper-agent-backend/pom.xml`
- Create: `paper-agent-backend/docker-compose.yml`
- Create: `paper-agent-backend/src/main/java/com/paperagent/PaperAgentApplication.java`
- Create: `paper-agent-backend/src/main/resources/application.yml`
- Create: `paper-agent-backend/src/main/resources/application-dev.yml`

- [ ] **Step 1: 创建 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.paperagent</groupId>
    <artifactId>paper-agent-backend</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <name>Paper Agent Backend</name>
    <description>自动科研智能体后端服务</description>

    <properties>
        <java.version>17</java.version>
        <spring-ai.version>1.0.0-M4</spring-ai.version>
        <spring-ai-alibaba.version>1.0.0-M4</spring-ai-alibaba.version>
    </properties>

    <dependencies>
        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Spring AI Alibaba (通义千问) -->
        <dependency>
            <groupId>com.alibaba.cloud.ai</groupId>
            <artifactId>spring-ai-alibaba-starter</artifactId>
            <version>${spring-ai-alibaba.version}</version>
        </dependency>

        <!-- Spring AI PgVector -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-pgvector-store</artifactId>
            <version>${spring-ai.version}</version>
        </dependency>

        <!-- PostgreSQL -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- PDF 解析 -->
        <dependency>
            <groupId>org.apache.pdfbox</groupId>
            <artifactId>pdfbox</artifactId>
            <version>3.0.3</version>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <repositories>
        <repository>
            <id>spring-milestones</id>
            <name>Spring Milestones</name>
            <url>https://repo.spring.io/milestone</url>
        </repository>
    </repositories>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 创建 docker-compose.yml**

```yaml
version: '3.8'
services:
  postgres:
    image: pgvector/pgvector:pg16
    container_name: paper-agent-db
    environment:
      POSTGRES_DB: paperagent
      POSTGRES_USER: paperagent
      POSTGRES_PASSWORD: paperagent123
    ports:
      - "5432:5432"
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./src/main/resources/schema.sql:/docker-entrypoint-initdb.d/01-schema.sql
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U paperagent"]
      interval: 5s
      timeout: 5s
      retries: 5

volumes:
  pgdata:
```

- [ ] **Step 3: 创建 Spring Boot 启动类**

```java
package com.paperagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class PaperAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaperAgentApplication.class, args);
    }
}
```

- [ ] **Step 4: 创建 application.yml**

```yaml
server:
  port: 8080

spring:
  application:
    name: paper-agent
  profiles:
    active: dev

  # 文件上传限制
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB

  # JPA
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

- [ ] **Step 5: 创建 application-dev.yml**

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/paperagent
    username: paperagent
    password: paperagent123
    driver-class-name: org.postgresql.Driver

  # Spring AI Alibaba (通义千问)
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:your-api-key-here}
      chat:
        options:
          model: qwen-plus
          temperature: 0.7
      embedding:
        options:
          model: text-embedding-v4

  # pgvector
  ai:
    vectorstore:
      pgvector:
        index-type: HNSW
        distance-type: COSINE_DISTANCE
        dimensions: 1024

logging:
  level:
    com.paperagent: DEBUG
    org.springframework.ai: DEBUG
```

- [ ] **Step 6: 创建 schema.sql**

```sql
-- 启用 pgvector 扩展
CREATE EXTENSION IF NOT EXISTS vector;

-- 论文表
CREATE TABLE IF NOT EXISTS papers (
    id          BIGSERIAL PRIMARY KEY,
    title       VARCHAR(500) NOT NULL,
    authors     VARCHAR(1000),
    filename    VARCHAR(255) NOT NULL,
    file_path   VARCHAR(500) NOT NULL,
    page_count  INTEGER,
    summary     TEXT,
    status      VARCHAR(20) NOT NULL DEFAULT 'UPLOADED',
    created_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 文档块表（含向量）
CREATE TABLE IF NOT EXISTS paper_chunks (
    id          BIGSERIAL PRIMARY KEY,
    paper_id    BIGINT NOT NULL REFERENCES papers(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL,
    content     TEXT NOT NULL,
    embedding   vector(1024),
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 向量索引
CREATE INDEX IF NOT EXISTS idx_paper_chunks_embedding
    ON paper_chunks USING hnsw (embedding vector_cosine_ops);
```

- [ ] **Step 7: 编译验证**

```bash
cd paper-agent-backend && mvn compile
```

预期: BUILD SUCCESS

- [ ] **Step 8: 启动 Docker 并验证数据库**

```bash
cd paper-agent-backend && docker compose up -d
docker ps | grep paper-agent-db
```

预期: 容器 running，端口 5432 可访问

---

### Task 2: JPA 实体 + Repository

**Files:**

- Create: `paper-agent-backend/src/main/java/com/paperagent/entity/Paper.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/entity/PaperChunk.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/repository/PaperRepository.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/repository/PaperChunkRepository.java`

- [ ] **Step 1: 创建 Paper 实体**

```java
package com.paperagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "papers")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Paper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 1000)
    private String authors;

    @Column(nullable = false)
    private String filename;

    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PaperStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum PaperStatus {
        UPLOADED, PARSING, PARSED, EMBEDDING, READY, ERROR
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = PaperStatus.UPLOADED;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: 创建 PaperChunk 实体**

```java
package com.paperagent.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "paper_chunks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaperChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "paper_id", nullable = false)
    private Long paperId;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: 创建 PaperRepository**

```java
package com.paperagent.repository;

import com.paperagent.entity.Paper;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PaperRepository extends JpaRepository<Paper, Long> {

    List<Paper> findAllByOrderByCreatedAtDesc();

    List<Paper> findByStatus(Paper.PaperStatus status);
}
```

- [ ] **Step 4: 创建 PaperChunkRepository**

```java
package com.paperagent.repository;

import com.paperagent.entity.PaperChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PaperChunkRepository extends JpaRepository<PaperChunk, Long> {

    List<PaperChunk> findByPaperIdOrderByChunkIndex(Long paperId);

    void deleteByPaperId(Long paperId);

    /**
     * 向量相似度搜索（使用 pgvector 的余弦距离）
     * Spring AI PgVectorStore 会处理这部分，此方法作为 fallback
     */
    @Query(value = """
        SELECT pc.*, 1 - (pc.embedding <=> CAST(:queryEmbedding AS vector)) AS similarity
        FROM paper_chunks pc
        WHERE pc.paper_id = :paperId
        ORDER BY pc.embedding <=> CAST(:queryEmbedding AS vector)
        LIMIT :limit
        """, nativeQuery = true)
    List<PaperChunk> findSimilarChunks(
        @Param("paperId") Long paperId,
        @Param("queryEmbedding") String queryEmbedding,
        @Param("limit") int limit
    );
}
```

- [ ] **Step 5: 编译验证**

```bash
cd paper-agent-backend && mvn compile
```

预期: BUILD SUCCESS

---

### Task 3: PDF 解析服务

**Files:**

- Create: `paper-agent-backend/src/main/java/com/paperagent/service/PdfParserService.java`

- [ ] **Step 1: 创建 PdfParserService**

```java
package com.paperagent.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class PdfParserService {

    private static final int CHUNK_SIZE = 1000;      // 每块字符数
    private static final int CHUNK_OVERLAP = 200;     // 块间重叠字符数

    /**
     * 从 PDF 文件提取全文
     */
    public String extractFullText(String filePath) throws IOException {
        File file = new File(filePath);
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setAddMoreFormatting(true);
            return stripper.getText(document);
        }
    }

    /**
     * 获取 PDF 页数
     */
    public int getPageCount(String filePath) throws IOException {
        File file = new File(filePath);
        try (PDDocument document = Loader.loadPDF(file)) {
            return document.getNumberOfPages();
        }
    }

    /**
     * 将文本按固定大小分块，块间有重叠
     */
    public List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            start += (CHUNK_SIZE - CHUNK_OVERLAP);
        }

        log.debug("Split text of {} chars into {} chunks", text.length(), chunks.size());
        return chunks;
    }

    /**
     * 提取 PDF 元信息作为 fallback title
     */
    public String extractTitle(String filePath) throws IOException {
        File file = new File(filePath);
        try (PDDocument document = Loader.loadPDF(file)) {
            String title = document.getDocumentInformation().getTitle();
            if (title != null && !title.isBlank()) {
                return title;
            }
            // Fallback: 用文件名
            return file.getName().replace(".pdf", "").replace("_", " ");
        }
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
cd paper-agent-backend && mvn compile
```

预期: BUILD SUCCESS

---

### Task 4: Spring AI 配置 + 对话服务

**Files:**

- Create: `paper-agent-backend/src/main/java/com/paperagent/config/AiConfig.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/config/VectorStoreConfig.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/config/WebConfig.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/service/ChatService.java`

- [ ] **Step 1: 创建 AiConfig**

```java
package com.paperagent.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    /**
     * ChatClient — Spring AI 1.0 的流式对话入口
     * ChatModel 由 spring-ai-alibaba-starter 自动配置（qwen-plus）
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
```

- [ ] **Step 2: 创建 VectorStoreConfig**

```java
package com.paperagent.config;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration
public class VectorStoreConfig {

    /**
     * PgVectorStore — 基于 PostgreSQL + pgvector 的向量存储
     * EmbeddingModel 由 spring-ai-alibaba-starter 自动配置（text-embedding-v4）
     */
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1024)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .tableName("paper_chunks")
                .build();
    }
}
```

- [ ] **Step 3: 创建 WebConfig（CORS）**

```java
package com.paperagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:5173")
                        .allowedMethods("GET", "POST", "PUT", "DELETE")
                        .allowedHeaders("*")
                        .allowCredentials(true);
            }
        };
    }
}
```

- [ ] **Step 4: 创建 ChatService（含 RAG 检索）**

```java
package com.paperagent.service;

import com.paperagent.entity.PaperChunk;
import com.paperagent.repository.PaperChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final PaperChunkRepository chunkRepository;

    /**
     * 普通对话（不带论文上下文）
     */
    public String chat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }

    /**
     * 基于指定论文的 RAG 对话（流式 SSE）
     */
    public Flux<String> chatWithPaperStream(Long paperId, String userMessage) {
        // 1. 从向量库检索相关 chunks
        List<String> contextChunks = retrieveRelevantChunks(paperId, userMessage, 5);

        // 2. 构建增强 prompt
        String systemPrompt = buildRagSystemPrompt(contextChunks);

        // 3. 流式调用 LLM
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .stream()
                .content();
    }

    /**
     * 生成论文摘要
     */
    public String generateSummary(Long paperId, String fullText) {
        // 取前 8000 字符作为摘要输入（避免超出 token 限制）
        String truncated = fullText.length() > 8000
                ? fullText.substring(0, 8000) + "..."
                : fullText;

        String prompt = """
            你是一位专业的学术论文审稿人。请对以下论文内容进行结构化总结，用中文输出：

            【研究背景】
            【研究方法】
            【主要发现】
            【结论与贡献】

            论文内容：
            %s
            """.formatted(truncated);

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    /**
     * 向量检索：从论文 chunks 中找最相关的片段
     */
    private List<String> retrieveRelevantChunks(Long paperId, String query, int topK) {
        try {
            // 使用 Spring AI VectorStore 做语义搜索
            // 通过 metadata 过滤只查指定论文的 chunks
            var results = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(query)
                            .topK(topK)
                            .filterExpression("paper_id == " + paperId)
                            .build()
            );

            return results.stream()
                    .map(doc -> doc.getText())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Vector search failed, falling back to all chunks: {}", e.getMessage());
            // Fallback: 返回该论文的所有 chunks（取前 5 个）
            return chunkRepository.findByPaperIdOrderByChunkIndex(paperId)
                    .stream()
                    .limit(topK)
                    .map(PaperChunk::getContent)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 构建 RAG system prompt
     */
    private String buildRagSystemPrompt(List<String> contextChunks) {
        if (contextChunks.isEmpty()) {
            return "你是一个学术论文助手，帮助用户理解和分析论文内容。请基于你的知识回答问题。";
        }

        String context = String.join("\n\n---\n\n", contextChunks);
        return """
            你是一个学术论文助手。请仅基于以下论文片段回答用户问题。
            如果下面没有相关信息，请如实说明"论文中未提及该内容"，不要编造。

            论文片段：
            %s

            请用中文回答，保持学术、准确、简洁的风格。
            """.formatted(context);
    }
}
```

- [ ] **Step 5: 编译验证**

```bash
cd paper-agent-backend && mvn compile
```

预期: BUILD SUCCESS

---

### Task 5: 向量化服务 + 论文管理服务

**Files:**

- Create: `paper-agent-backend/src/main/java/com/paperagent/service/EmbeddingService.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/service/PaperService.java`

- [ ] **Step 1: 创建 EmbeddingService**

```java
package com.paperagent.service;

import com.paperagent.entity.PaperChunk;
import com.paperagent.repository.PaperChunkRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmbeddingService {

    private final VectorStore vectorStore;
    private final PaperChunkRepository chunkRepository;

    /**
     * 将论文 chunks 向量化并存入 pgvector
     * 分两步：先存 chunk 元数据到 paper_chunks，再通过 VectorStore 写入向量
     */
    @Transactional
    public void embedAndStore(Long paperId, List<String> chunks) {
        if (chunks.isEmpty()) {
            log.warn("No chunks to embed for paper {}", paperId);
            return;
        }

        // 1. 保存所有 chunk 的文本到 paper_chunks
        for (int i = 0; i < chunks.size(); i++) {
            PaperChunk chunk = PaperChunk.builder()
                    .paperId(paperId)
                    .chunkIndex(i)
                    .content(chunks.get(i))
                    .build();
            chunkRepository.save(chunk);
        }

        // 2. 通过 VectorStore 批量写入向量
        // Spring AI 的 VectorStore.add() 会自动调用 EmbeddingModel 生成向量
        List<Document> documents = chunks.stream()
                .map(text -> new Document(text, Map.of("paper_id", paperId)))
                .toList();

        try {
            vectorStore.add(documents);
            log.info("Successfully embedded {} chunks for paper {}", chunks.size(), paperId);
        } catch (Exception e) {
            log.error("Failed to embed chunks for paper {}: {}", paperId, e.getMessage());
            // chunks 文本已保存，向量可稍后重试
        }
    }

    /**
     * 删除论文的所有向量数据
     */
    @Transactional
    public void deleteByPaperId(Long paperId) {
        chunkRepository.deleteByPaperId(paperId);
        // PgVectorStore 的 delete 通过 metadata filter
        vectorStore.delete("paper_id == " + paperId);
    }
}
```

- [ ] **Step 2: 创建 PaperService（编排上传→解析→向量化→摘要）**

```java
package com.paperagent.service;

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

        // 3. 异步处理（简化版：同步执行）
        try {
            processPaper(paper);
        } catch (Exception e) {
            log.error("Failed to process paper {}: {}", paper.getId(), e.getMessage());
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

        // Step 1: 解析
        paper.setStatus(PaperStatus.PARSING);
        paperRepository.save(paper);

        String fullText = pdfParserService.extractFullText(paper.getFilePath());
        List<String> chunks = pdfParserService.chunkText(fullText);
        log.info("Paper {} parsed: {} chars, {} chunks", paperId, fullText.length(), chunks.size());

        // Step 2: 向量化
        paper.setStatus(PaperStatus.EMBEDDING);
        paperRepository.save(paper);

        embeddingService.embedAndStore(paperId, chunks);

        // Step 3: 生成摘要
        String summary = chatService.generateSummary(paperId, fullText);
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

    /**
     * 获取单篇论文
     */
    public Paper getPaper(Long id) {
        return paperRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Paper not found: " + id));
    }

    /**
     * 获取论文全文（用于前端阅读）
     */
    public String getFullText(Long id) throws IOException {
        Paper paper = getPaper(id);
        return pdfParserService.extractFullText(paper.getFilePath());
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
cd paper-agent-backend && mvn compile
```

预期: BUILD SUCCESS

---

### Task 6: DTO + Controller 层

**Files:**

- Create: `paper-agent-backend/src/main/java/com/paperagent/dto/ChatRequest.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/dto/PaperSummaryResponse.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/dto/PaperListItem.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/controller/ChatController.java`
- Create: `paper-agent-backend/src/main/java/com/paperagent/controller/PaperController.java`

- [ ] **Step 1: 创建 ChatRequest DTO**

```java
package com.paperagent.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatRequest(
        @NotBlank(message = "消息不能为空")
        String message,

        Long paperId     // 可选：null 表示普通对话，非 null 表示基于论文的 RAG 对话
) {}
```

- [ ] **Step 2: 创建 PaperSummaryResponse DTO**

```java
package com.paperagent.dto;

import java.time.LocalDateTime;

public record PaperSummaryResponse(
        Long id,
        String title,
        String authors,
        String filename,
        Integer pageCount,
        String summary,
        String status,
        LocalDateTime createdAt
) {}
```

- [ ] **Step 3: 创建 PaperListItem DTO**

```java
package com.paperagent.dto;

import com.paperagent.entity.Paper;

import java.time.LocalDateTime;

public record PaperListItem(
        Long id,
        String title,
        String filename,
        String status,
        LocalDateTime createdAt
) {
    public static PaperListItem from(Paper paper) {
        return new PaperListItem(
                paper.getId(),
                paper.getTitle(),
                paper.getFilename(),
                paper.getStatus().name(),
                paper.getCreatedAt()
        );
    }
}
```

- [ ] **Step 4: 创建 ChatController（SSE 流式）**

```java
package com.paperagent.controller;

import com.paperagent.dto.ChatRequest;
import com.paperagent.service.ChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * 流式对话（SSE）
     * POST /api/chat/stream
     *
     * Content-Type: text/event-stream
     * 前端通过 EventSource 或 fetch + ReadableStream 消费
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> chatStream(@Valid @RequestBody ChatRequest request) {
        log.info("Chat request: paperId={}, message={}", request.paperId(), request.message());

        if (request.paperId() != null) {
            // 基于论文的 RAG 对话
            return chatService.chatWithPaperStream(request.paperId(), request.message());
        } else {
            // 普通对话
            return Flux.just(chatService.chat(request.message()));
        }
    }

    /**
     * 非流式对话（简单场景）
     * POST /api/chat
     */
    @PostMapping
    public String chat(@Valid @RequestBody ChatRequest request) {
        log.info("Non-stream chat: paperId={}, message={}", request.paperId(), request.message());

        if (request.paperId() != null) {
            // 收集流式结果
            return chatService.chatWithPaperStream(request.paperId(), request.message())
                    .collectList()
                    .map(list -> String.join("", list))
                    .block();
        }

        return chatService.chat(request.message());
    }
}
```

- [ ] **Step 5: 创建 PaperController**

```java
package com.paperagent.controller;

import com.paperagent.dto.PaperListItem;
import com.paperagent.dto.PaperSummaryResponse;
import com.paperagent.entity.Paper;
import com.paperagent.service.PaperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        if (!"application/pdf".equals(file.getContentType())) {
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
    public List<PaperListItem> list() {
        return paperService.getAllPapers().stream()
                .map(PaperListItem::from)
                .toList();
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
                paper.getCreatedAt()
        );
    }
}
```

- [ ] **Step 6: 编译验证**

```bash
cd paper-agent-backend && mvn compile
```

预期: BUILD SUCCESS

---

### Task 7: 后端集成测试（启动 Spring Boot + 测试 API）

**Files:**

- Create: `paper-agent-backend/src/test/java/com/paperagent/PaperAgentApplicationTests.java`

- [ ] **Step 1: 确保 Docker PostgreSQL 运行**

```bash
cd paper-agent-backend && docker compose up -d
docker ps | grep paper-agent-db
```

- [ ] **Step 2: 启动 Spring Boot 应用**

```bash
cd paper-agent-backend && mvn spring-boot:run
```

预期: 应用在 8080 端口启动，日志显示 DashScope API 连接成功

- [ ] **Step 3: 测试健康检查**

```bash
curl http://localhost:8080/actuator/health 2>/dev/null || echo "Actuator not configured, test with:"
curl http://localhost:8080/api/papers
```

预期: 返回空数组 `[]`

- [ ] **Step 4: 测试普通对话 API**

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "你好，请简单介绍一下你自己"}'
```

预期: 返回 AI 回复文本

- [ ] **Step 5: 测试 PDF 上传（准备一个测试 PDF）**

```bash
# 先用一个简单的 PDF 测试（任意 PDF 文件）
curl -X POST http://localhost:8080/api/papers/upload \
  -F "file=@/path/to/test.pdf"
```

预期: 返回 Paper JSON，含 id、title、summary 等字段，status 为 "READY"

- [ ] **Step 6: 验证论文列表和基于论文的对话**

```bash
# 获取论文列表
curl http://localhost:8080/api/papers

# 基于论文对话（用刚才返回的 paper id）
curl -X POST http://localhost:8080/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"message": "这篇论文的创新点是什么？", "paperId": 1}'
```

预期: SSE 流式返回 AI 基于论文内容的回答

---

## 前端任务

### Task 8: React 前端脚手架

**Files:**

- Create: `paper-agent-frontend/` (通过 Vite 模板创建)

- [ ] **Step 1: 用 Vite 创建 React + TypeScript 项目**

```bash
cd c:/paperagent
npm create vite@latest paper-agent-frontend -- --template react-ts
```

- [ ] **Step 2: 安装核心依赖**

```bash
cd paper-agent-frontend
npm install
npm install tailwindcss @tailwindcss/vite
npm install zustand
npm install lucide-react
```

- [ ] **Step 3: 安装 shadcn/ui**

```bash
# 安装 shadcn/ui CLI
npx shadcn@latest init -d
```

选择: TypeScript, React 19 style (new-york), TailwindCSS 4, CSS variables for colors: yes

- [ ] **Step 4: 添加 shadcn/ui 组件**

```bash
npx shadcn@latest add button
npx shadcn@latest add input
npx shadcn@latest add textarea
npx shadcn@latest add card
npx shadcn@latest add scroll-area
npx shadcn@latest add dialog
npx shadcn@latest add separator
npx shadcn@latest add badge
npx shadcn@latest add avatar
npx shadcn@latest add sonner   # toast 通知
npx shadcn@latest add tooltip
npx shadcn@latest add dropdown-menu
```

- [ ] **Step 5: 验证前端启动**

```bash
npm run dev
```

预期: Vite 在 5173 端口启动，浏览器显示默认 Vite + React 页面

---

### Task 9: 前端类型定义 + API Client

**Files:**

- Create: `paper-agent-frontend/src/types/index.ts`
- Create: `paper-agent-frontend/src/api/client.ts`

- [ ] **Step 1: 创建类型定义**

```typescript
// src/types/index.ts

/** 论文列表项 */
export interface PaperListItem {
  id: number;
  title: string;
  filename: string;
  status: string;
  createdAt: string;
}

/** 论文详情（含摘要） */
export interface PaperSummary {
  id: number;
  title: string;
  authors: string | null;
  filename: string;
  pageCount: number | null;
  summary: string | null;
  status: string;
  createdAt: string;
}

/** 聊天请求 */
export interface ChatRequest {
  message: string;
  paperId?: number | null;
}

/** 聊天消息 */
export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: number;
  isStreaming?: boolean;
}
```

- [ ] **Step 2: 创建 API Client**

```typescript
// src/api/client.ts
import type { PaperListItem, PaperSummary, ChatRequest } from '../types';

const BASE_URL = 'http://localhost:8080/api';

/** 通用 fetch 封装 */
async function request<T>(
  path: string,
  options?: RequestInit
): Promise<T> {
  const res = await fetch(`${BASE_URL}${path}`, {
    headers: { 'Content-Type': 'application/json' },
    ...options,
  });

  if (!res.ok) {
    throw new Error(`API Error: ${res.status} ${res.statusText}`);
  }

  return res.json();
}

/** 获取论文列表 */
export async function getPapers(): Promise<PaperListItem[]> {
  return request<PaperListItem[]>('/papers');
}

/** 获取论文详情 */
export async function getPaper(id: number): Promise<PaperSummary> {
  return request<PaperSummary>(`/papers/${id}`);
}

/** 获取论文全文 */
export async function getPaperFullText(id: number): Promise<string> {
  return request<string>(`/papers/${id}/fulltext`);
}

/** 上传 PDF */
export async function uploadPaper(file: File): Promise<PaperSummary> {
  const formData = new FormData();
  formData.append('file', file);

  const res = await fetch(`${BASE_URL}/papers/upload`, {
    method: 'POST',
    body: formData,
  });

  if (!res.ok) {
    throw new Error(`Upload failed: ${res.status}`);
  }

  return res.json();
}

/** 流式对话（SSE） */
export function streamChat(
  request: ChatRequest,
  onChunk: (text: string) => void,
  onDone: () => void,
  onError: (err: Error) => void
): AbortController {
  const controller = new AbortController();

  fetch(`${BASE_URL}/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(request),
    signal: controller.signal,
  })
    .then(async (res) => {
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const reader = res.body?.getReader();
      if (!reader) throw new Error('No response body');

      const decoder = new TextDecoder();
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        const text = decoder.decode(value, { stream: true });
        onChunk(text);
      }
      onDone();
    })
    .catch((err) => {
      if (err.name !== 'AbortError') {
        onError(err);
      }
    });

  return controller;
}

/** 非流式对话 */
export async function sendChat(req: ChatRequest): Promise<string> {
  return request<string>('/chat', {
    method: 'POST',
    body: JSON.stringify(req),
  });
}
```

---

### Task 10: Zustand 状态管理 + 聊天组件

**Files:**

- Create: `paper-agent-frontend/src/store/chatStore.ts`
- Create: `paper-agent-frontend/src/components/chat/ChatMessage.tsx`
- Create: `paper-agent-frontend/src/components/chat/ChatInput.tsx`
- Create: `paper-agent-frontend/src/components/chat/ChatWindow.tsx`

- [ ] **Step 1: 创建聊天状态管理**

```typescript
// src/store/chatStore.ts
import { create } from 'zustand';
import type { ChatMessage } from '../types';
import { streamChat, sendChat } from '../api/client';

interface ChatState {
  messages: ChatMessage[];
  isLoading: boolean;
  selectedPaperId: number | null;

  addMessage: (msg: ChatMessage) => void;
  updateLastMessage: (content: string) => void;
  setLoading: (loading: boolean) => void;
  setSelectedPaper: (paperId: number | null) => void;
  clearMessages: () => void;

  /** 发送消息并接收流式回复 */
  sendMessage: (content: string) => Promise<void>;
}

let messageCounter = 0;
function genId(): string {
  return `msg_${Date.now()}_${++messageCounter}`;
}

export const useChatStore = create<ChatState>((set, get) => ({
  messages: [],
  isLoading: false,
  selectedPaperId: null,

  addMessage: (msg) =>
    set((s) => ({ messages: [...s.messages, msg] })),

  updateLastMessage: (content) =>
    set((s) => {
      const msgs = [...s.messages];
      const last = msgs[msgs.length - 1];
      if (last && last.role === 'assistant') {
        msgs[msgs.length - 1] = { ...last, content: last.content + content };
      }
      return { messages: msgs };
    }),

  setLoading: (loading) => set({ isLoading: loading }),

  setSelectedPaper: (paperId) =>
    set({ selectedPaperId: paperId }),

  clearMessages: () => set({ messages: [] }),

  sendMessage: async (content: string) => {
    const { selectedPaperId, addMessage, updateLastMessage, setLoading } = get();

    // 添加用户消息
    const userMsg: ChatMessage = {
      id: genId(),
      role: 'user',
      content,
      timestamp: Date.now(),
    };
    addMessage(userMsg);

    // 添加空的 assistant 消息占位
    const assistantMsg: ChatMessage = {
      id: genId(),
      role: 'assistant',
      content: '',
      timestamp: Date.now(),
      isStreaming: true,
    };
    addMessage(assistantMsg);
    setLoading(true);

    const req = { message: content, paperId: selectedPaperId };

    streamChat(
      req,
      (chunk) => updateLastMessage(chunk),
      () => {
        setLoading(false);
        // 移除 isStreaming 标记
        set((s) => {
          const msgs = [...s.messages];
          const last = msgs[msgs.length - 1];
          if (last) msgs[msgs.length - 1] = { ...last, isStreaming: false };
          return { messages: msgs };
        });
      },
      (err) => {
        console.error('Chat error:', err);
        setLoading(false);
        updateLastMessage(`\n\n> _请求失败: ${err.message}_`);
      }
    );
  },
}));
```

- [ ] **Step 2: 创建 ChatMessage 组件**

```tsx
// src/components/chat/ChatMessage.tsx
import { cn } from '@/lib/utils';
import type { ChatMessage as ChatMessageType } from '@/types';
import { Bot, User } from 'lucide-react';
import ReactMarkdown from 'react-markdown';

interface Props {
  message: ChatMessageType;
}

export function ChatMessage({ message }: Props) {
  const isUser = message.role === 'user';

  return (
    <div className={cn('flex gap-3 py-4', isUser ? 'flex-row-reverse' : '')}>
      {/* Avatar */}
      <div
        className={cn(
          'flex h-8 w-8 shrink-0 items-center justify-center rounded-full',
          isUser ? 'bg-primary text-primary-foreground' : 'bg-muted'
        )}
      >
        {isUser ? <User size={16} /> : <Bot size={16} />}
      </div>

      {/* Content */}
      <div
        className={cn(
          'max-w-[80%] rounded-lg px-4 py-2 text-sm leading-relaxed',
          isUser
            ? 'bg-primary text-primary-foreground'
            : 'bg-muted text-foreground'
        )}
      >
        {message.content ? (
          <ReactMarkdown>{message.content}</ReactMarkdown>
        ) : message.isStreaming ? (
          <span className="inline-block animate-pulse">▋</span>
        ) : (
          <span className="text-muted-foreground italic">...</span>
        )}
      </div>
    </div>
  );
}
```

- [ ] **Step 3: 创建 ChatInput 组件**

```tsx
// src/components/chat/ChatInput.tsx
import { useState, useRef, useEffect, KeyboardEvent } from 'react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Send, Loader2 } from 'lucide-react';

interface Props {
  onSend: (message: string) => void;
  isLoading: boolean;
}

export function ChatInput({ onSend, isLoading }: Props) {
  const [input, setInput] = useState('');
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  // 自动调整高度
  useEffect(() => {
    const el = textareaRef.current;
    if (el) {
      el.style.height = 'auto';
      el.style.height = Math.min(el.scrollHeight, 200) + 'px';
    }
  }, [input]);

  const handleSend = () => {
    const trimmed = input.trim();
    if (!trimmed || isLoading) return;
    onSend(trimmed);
    setInput('');
  };

  const handleKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <div className="flex items-end gap-2 border-t bg-background p-4">
      <Textarea
        ref={textareaRef}
        value={input}
        onChange={(e) => setInput(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder={
          isLoading ? 'AI 正在回复...' : '输入消息，按 Enter 发送（Shift+Enter 换行）'
        }
        disabled={isLoading}
        rows={1}
        className="min-h-[40px] resize-none"
      />
      <Button
        onClick={handleSend}
        disabled={!input.trim() || isLoading}
        size="icon"
      >
        {isLoading ? (
          <Loader2 size={18} className="animate-spin" />
        ) : (
          <Send size={18} />
        )}
      </Button>
    </div>
  );
}
```

- [ ] **Step 4: 创建 ChatWindow 组件**

```tsx
// src/components/chat/ChatWindow.tsx
import { useEffect, useRef } from 'react';
import { ScrollArea } from '@/components/ui/scroll-area';
import { ChatMessage } from './ChatMessage';
import { ChatInput } from './ChatInput';
import { useChatStore } from '@/store/chatStore';

export function ChatWindow() {
  const { messages, isLoading, sendMessage, selectedPaperId, setSelectedPaper } =
    useChatStore();
  const bottomRef = useRef<HTMLDivElement>(null);

  // 自动滚动到底部
  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  return (
    <div className="flex h-full flex-col">
      {/* 论文上下文提示 */}
      {selectedPaperId && (
        <div className="flex items-center gap-2 border-b bg-accent/10 px-4 py-2 text-sm text-muted-foreground">
          <span>📄 正在与论文 #{selectedPaperId} 对话</span>
          <button
            onClick={() => setSelectedPaper(null)}
            className="ml-auto text-xs underline hover:text-foreground"
          >
            取消
          </button>
        </div>
      )}

      {/* 消息列表 */}
      <ScrollArea className="flex-1 px-4">
        {messages.length === 0 ? (
          <div className="flex h-full items-center justify-center text-muted-foreground">
            <div className="text-center">
              <h2 className="mb-2 text-lg font-semibold">Paper Agent</h2>
              <p className="text-sm">上传 PDF 论文，我来帮你阅读和分析。</p>
              <p className="text-sm">开始提问吧 ✨</p>
            </div>
          </div>
        ) : (
          messages.map((msg) => <ChatMessage key={msg.id} message={msg} />)
        )}
        <div ref={bottomRef} />
      </ScrollArea>

      {/* 输入框 */}
      <ChatInput onSend={sendMessage} isLoading={isLoading} />
    </div>
  );
}
```

---

### Task 11: 文献库页面组件

**Files:**

- Create: `paper-agent-frontend/src/components/library/PaperCard.tsx`
- Create: `paper-agent-frontend/src/components/library/PaperUpload.tsx`
- Create: `paper-agent-frontend/src/components/library/LibraryPage.tsx`

- [ ] **Step 1: 创建 PaperCard 组件**

```tsx
// src/components/library/PaperCard.tsx
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import type { PaperListItem } from '@/types';
import { FileText, MessageCircle } from 'lucide-react';

interface Props {
  paper: PaperListItem;
  onChat: (paperId: number) => void;
}

const statusMap: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  UPLOADED:  { label: '已上传', variant: 'secondary' },
  PARSING:   { label: '解析中', variant: 'outline' },
  PARSED:    { label: '已解析', variant: 'outline' },
  EMBEDDING: { label: '向量化中', variant: 'outline' },
  READY:     { label: '就绪', variant: 'default' },
  ERROR:     { label: '错误', variant: 'destructive' },
};

export function PaperCard({ paper, onChat }: Props) {
  const status = statusMap[paper.status] ?? { label: paper.status, variant: 'outline' as const };

  return (
    <Card className="transition-shadow hover:shadow-md">
      <CardHeader className="pb-2">
        <div className="flex items-start justify-between gap-2">
          <CardTitle className="line-clamp-2 text-base">
            <FileText size={16} className="mr-2 inline text-muted-foreground" />
            {paper.title}
          </CardTitle>
          <Badge variant={status.variant} className="shrink-0">
            {status.label}
          </Badge>
        </div>
      </CardHeader>
      <CardContent>
        <p className="mb-3 text-xs text-muted-foreground">
          {paper.filename} · {new Date(paper.createdAt).toLocaleDateString('zh-CN')}
        </p>
        <Button
          variant="outline"
          size="sm"
          className="w-full"
          disabled={paper.status !== 'READY'}
          onClick={() => onChat(paper.id)}
        >
          <MessageCircle size={14} className="mr-2" />
          开始对话
        </Button>
      </CardContent>
    </Card>
  );
}
```

- [ ] **Step 2: 创建 PaperUpload 组件**

```tsx
// src/components/library/PaperUpload.tsx
import { useState, useRef } from 'react';
import { Button } from '@/components/ui/button';
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { Upload, Loader2, FileText } from 'lucide-react';
import { uploadPaper } from '@/api/client';
import { toast } from 'sonner';
import type { PaperSummary } from '@/types';

interface Props {
  onUploaded: (paper: PaperSummary) => void;
}

export function PaperUpload({ onUploaded }: Props) {
  const [open, setOpen] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  const handleFileSelect = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file && file.type === 'application/pdf') {
      setSelectedFile(file);
    } else if (file) {
      toast.error('仅支持 PDF 文件');
    }
  };

  const handleUpload = async () => {
    if (!selectedFile) return;
    setIsUploading(true);
    try {
      const result = await uploadPaper(selectedFile);
      toast.success(`上传成功！${result.summary ? ' 摘要已生成。' : ''}`);
      onUploaded(result);
      setOpen(false);
      setSelectedFile(null);
    } catch (err) {
      toast.error(`上传失败: ${err instanceof Error ? err.message : '未知错误'}`);
    } finally {
      setIsUploading(false);
    }
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    const file = e.dataTransfer.files[0];
    if (file?.type === 'application/pdf') {
      setSelectedFile(file);
    }
  };

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button>
          <Upload size={16} className="mr-2" />
          上传论文
        </Button>
      </DialogTrigger>

      <DialogContent>
        <DialogHeader>
          <DialogTitle>上传 PDF 论文</DialogTitle>
        </DialogHeader>

        <div
          className="flex flex-col items-center gap-4 rounded-lg border-2 border-dashed p-8"
          onDrop={handleDrop}
          onDragOver={(e) => e.preventDefault()}
        >
          {selectedFile ? (
            <div className="flex items-center gap-2 text-sm">
              <FileText size={20} className="text-primary" />
              <span className="font-medium">{selectedFile.name}</span>
              <span className="text-muted-foreground">
                ({(selectedFile.size / 1024 / 1024).toFixed(1)} MB)
              </span>
            </div>
          ) : (
            <>
              <Upload size={40} className="text-muted-foreground" />
              <p className="text-sm text-muted-foreground">
                拖拽 PDF 文件到此处，或
              </p>
            </>
          )}

          <Button
            variant="outline"
            onClick={() => inputRef.current?.click()}
          >
            选择文件
          </Button>
          <input
            ref={inputRef}
            type="file"
            accept="application/pdf"
            className="hidden"
            onChange={handleFileSelect}
          />

          <Button
            onClick={handleUpload}
            disabled={!selectedFile || isUploading}
            className="w-full"
          >
            {isUploading ? (
              <>
                <Loader2 size={16} className="mr-2 animate-spin" />
                上传中...
              </>
            ) : (
              '开始上传'
            )}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
}
```

- [ ] **Step 3: 创建 LibraryPage 组件**

```tsx
// src/components/library/LibraryPage.tsx
import { useEffect, useState } from 'react';
import { PaperCard } from './PaperCard';
import { PaperUpload } from './PaperUpload';
import { getPapers } from '@/api/client';
import { useChatStore } from '@/store/chatStore';
import type { PaperListItem, PaperSummary } from '@/types';
import { Loader2, Library } from 'lucide-react';

export function LibraryPage() {
  const [papers, setPapers] = useState<PaperListItem[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const setSelectedPaper = useChatStore((s) => s.setSelectedPaper);

  const loadPapers = async () => {
    try {
      setIsLoading(true);
      const data = await getPapers();
      setPapers(data);
      setError(null);
    } catch (err) {
      setError(err instanceof Error ? err.message : '加载失败');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    loadPapers();
  }, []);

  const handleChat = (paperId: number) => {
    setSelectedPaper(paperId);
  };

  const handleUploaded = (paper: PaperSummary) => {
    // 将新上传的论文加入列表
    const item: PaperListItem = {
      id: paper.id,
      title: paper.title,
      filename: paper.filename,
      status: paper.status,
      createdAt: paper.createdAt,
    };
    setPapers((prev) => [item, ...prev]);
  };

  return (
    <div className="space-y-4 p-6">
      {/* 头部 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">文献库</h1>
          <p className="text-sm text-muted-foreground">
            管理已上传的论文，上传后自动生成摘要并支持智能问答
          </p>
        </div>
        <PaperUpload onUploaded={handleUploaded} />
      </div>

      {/* 加载状态 */}
      {isLoading && (
        <div className="flex items-center gap-2 py-12 text-center text-muted-foreground">
          <Loader2 size={20} className="mx-auto animate-spin" />
        </div>
      )}

      {/* 错误状态 */}
      {error && (
        <div className="py-12 text-center">
          <p className="text-destructive">{error}</p>
          <button
            onClick={loadPapers}
            className="mt-2 text-sm underline"
          >
            重试
          </button>
        </div>
      )}

      {/* 空状态 */}
      {!isLoading && !error && papers.length === 0 && (
        <div className="flex flex-col items-center gap-3 py-16 text-muted-foreground">
          <Library size={48} strokeWidth={1} />
          <p>还没有上传论文</p>
          <p className="text-sm">点击"上传论文"开始使用</p>
        </div>
      )}

      {/* 论文列表 */}
      {!isLoading && papers.length > 0 && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {papers.map((paper) => (
            <PaperCard key={paper.id} paper={paper} onChat={handleChat} />
          ))}
        </div>
      )}
    </div>
  );
}
```

---

### Task 12: 布局 + 路由 + App 组装

**Files:**

- Create: `paper-agent-frontend/src/components/layout/Sidebar.tsx`
- Create: `paper-agent-frontend/src/components/layout/Layout.tsx`
- Modify: `paper-agent-frontend/src/App.tsx`
- Modify: `paper-agent-frontend/src/main.tsx`

- [ ] **Step 1: 创建 Sidebar 导航**

```tsx
// src/components/layout/Sidebar.tsx
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { Separator } from '@/components/ui/separator';
import { MessageCircle, Library, Github } from 'lucide-react';

interface Props {
  currentView: 'chat' | 'library';
  onNavigate: (view: 'chat' | 'library') => void;
}

export function Sidebar({ currentView, onNavigate }: Props) {
  return (
    <aside className="flex h-full w-56 flex-col border-r bg-card">
      {/* Logo */}
      <div className="p-4">
        <h1 className="text-lg font-bold tracking-tight">
          Paper <span className="text-primary">Agent</span>
        </h1>
        <p className="text-xs text-muted-foreground">自动科研智能体</p>
      </div>

      <Separator />

      {/* 导航 */}
      <nav className="flex-1 space-y-1 p-3">
        <Button
          variant={currentView === 'chat' ? 'secondary' : 'ghost'}
          className="w-full justify-start"
          onClick={() => onNavigate('chat')}
        >
          <MessageCircle size={16} className="mr-2" />
          对话
        </Button>

        <Button
          variant={currentView === 'library' ? 'secondary' : 'ghost'}
          className="w-full justify-start"
          onClick={() => onNavigate('library')}
        >
          <Library size={16} className="mr-2" />
          文献库
        </Button>
      </nav>

      {/* 底部 */}
      <div className="p-3">
        <Separator className="mb-3" />
        <a
          href="https://github.com"
          target="_blank"
          rel="noopener noreferrer"
          className="flex items-center gap-2 text-xs text-muted-foreground hover:text-foreground"
        >
          <Github size={14} />
          Paper Agent v0.1
        </a>
      </div>
    </aside>
  );
}
```

- [ ] **Step 2: 创建 Layout 组件**

```tsx
// src/components/layout/Layout.tsx
import { useState } from 'react';
import { Sidebar } from './Sidebar';
import { ChatWindow } from '@/components/chat/ChatWindow';
import { LibraryPage } from '@/components/library/LibraryPage';
import { Toaster } from '@/components/ui/sonner';

type View = 'chat' | 'library';

export function Layout() {
  const [currentView, setCurrentView] = useState<View>('chat');

  return (
    <div className="flex h-screen">
      <Sidebar currentView={currentView} onNavigate={setCurrentView} />

      <main className="flex-1 overflow-hidden">
        {currentView === 'chat' && <ChatWindow />}
        {currentView === 'library' && <LibraryPage />}
      </main>

      <Toaster />
    </div>
  );
}
```

- [ ] **Step 3: 更新 App.tsx**

```tsx
// src/App.tsx
import { Layout } from './components/layout/Layout';

function App() {
  return <Layout />;
}

export default App;
```

- [ ] **Step 4: 确保 main.tsx 正确配置**

```tsx
// src/main.tsx
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import './index.css';
import App from './App';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>
);
```

- [ ] **Step 5: 验证前端完整运行**

```bash
cd paper-agent-frontend && npm run dev
```

预期: 浏览器打开 localhost:5173，显示侧边栏 + 对话窗口，可切换至文献库页面

---

## 端到端验证清单

全部完成后，执行以下验证：

- [ ] 后端 Docker PostgreSQL 正常运行
- [ ] 后端 Spring Boot 正常启动（8080 端口）
- [ ] 前端 Vite 正常启动（5173 端口）
- [ ] 上传 PDF 文件成功，返回论文摘要
- [ ] 在对话页面输入问题，获得流式 AI 回复
- [ ] 文献库页面显示已上传论文
- [ ] 从文献库点击"开始对话"，切换到对话页并带上论文上下文
- [ ] 基于论文的 RAG 问答能返回论文相关的内容
