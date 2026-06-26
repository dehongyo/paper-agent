package com.paperagent.service;

import com.paperagent.dto.ChatHistoryMessage;
import com.paperagent.dto.EvidenceChunk;
import com.paperagent.dto.TraceableChatResponse;
import com.paperagent.dto.TraceableChatStreamEvent;
import com.paperagent.entity.Paper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ChatService {

    private final ChatClient chatClient;
    private final EmbeddingService embeddingService;
    private final EvidenceSearchService evidenceSearchService;
    private final PaperService paperService;

    public ChatService(
            ChatClient chatClient,
            EmbeddingService embeddingService,
            EvidenceSearchService evidenceSearchService,
            @Lazy PaperService paperService
    ) {
        this.chatClient = chatClient;
        this.embeddingService = embeddingService;
        this.evidenceSearchService = evidenceSearchService;
        this.paperService = paperService;
    }

    public String chat(String userMessage) {
        return chatClient.prompt()
                .user(userMessage)
                .call()
                .content();
    }

    public Flux<String> chatStream(String userMessage) {
        return chatStream(userMessage, List.of());
    }

    public Flux<String> chatStream(String userMessage, List<ChatHistoryMessage> history) {
        return chatClient.prompt()
                .user(buildConversationPrompt(userMessage, history))
                .stream()
                .content();
    }

    public Flux<String> chatWithPaperStream(Long paperId, String userMessage) {
        return chatWithPaperStream(paperId, userMessage, List.of());
    }

    public Flux<String> chatWithPaperStream(Long paperId, String userMessage, List<ChatHistoryMessage> history) {
        List<String> contextChunks = embeddingService.similaritySearch(paperId, userMessage, 5);
        String systemPrompt = buildRagSystemPrompt(contextChunks);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(buildConversationPrompt(userMessage, history))
                .stream()
                .content();
    }

    public TraceableChatResponse chatWithEvidence(String userMessage, Long paperId, String scope) {
        Long searchPaperId = "library".equals(scope) ? null : paperId;
        List<EvidenceChunk> evidence = evidenceSearchService.search(userMessage, searchPaperId, 6);
        if (evidence.isEmpty()) {
            return new TraceableChatResponse(
                    "本地文献库中没有足够信息回答这个问题。请上传更多相关论文，或换一个更具体的问题。",
                    evidence
            );
        }

        String prompt = buildTraceablePrompt(userMessage, evidence, null, List.of());
        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        return new TraceableChatResponse(answer, evidence);
    }

    public Flux<TraceableChatStreamEvent> chatWithEvidenceStream(String userMessage, Long paperId, String scope) {
        return chatWithEvidenceStream(userMessage, paperId, scope, List.of());
    }

    public Flux<TraceableChatStreamEvent> chatWithEvidenceStream(
            String userMessage,
            Long paperId,
            String scope,
            List<ChatHistoryMessage> history
    ) {
        boolean isLibraryScope = "library".equals(scope);
        Long searchPaperId = isLibraryScope ? null : paperId;
        List<EvidenceChunk> evidence = evidenceSearchService.search(userMessage, searchPaperId, 8);

        // Build library overview for library-scoped conversations
        String libraryOverview = isLibraryScope ? buildLibraryOverview() : null;

        // Even without matching evidence, answer library meta-questions using the overview
        if (evidence.isEmpty() && (libraryOverview == null || libraryOverview.isBlank())) {
            return Flux.just(
                    TraceableChatStreamEvent.answer("本地文献库中没有足够信息回答这个问题。请上传更多相关论文，或换一个更具体的问题。"),
                    TraceableChatStreamEvent.done()
            );
        }

        String prompt = buildTraceablePrompt(userMessage, evidence, libraryOverview, history);
        return chatClient.prompt()
                .user(prompt)
                .stream()
                .content()
                .map(TraceableChatStreamEvent::answer)
                .concatWithValues(
                        TraceableChatStreamEvent.evidence(evidence),
                        TraceableChatStreamEvent.done()
                );
    }

    /**
     * Build a comprehensive overview of the entire paper library.
     * This gives the AI full visibility into the library for meta-questions.
     */
    private String buildLibraryOverview() {
        List<Paper> allPapers = paperService.getAllPapers();
        if (allPapers.isEmpty()) return "";

        long readyCount = allPapers.stream().filter(p -> p.getStatus() == Paper.PaperStatus.READY).count();
        long processingCount = allPapers.stream().filter(p -> p.getStatus() != Paper.PaperStatus.READY && p.getStatus() != Paper.PaperStatus.ERROR).count();

        // Aggregate tags
        Map<String, Long> tagCounts = allPapers.stream()
                .flatMap(p -> PaperService.parseTags(p.getTags()).stream())
                .collect(Collectors.groupingBy(t -> t, LinkedHashMap::new, Collectors.counting()));

        StringBuilder sb = new StringBuilder();
        sb.append("=== 文献库概况 ===\n");
        sb.append("论文总数：").append(allPapers.size()).append(" 篇");
        sb.append("（就绪 ").append(readyCount).append(" 篇");
        if (processingCount > 0) sb.append("，处理中 ").append(processingCount).append(" 篇");
        sb.append("）\n\n");

        // Tag summary
        if (!tagCounts.isEmpty()) {
            sb.append("主题标签分布：\n");
            tagCounts.forEach((tag, count) ->
                sb.append("  - ").append(tag).append("（").append(count).append("篇）\n"));
            sb.append("\n");
        }

        // Individual paper summaries
        sb.append("文献列表：\n");
        for (Paper p : allPapers) {
            sb.append("  [ID:").append(p.getId()).append("] ");
            sb.append(p.getTitle());
            if (p.getAuthors() != null && !p.getAuthors().isBlank()) {
                sb.append(" | 作者：").append(p.getAuthors());
            }
            if (p.getTags() != null && !p.getTags().isBlank()) {
                sb.append(" | 标签：").append(p.getTags());
            }
            sb.append(" | 状态：").append(p.getStatus().name());
            if (p.getSummary() != null && !p.getSummary().isBlank()) {
                // Truncate summary to keep overview manageable
                String s = p.getSummary();
                sb.append(" | 摘要：").append(s.length() > 200 ? s.substring(0, 200) + "..." : s);
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    public String generateSummary(String fullText) {
        String truncated = fullText.length() > 8000
                ? fullText.substring(0, 8000) + "..."
                : fullText;

        String prompt = """
            你是一位专业的学术论文审阅人。请对以下论文内容进行结构化总结，用中文输出：
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

    private String buildRagSystemPrompt(List<String> contextChunks) {
        if (contextChunks.isEmpty()) {
            return "你是一个学术论文助手，帮助用户理解和分析论文内容。请基于你的知识回答问题。";
        }

        String context = String.join("\n\n---\n\n", contextChunks);
        return """
            你是一个学术论文助手。请仅基于以下论文片段回答用户问题。
            如果下面没有相关信息，请如实说明“论文中未提及该内容”，不要编造。

            论文片段：
            %s

            请用中文回答，保持学术、准确、简洁的风格。
            """.formatted(context);
    }

    private String buildTraceablePrompt(String userMessage, List<EvidenceChunk> evidence, String libraryOverview, List<ChatHistoryMessage> history) {
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

        boolean hasLibrary = libraryOverview != null && !libraryOverview.isBlank();
        boolean hasEvidence = !evidence.isEmpty();

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个严谨的学术论文助手，同时也是用户的文献库管家。\n\n");

        // Library overview section
        if (hasLibrary) {
            prompt.append("## 用户的文献库\n");
            prompt.append("以下是用户文献库的完整概况，包含所有论文的标题、作者、标签、状态和摘要。\n");
            prompt.append("当用户询问「文献库有多少篇论文」「有哪些主题」「关于XX的论文有哪些」等文献库管理类问题时，请基于此概况回答。\n");
            prompt.append(libraryOverview);
            prompt.append("\n\n");
        }

        // Evidence section
        if (hasEvidence) {
            prompt.append("## 语义搜索匹配到的内容片段\n");
            prompt.append("以下是基于用户问题匹配的相关论文片段。当用户询问论文具体内容、方法、结论等细节时，请基于这些片段回答，并使用 [1]、[2] 编号标注来源。\n");
            prompt.append(context);
            prompt.append("\n");
        }

        // Instructions
        prompt.append("## 指引\n");
        prompt.append("- 文献库概况类问题（总数、主题、分布）：使用「文献库」部分回答\n");
        if (hasEvidence) {
            prompt.append("- 论文内容类问题（方法、结论、细节）：使用「内容片段」部分回答，标注 [1][2] 来源\n");
        } else {
            prompt.append("- 当前没有匹配到相关论文片段，如果用户问的是内容细节类问题，请说明文献库中暂无相关内容\n");
        }
        prompt.append("- 不要编造论文、作者、实验数据或引用\n");
        prompt.append("- 回答使用中文，保持学术、准确、有帮助的风格\n\n");

        // History
        prompt.append("历史对话：\n");
        prompt.append(formatHistory(history));
        prompt.append("\n\n");

        // User question
        prompt.append("用户问题：\n");
        prompt.append(userMessage);

        return prompt.toString();
    }

    private String buildConversationPrompt(String userMessage, List<ChatHistoryMessage> history) {
        if (history == null || history.isEmpty()) return userMessage;
        return """
            请参考以下历史对话保持上下文连续，但优先回答用户当前问题。

            历史对话：
            %s

            当前问题：
            %s
            """.formatted(formatHistory(history), userMessage);
    }

    private String formatHistory(List<ChatHistoryMessage> history) {
        if (history == null || history.isEmpty()) return "无";
        return history.stream()
                .map(item -> "%s：%s".formatted(item.role(), item.content()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("无");
    }
}
