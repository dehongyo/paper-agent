package com.paperagent.service;

import com.paperagent.dto.ChatHistoryMessage;
import com.paperagent.dto.ConversationContext;
import com.paperagent.dto.EvidenceChunk;
import com.paperagent.dto.SearchFilters;
import com.paperagent.dto.TraceableChatResponse;
import com.paperagent.dto.TraceableChatStreamEvent;
import com.paperagent.entity.Paper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
        return chatStream(userMessage, new ConversationContext(null, null, history));
    }

    public Flux<String> chatStream(String userMessage, ConversationContext context) {
        return chatClient.prompt()
                .user(buildConversationPrompt(userMessage, context))
                .stream()
                .content();
    }

    public Flux<String> chatWithPaperStream(Long paperId, String userMessage) {
        return chatWithPaperStream(paperId, userMessage, List.of());
    }

    public Flux<String> chatWithPaperStream(Long paperId, String userMessage, List<ChatHistoryMessage> history) {
        return chatWithPaperStream(paperId, userMessage, new ConversationContext(null, null, history));
    }

    public Flux<String> chatWithPaperStream(Long paperId, String userMessage, ConversationContext context) {
        List<String> contextChunks = embeddingService.similaritySearch(paperId, userMessage, 5);
        String systemPrompt = buildRagSystemPrompt(contextChunks);

        return chatClient.prompt()
                .system(systemPrompt)
                .user(buildConversationPrompt(userMessage, context))
                .stream()
                .content();
    }

    public TraceableChatResponse chatWithEvidence(String userMessage, Long paperId, String scope) {
        Long searchPaperId = "library".equals(scope) ? null : paperId;
        return chatWithEvidence(userMessage, SearchFilters.of(searchPaperId), scope);
    }

    public TraceableChatResponse chatWithEvidence(String userMessage, SearchFilters filters, String scope) {
        return chatWithEvidence(userMessage, filters, scope, ConversationContext.empty());
    }

    public TraceableChatResponse chatWithEvidence(
            String userMessage,
            SearchFilters filters,
            String scope,
            ConversationContext context
    ) {
        // C.13: Enhance query with context before retrieval
        String enhancedQuery = enhanceQuery(userMessage, context);
        String retrievalQuery = enhancedQuery != null ? enhancedQuery : userMessage;

        List<EvidenceChunk> evidence = evidenceSearchService.searchWithParentContext(retrievalQuery, filters, 6);
        if (evidence.isEmpty()) {
            return new TraceableChatResponse(
                    "本地文献库中没有足够信息回答这个问题。请上传更多相关论文，或换一个更具体的问题。",
                    evidence
            );
        }

        // C.14: Compress evidence to query-relevant sentences
        List<EvidenceChunk> compressedEvidence = compressEvidence(userMessage, evidence);

        // B.8: Compress context to query-relevant parts
        ConversationContext compressedContext = compressContext(userMessage, context);

        String prompt = buildTraceablePrompt(userMessage, compressedEvidence, null, compressedContext);
        String answer = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
        return new TraceableChatResponse(verifyAnswerAgainstEvidence(userMessage, answer, compressedEvidence), compressedEvidence);
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
        Long searchPaperId = "library".equals(scope) ? null : paperId;
        return chatWithEvidenceStream(userMessage, SearchFilters.of(searchPaperId), scope, history);
    }

    public Flux<TraceableChatStreamEvent> chatWithEvidenceStream(
            String userMessage,
            SearchFilters filters,
            String scope,
            List<ChatHistoryMessage> history
    ) {
        return chatWithEvidenceStream(userMessage, filters, scope, new ConversationContext(null, null, history));
    }

    public Flux<TraceableChatStreamEvent> chatWithEvidenceStream(
            String userMessage,
            SearchFilters filters,
            String scope,
            ConversationContext context
    ) {
        boolean isLibraryScope = "library".equals(scope);

        // C.13: Enhance query with context before retrieval
        String enhancedQuery = enhanceQuery(userMessage, context);
        String retrievalQuery = enhancedQuery != null ? enhancedQuery : userMessage;

        List<EvidenceChunk> rawEvidence = evidenceSearchService.searchWithParentContext(retrievalQuery, filters, 8);

        // C.14: Compress evidence to query-relevant sentences
        List<EvidenceChunk> evidence = compressEvidence(userMessage, rawEvidence);

        // Build library overview for library-scoped conversations
        String libraryOverview = isLibraryScope ? buildLibraryOverview() : null;

        // Even without matching evidence, answer library meta-questions using the overview
        if (evidence.isEmpty() && (libraryOverview == null || libraryOverview.isBlank())) {
            return Flux.just(
                    TraceableChatStreamEvent.answer("本地文献库中没有足够信息回答这个问题。请上传更多相关论文，或换一个更具体的问题。"),
                    TraceableChatStreamEvent.done()
            );
        }

        // B.8: Compress context to query-relevant parts
        ConversationContext compressedContext = compressContext(userMessage, context);

        String prompt = buildTraceablePrompt(userMessage, evidence, libraryOverview, compressedContext);
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
        return buildTraceablePrompt(userMessage, evidence, libraryOverview, new ConversationContext(null, null, history));
    }

    private String buildTraceablePrompt(
            String userMessage,
            List<EvidenceChunk> evidence,
            String libraryOverview,
            ConversationContext conversationContext
    ) {
        String context = formatEvidenceContext(evidence);

        boolean hasLibrary = libraryOverview != null && !libraryOverview.isBlank();
        boolean hasEvidence = !evidence.isEmpty();

        // B.9: Token budget allocation (chars/3 ≈ tokens for mixed Chinese-English)
        TokenBudget budget = new TokenBudget(8000);
        int TOK_INSTRUCTIONS = 400;
        int TOK_LIBRARY = hasLibrary ? 600 : 0;
        int TOK_EVIDENCE = hasEvidence ? Math.min(2000, budget.remainingAfter(TOK_INSTRUCTIONS + TOK_LIBRARY + 1500 + 1000)) : 0;
        int TOK_MEMORY = 1000;
        int TOK_HISTORY = 1500;
        // User question gets the rest

        StringBuilder prompt = new StringBuilder();
        prompt.append("你是一个严谨的学术论文助手，同时也是用户的文献库管家。\n\n");

        // Library overview section
        if (hasLibrary) {
            prompt.append("## 用户的文献库\n");
            prompt.append("以下是用户文献库的完整概况，包含所有论文的标题、作者、标签、状态和摘要。\n");
            prompt.append("当用户询问「文献库有多少篇论文」「有哪些主题」「关于XX的论文有哪些」等文献库管理类问题时，请基于此概况回答。\n");
            prompt.append(budget.truncateToBudget(libraryOverview, TOK_LIBRARY));
            prompt.append("\n\n");
        }

        // Evidence section
        if (hasEvidence) {
            prompt.append("## 语义搜索匹配到的内容片段\n");
            prompt.append("以下是基于用户问题匹配的相关论文片段。当用户询问论文具体内容、方法、结论等细节时，请基于这些片段回答，并使用 [1]、[2] 编号标注来源。\n");
            prompt.append(budget.truncateToBudget(context, TOK_EVIDENCE));
            prompt.append("\n");
        }

        // Instructions
        prompt.append("## 指引\n");
        prompt.append("- 每个关于论文内容的事实性结论都必须由上方证据直接支持，并标注至少一个来源编号，例如 [1]\n");
        prompt.append("- 关于用户偏好、目标、既定决策：使用「Conversation Memory」中的长期记忆和结构化状态来个性化回答\n");
        prompt.append("- 不要把长期记忆当成论文证据来引用——\"用户之前说过偏好中文\"是合理的记忆使用，\"论文X使用了方法Y\"必须来自证据片段\n");
        prompt.append("- 删除没有证据支持的数字、方法、对比、作者、年份、数据集和结论，不要猜测\n");
        prompt.append("- 文献库概况类问题（总数、主题、分布）：使用「文献库」部分回答\n");
        if (hasEvidence) {
            prompt.append("- 论文内容类问题（方法、结论、细节）：使用「内容片段」部分回答，标注 [1][2] 来源\n");
        } else {
            prompt.append("- 当前没有匹配到相关论文片段，如果用户问的是内容细节类问题，请说明文献库中暂无相关内容\n");
        }
        prompt.append("- 不要编造论文、作者、实验数据或引用\n");
        prompt.append("- 回答使用中文，保持学术、准确、有帮助的风格\n\n");

        // History with budget
        prompt.append("历史对话：\n");
        appendConversationContext(prompt, conversationContext, TOK_MEMORY, TOK_HISTORY);
        prompt.append("\n\n");

        // User question
        prompt.append("用户问题：\n");
        prompt.append(userMessage);

        int estimatedTokens = budget.estimateTokens(prompt.toString());
        log.debug("Traceable prompt: ~{} tokens (budget: {} evidence, {} library, {} memory, {} history)",
                estimatedTokens, TOK_EVIDENCE, TOK_LIBRARY, TOK_MEMORY, TOK_HISTORY);

        return prompt.toString();
    }

    private String verifyAnswerAgainstEvidence(String userMessage, String draftAnswer, List<EvidenceChunk> evidence) {
        if (draftAnswer == null || draftAnswer.isBlank() || evidence == null || evidence.isEmpty()) {
            return draftAnswer;
        }
        try {
            String prompt = """
                    You are a citation-grounded RAG verifier.

                    User question:
                    %s

                    Evidence:
                    %s

                    Draft answer:
                    %s

                    Verify the draft answer against the evidence.
                    Rules:
                    - Keep only claims that are directly supported by the evidence.
                    - Every factual claim about paper content must retain a citation like [1] or [2].
                    - Remove unsupported numbers, methods, comparisons, authors, years, datasets, and conclusions.
                    - If the evidence is insufficient, say so briefly.
                    - Return ONLY the revised final answer in Chinese.
                    """.formatted(userMessage, formatEvidenceContext(evidence), draftAnswer);
            String verified = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            return verified == null || verified.isBlank() ? draftAnswer : verified;
        } catch (Exception e) {
            log.warn("Claim verification failed: {}", e.getMessage());
            return draftAnswer;
        }
    }

    private String formatEvidenceContext(List<EvidenceChunk> evidence) {
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
        return context.toString();
    }

    private String buildConversationPrompt(String userMessage, ConversationContext context) {
        ConversationContext safeContext = context == null ? ConversationContext.empty() : context;
        if (!safeContext.hasMemory() && safeContext.safeRecentMessages().isEmpty()) return userMessage;
        StringBuilder prompt = new StringBuilder();
        prompt.append("Please use the following conversation memory for continuity, but prioritize the current user question.\n\n");
        appendConversationContext(prompt, safeContext);
        prompt.append("\n\nCurrent question:\n");
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

    private void appendConversationContext(StringBuilder prompt, ConversationContext context) {
        appendConversationContext(prompt, context, 1000, 1500);
    }

    private void appendConversationContext(StringBuilder prompt, ConversationContext context, int memoryBudgetTokens, int historyBudgetTokens) {
        ConversationContext safeContext = context == null ? ConversationContext.empty() : context;
        TokenBudget budget = new TokenBudget(memoryBudgetTokens + historyBudgetTokens);
        prompt.append("## Conversation Memory\n");
        if (safeContext.rollingSummary() != null && !safeContext.rollingSummary().isBlank()) {
            prompt.append("Rolling summary (compressed older conversation):\n");
            prompt.append(safeContext.rollingSummary().trim()).append("\n\n");
        }
        if (safeContext.stateJson() != null && !safeContext.stateJson().isBlank()) {
            prompt.append("Structured session state (goals, decisions, questions, preferences):\n");
            prompt.append(safeContext.stateJson().trim()).append("\n\n");
        }
        if (safeContext.hasLongTermMemories()) {
            prompt.append("Long-term conversation memories (persistent facts, preferences, decisions):\n");
            List<String> memories = safeContext.safeLongTermMemories();
            StringBuilder memText = new StringBuilder();
            for (String memory : memories) {
                memText.append("- ").append(memory).append("\n");
            }
            prompt.append(budget.truncateToBudget(memText.toString(), memoryBudgetTokens));
            prompt.append("\n");
        }
        prompt.append("Recent conversation window:\n");
        String history = formatHistory(safeContext.safeRecentMessages());
        prompt.append(budget.truncateToBudget(history, historyBudgetTokens));
    }

    private String formatHistory(List<ChatHistoryMessage> history) {
        if (history == null || history.isEmpty()) return "无";
        return history.stream()
                .map(item -> "%s：%s".formatted(item.role(), item.content()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("无");
    }

    // ==================== C.13: Query Enhancement ====================

    /**
     * Enhance the user query with relevant terms from conversation memory.
     * This improves retrieval quality by adding context-specific keywords.
     * Returns null if no enhancement is possible (use original query).
     */
    private String enhanceQuery(String query, ConversationContext context) {
        if (query == null || query.isBlank() || context == null) return null;

        List<String> boostTerms = new ArrayList<>();

        // Extract terms from structured state
        if (context.stateJson() != null && !context.stateJson().isBlank()) {
            String state = context.stateJson().toLowerCase(Locale.ROOT);
            // Extract quoted strings (decisions, preferences)
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]{3,80})\"").matcher(state);
            while (m.find()) {
                String term = m.group(1).trim();
                if (!term.equalsIgnoreCase(query.trim()) && !query.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT))) {
                    boostTerms.add(term);
                }
            }
        }

        // Extract terms from long-term memories
        if (context.hasLongTermMemories()) {
            Set<String> queryWords = extractKeywords(query);
            for (String memory : context.safeLongTermMemories()) {
                // Only use memories that already overlap with the query
                if (keywordOverlap(query, memory) > 0.3) {
                    Set<String> memWords = extractKeywords(memory);
                    memWords.removeAll(queryWords);
                    for (String w : memWords) {
                        if (w.length() >= 3 && !boostTerms.contains(w)) {
                            boostTerms.add(w);
                        }
                    }
                }
            }
        }

        if (boostTerms.isEmpty()) return null;

        // Limit to top 5 extra terms to avoid query drift
        String enhanced = query + " " + boostTerms.stream().limit(5).collect(Collectors.joining(" "));
        log.debug("Enhanced query: {} -> {}", query, enhanced);
        return enhanced;
    }

    // ==================== C.14: Evidence Compression ====================

    /**
     * Compress evidence chunks to only keep sentences relevant to the query.
     * Preserves citation markers so the LLM can still reference sources.
     */
    private List<EvidenceChunk> compressEvidence(String query, List<EvidenceChunk> evidence) {
        if (query == null || query.isBlank() || evidence == null || evidence.isEmpty()) {
            return evidence == null ? List.of() : evidence;
        }
        if (evidence.size() <= 3) return evidence; // don't compress small sets

        Set<String> queryKeywords = extractKeywords(query);
        if (queryKeywords.isEmpty()) return evidence;

        List<EvidenceChunk> compressed = new ArrayList<>();
        for (EvidenceChunk chunk : evidence) {
            String compressedContent = compressToRelevantSentences(chunk.content(), queryKeywords);
            if (compressedContent != null && !compressedContent.isBlank()) {
                compressed.add(new EvidenceChunk(
                        chunk.chunkId(), chunk.paperId(), chunk.paperTitle(),
                        chunk.chunkIndex(), compressedContent, chunk.similarity()
                ));
            } else {
                // Keep original if nothing relevant found (defensive)
                compressed.add(chunk);
            }
        }
        int beforeChars = evidence.stream().mapToInt(e -> e.content().length()).sum();
        int afterChars = compressed.stream().mapToInt(e -> e.content().length()).sum();
        if (afterChars > 0 && afterChars < beforeChars) {
            log.debug("Evidence compressed: {} -> {} chars ({}%)",
                    beforeChars, afterChars, Math.round(100.0 * afterChars / beforeChars));
        }
        return compressed;
    }

    /**
     * Keep only sentences that contain at least one query keyword.
     * Falls back to original content if no sentence matches.
     */
    private String compressToRelevantSentences(String content, Set<String> keywords) {
        if (content == null || content.isBlank() || keywords.isEmpty()) return content;

        // Split into sentences (Chinese and English aware)
        String[] sentences = content.split("(?<=[。！？.!?\\n])\\s*");
        List<String> relevant = new ArrayList<>();

        for (String sentence : sentences) {
            if (sentence.trim().isEmpty()) continue;
            String lowerSentence = sentence.toLowerCase(Locale.ROOT);
            boolean hasKeyword = keywords.stream().anyMatch(lowerSentence::contains);
            if (hasKeyword) {
                relevant.add(sentence.trim());
            }
        }

        if (relevant.isEmpty()) {
            // Fallback: keep first 2 sentences + last 2 sentences (intro + conclusion of chunk)
            if (sentences.length <= 4) return content;
            StringBuilder fallback = new StringBuilder();
            for (int i = 0; i < Math.min(2, sentences.length); i++) fallback.append(sentences[i].trim()).append(" ");
            fallback.append("... ");
            for (int i = Math.max(2, sentences.length - 2); i < sentences.length; i++) fallback.append(sentences[i].trim()).append(" ");
            return fallback.toString().trim();
        }

        return String.join(" ", relevant);
    }

    // ==================== B.8: Query-Aware Context Compression ====================

    /**
     * Compress conversation context to only include parts relevant to the current query.
     * This reduces prompt size and focuses the LLM on what matters.
     */
    private ConversationContext compressContext(String query, ConversationContext context) {
        if (context == null || query == null || query.isBlank()) {
            return context == null ? ConversationContext.empty() : context;
        }

        List<String> filteredMemories = context.hasLongTermMemories()
                ? filterMemoriesByQuery(query, context.safeLongTermMemories())
                : List.of();

        return ConversationContext.withMemories(
                context.rollingSummary(),    // keep summary — already compressed
                context.stateJson(),         // keep state — small and always relevant
                context.safeRecentMessages(), // keep recent messages — always relevant
                filteredMemories             // only query-relevant memories
        );
    }

    /**
     * Filter long-term memories to only those with keyword overlap with the query.
     */
    private List<String> filterMemoriesByQuery(String query, List<String> memories) {
        if (memories == null || memories.isEmpty()) return List.of();
        if (memories.size() <= 3) return memories; // small set — keep all

        List<String> filtered = new ArrayList<>();
        List<String> noMatch = new ArrayList<>();
        for (String memory : memories) {
            if (keywordOverlap(query, memory) > 0.0) {
                filtered.add(memory);
            } else {
                noMatch.add(memory);
            }
        }

        // If filtering removed everything, keep top half to avoid losing context
        if (filtered.isEmpty()) {
            return memories.subList(0, Math.max(2, memories.size() / 2));
        }

        // Add back a few non-matching memories to preserve diversity (max 2)
        int spare = Math.min(2, noMatch.size());
        for (int i = 0; i < spare; i++) {
            filtered.add(noMatch.get(i));
        }

        return filtered;
    }

    // ==================== Shared Keyword Utilities ====================

    private Set<String> extractKeywords(String text) {
        if (text == null || text.isBlank()) return Set.of();
        String[] words = text.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
        return Arrays.stream(words)
                .filter(w -> w.length() >= 2)
                .collect(Collectors.toSet());
    }

    private double keywordOverlap(String query, String content) {
        if (query == null || query.isBlank() || content == null || content.isBlank()) return 0.0;
        String[] queryTerms = query.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
        String lowerContent = content.toLowerCase(Locale.ROOT);
        int matches = 0;
        int useful = 0;
        for (String term : queryTerms) {
            if (term.length() < 2) continue;
            useful++;
            if (lowerContent.contains(term)) matches++;
        }
        return useful == 0 ? 0.0 : (double) matches / useful;
    }

    // ==================== B.9: Token Budget ====================

    /**
     * Simple token budget manager.
     * Estimates tokens as chars/3 (conservative for mixed Chinese-English text).
     * Truncates content intelligently when it exceeds budget:
     *   - Keeps the beginning (most important context)
     *   - Keeps the end (recent facts/numbers)
     *   - Cuts from the middle
     */
    private record TokenBudget(int maxTokens) {

        int estimateTokens(String text) {
            if (text == null || text.isBlank()) return 0;
            return text.length() / 3;
        }

        int remainingAfter(int used) {
            return Math.max(0, maxTokens - used);
        }

        String truncateToBudget(String text, int budgetTokens) {
            if (text == null || text.isBlank()) return "";
            int charBudget = budgetTokens * 3;
            if (text.length() <= charBudget) return text;

            // Strategy: keep first 60% + last 40% of budget, cut middle
            int headChars = (int) (charBudget * 0.6);
            int tailChars = charBudget - headChars;

            String head = text.substring(0, Math.min(headChars, text.length()));
            String tail = text.substring(Math.max(headChars, text.length() - tailChars));

            return head + "\n... [truncated " + (text.length() - charBudget) + " chars] ...\n" + tail;
        }
    }
}
