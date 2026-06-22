package com.paperagent.service;

import com.paperagent.dto.EvidenceChunk;
import com.paperagent.dto.TraceableChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatClient chatClient;
    private final EmbeddingService embeddingService;
    private final EvidenceSearchService evidenceSearchService;

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
        List<String> contextChunks = embeddingService.similaritySearch(paperId, userMessage, 5);

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
     * 返回答案和结构化证据的 RAG 问答。
     */
    public TraceableChatResponse chatWithEvidence(String userMessage, Long paperId, String scope) {
        Long searchPaperId = "library".equals(scope) ? null : paperId;
        List<EvidenceChunk> evidence = evidenceSearchService.search(userMessage, searchPaperId, 6);
        if (evidence.isEmpty()) {
            return new TraceableChatResponse(
                    "本地文献库中没有足够信息回答这个问题。请上传更多相关论文，或换一个更具体的问题。",
                    evidence
            );
        }

        String prompt = buildTraceablePrompt(userMessage, evidence);
        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        return new TraceableChatResponse(answer, evidence);
    }

    /**
     * 生成论文摘要
     */
    public String generateSummary(String fullText) {
        // 取前 8000 字符
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

    private String buildTraceablePrompt(String userMessage, List<EvidenceChunk> evidence) {
        StringBuilder context = new StringBuilder();
        for (int i = 0; i < evidence.size(); i++) {
            EvidenceChunk chunk = evidence.get(i);
            context.append("[")
                    .append(i + 1)
                    .append("] ")
                    .append(chunk.paperTitle())
                    .append(" chunk#")
                    .append(chunk.chunkIndex())
                    .append("\n")
                    .append(chunk.content())
                    .append("\n\n");
        }

        return """
            你是一个严谨的学术论文助手。请只基于下面证据回答问题。
            如果证据不足，请明确说明证据不足，不要编造论文、作者、实验数据或引用。
            回答中使用 [1]、[2] 这样的编号标注来源。

            证据：
            %s

            用户问题：
            %s
            """.formatted(context, userMessage);
    }
}
