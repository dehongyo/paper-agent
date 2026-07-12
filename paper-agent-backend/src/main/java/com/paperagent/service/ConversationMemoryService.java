package com.paperagent.service;

import com.paperagent.dto.ConversationContext;
import com.paperagent.entity.ConversationMemory;
import com.paperagent.repository.ConversationMemoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private static final double MEMORY_IMPORTANCE_THRESHOLD = 0.70;
    private static final double MEMORY_CONFIDENCE_THRESHOLD = 0.70;
    private static final int MEMORY_CONTENT_MAX_CHARS = 1200;
    private static final int RECALL_DEFAULT_LIMIT = 10;
    private static final int RECALL_MAX_LIMIT = 20;
    private static final double DEDUP_SIMILARITY_THRESHOLD = 0.55;

    private final ChatClient chatClient;
    private final ChatSessionService chatSessionService;
    private final ConversationMemoryRepository memoryRepository;
    private final EmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    // ==================== Write Side ====================

    public void updateAfterTurn(Long sessionId, String userMessage, String assistantMessage) {
        if (sessionId == null || isBlank(userMessage) || isBlank(assistantMessage)) {
            return;
        }

        try {
            ConversationContext context = chatSessionService.getConversationContext(sessionId);
            JsonNode update = parseModelUpdate(requestModelUpdate(context, userMessage, assistantMessage));
            String rollingSummary = text(update, "rollingSummary");
            String stateJson = objectMapper.writeValueAsString(update.get("state"));

            chatSessionService.updateConversationMemory(sessionId, rollingSummary, stateJson);
            writeMemoryCandidates(sessionId, update.get("memoryCandidates"));
        } catch (Exception e) {
            log.warn("Conversation memory update failed; falling back to rolling append: {}", e.getMessage());
            chatSessionService.appendTurnToRollingSummary(sessionId, userMessage, assistantMessage);
        }
    }

    // ==================== Read Side — Memory Recall ====================

    /**
     * Recall relevant long-term memories for a session.
     * Memories are scored by importance (0.4) + confidence (0.3) + recency (0.3),
     * then optionally boosted by keyword overlap with the current query.
     */
    public List<String> recallMemories(Long sessionId, String query, int limit) {
        if (sessionId == null) return List.of();

        int safeLimit = Math.clamp(limit, 1, RECALL_MAX_LIMIT);
        List<ConversationMemory> candidates = memoryRepository.findRelevantBySession(sessionId);

        if (candidates.isEmpty()) return List.of();

        // Score: importance * 0.4 + confidence * 0.3 + recency * 0.3 + queryBoost
        LocalDateTime now = LocalDateTime.now();
        List<ScoredMemory> scored = candidates.stream()
                .map(m -> {
                    double recency = recencyScore(m.getUpdatedAt(), now);
                    double baseScore = m.getImportance() * 0.4 + m.getConfidence() * 0.3 + recency * 0.3;
                    double queryBoost = query != null ? keywordOverlap(query, m.getContent()) * 0.15 : 0.0;
                    return new ScoredMemory(m, baseScore + queryBoost);
                })
                .sorted(Comparator.comparingDouble(ScoredMemory::score).reversed())
                .toList();

        // Deduplicate by content similarity while preserving order
        List<String> result = new ArrayList<>();
        for (ScoredMemory sm : scored) {
            if (result.size() >= safeLimit) break;
            String content = sm.memory().getContent();
            if (result.stream().noneMatch(existing -> contentSimilarity(existing, content) >= DEDUP_SIMILARITY_THRESHOLD)) {
                result.add(content);
            }
        }
        return result;
    }

    public List<String> recallMemories(Long sessionId, int limit) {
        return recallMemories(sessionId, null, limit);
    }

    public List<String> recallMemories(Long sessionId) {
        return recallMemories(sessionId, null, RECALL_DEFAULT_LIMIT);
    }

    /**
     * Build a compact formatted string of recalled memories for prompt injection.
     */
    public String formatRecalledMemories(Long sessionId, String query, int limit) {
        List<String> memories = recallMemories(sessionId, query, limit);
        if (memories.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < memories.size(); i++) {
            sb.append("- ").append(memories.get(i));
            if (i < memories.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    // ==================== Memory Management ====================

    /**
     * Delete all memories for a session (e.g. when session is deleted).
     */
    public void deleteBySessionId(Long sessionId) {
        if (sessionId == null) return;
        memoryRepository.deleteBySessionId(sessionId);
    }

    /**
     * Get all memories for a session (for debug/display).
     */
    public List<ConversationMemory> getMemories(Long sessionId) {
        if (sessionId == null) return List.of();
        return memoryRepository.findBySessionIdOrderByUpdatedAtDesc(sessionId);
    }

    // ==================== Private: Model Interaction ====================

    private String requestModelUpdate(ConversationContext context, String userMessage, String assistantMessage) {
        String prompt = """
                You update enterprise conversation memory for a research-paper RAG assistant.
                Output STRICTLY valid JSON. No markdown. No extra text.

                Stable state_json schema:
                {
                  "schemaVersion": 1,
                  "currentGoal": "short active goal or empty string",
                  "confirmedDecisions": ["stable user-confirmed decisions"],
                  "openQuestions": ["unresolved questions"],
                  "activePaperIds": [1, 2],
                  "userPreferences": ["stable answer or workflow preferences"]
                }

                Output schema:
                {
                  "rollingSummary": "compact summary of the durable conversation so far",
                  "state": { ...state_json schema above... },
                  "memoryCandidates": [
                    {
                      "shouldRemember": true,
                      "scope": "session|project|global|paper",
                      "memoryType": "user_preference|confirmed_decision|project_goal|paper_fact|workflow_rule|open_question|research_topic|methodology_choice",
                      "content": "atomic durable memory — self-contained, independently understandable",
                      "importance": 0.0,
                      "confidence": 0.0,
                      "reason": "one sentence explaining WHY this memory is worth persisting"
                    }
                  ]
                }

                Memory write gate — STRICT rules:
                - shouldRemember=true ONLY for:
                  * user_preference: the user's language/style/workflow/answer-format preferences
                  * confirmed_decision: explicit decisions the user confirmed
                  * project_goal: research goals or project-level objectives
                  * paper_fact: verified facts about a specific paper
                  * workflow_rule: reusable task patterns the user established
                  * open_question: important unresolved questions worth tracking
                  * research_topic: declared research themes or interests
                  * methodology_choice: preferred methods, tools, or approaches
                - NEVER remember (shouldRemember=false):
                  * greetings, thanks, small talk
                  * transient one-time tasks already completed
                  * model hallucinations or unsupported guesses
                  * API keys, passwords, tokens, personal identity numbers, emails, phone numbers
                  * content that paraphrases the paper evidence (that's what RAG is for)
                - importance scoring guide:
                  * 0.9-1.0: user explicitly stated preference / confirmed major decision / core research goal
                  * 0.7-0.8: useful context for future turns / reusable workflow pattern
                  * 0.5-0.6: interesting but not critical
                  * <0.5: do NOT write (transient chatter)
                - confidence scoring guide:
                  * 0.9-1.0: user explicitly stated this
                  * 0.7-0.8: clearly implied by conversation
                  * 0.5-0.6: inferred with some ambiguity
                  * <0.5: do NOT write (too uncertain)
                - Keep memoryCandidates atomic — one fact per entry, no compound memories.
                - The "reason" field should explain preservation rationale, not repeat the content.

                Previous rolling summary:
                %s

                Previous state_json:
                %s

                Current user message:
                %s

                Current assistant answer:
                %s
                """.formatted(
                nvl(context == null ? null : context.rollingSummary()),
                nvl(context == null ? null : context.stateJson()),
                userMessage,
                assistantMessage
        );
        return chatClient.prompt().user(prompt).call().content();
    }

    private JsonNode parseModelUpdate(String output) throws Exception {
        String json = extractJson(output);
        JsonNode root = objectMapper.readTree(json);
        if (root == null || !root.has("rollingSummary") || !root.has("state") || !root.has("memoryCandidates")) {
            throw new IllegalArgumentException("memory update JSON missing required fields");
        }
        JsonNode state = root.get("state");
        if (state == null || !state.has("schemaVersion") || !state.has("currentGoal")
                || !state.has("confirmedDecisions") || !state.has("openQuestions")
                || !state.has("activePaperIds") || !state.has("userPreferences")) {
            throw new IllegalArgumentException("state_json does not match schemaVersion 1");
        }
        return root;
    }

    // ==================== Private: Memory Write with Dedup ====================

    private void writeMemoryCandidates(Long sessionId, JsonNode candidates) {
        if (candidates == null || !candidates.isArray()) {
            return;
        }

        // Load existing memories for dedup
        List<ConversationMemory> existingMemories = memoryRepository.findBySessionIdOrderByUpdatedAtDesc(sessionId);

        for (JsonNode candidate : candidates) {
            if (!candidate.path("shouldRemember").asBoolean(false)) {
                continue;
            }
            double importance = candidate.path("importance").asDouble(0.0);
            double confidence = candidate.path("confidence").asDouble(0.0);
            String content = text(candidate, "content");
            String memoryType = normalize(candidate.path("memoryType").asText("project_fact"), "project_fact", 50);
            String reason = truncate(text(candidate, "reason"), 500);

            if (importance < MEMORY_IMPORTANCE_THRESHOLD
                    || confidence < MEMORY_CONFIDENCE_THRESHOLD
                    || isBlank(content)) {
                continue;
            }

            // Sensitivity filter: reject memories containing secrets, credentials, PII
            if (containsSensitiveContent(content)) {
                log.info("Rejected memory candidate due to sensitive content: type={}", memoryType);
                continue;
            }

            // Low-value filter: reject trivial chatter
            if (isLowValueContent(content)) {
                log.debug("Rejected low-value memory candidate: type={}", memoryType);
                continue;
            }

            // Check for similar existing memory → update instead of insert
            Optional<ConversationMemory> similar = findSimilarMemory(existingMemories, content, memoryType);
            if (similar.isPresent()) {
                ConversationMemory existing = similar.get();
                existing.setContent(truncate(content, MEMORY_CONTENT_MAX_CHARS));
                existing.setImportance(Math.max(existing.getImportance(), importance));
                existing.setConfidence(Math.max(existing.getConfidence(), confidence));
                existing.setReason(isBlank(reason) ? existing.getReason() : reason);
                existing.setUpdatedAt(LocalDateTime.now());
                memoryRepository.save(existing);
                log.debug("Updated existing memory id={} type={}", existing.getId(), memoryType);
            } else {
                ConversationMemory saved = memoryRepository.save(ConversationMemory.builder()
                        .sessionId(sessionId)
                        .scope(normalize(candidate.path("scope").asText("session"), "session", 30))
                        .memoryType(memoryType)
                        .content(truncate(content, MEMORY_CONTENT_MAX_CHARS))
                        .importance(importance)
                        .confidence(confidence)
                        .reason(isBlank(reason) ? null : reason)
                        .source("conversation_gate")
                        .build());
                existingMemories.add(saved); // track for subsequent dedup within this batch
                // H.36: Async-embed the new memory for future vector recall
                embedMemoryAsync(saved);
                log.debug("Created new memory id={} type={}", saved.getId(), memoryType);
            }
        }
    }

    /**
     * Trigger embedding generation for a newly created/updated memory.
     * Runs asynchronously — failures are logged but don't block the write path.
     */
    private void embedMemoryAsync(ConversationMemory memory) {
        Thread.startVirtualThread(() -> {
            try {
                embeddingService.embedMemoryContent(memory.getId(), memory.getContent());
            } catch (Exception e) {
                log.warn("Async memory embedding failed for id={}: {}", memory.getId(), e.getMessage());
            }
        });
    }

    // ==================== H.36: Vector-based Recall ====================

    /**
     * Recall memories by vector similarity to a query.
     * This enables cross-session semantic recall — finding memories
     * that are semantically similar even without keyword overlap.
     * Results are combined with the scored recall for hybrid ranking.
     */
    public List<String> recallByVector(String query, String scope, int limit) {
        if (query == null || query.isBlank()) return List.of();

        List<Long> ids = embeddingService.searchMemoriesByVector(query, scope, limit);
        if (ids.isEmpty()) return List.of();

        List<ConversationMemory> memories = memoryRepository.findAllById(ids);
        // Preserve vector search order
        Map<Long, ConversationMemory> byId = memories.stream()
                .collect(Collectors.toMap(ConversationMemory::getId, m -> m, (a, b) -> a, LinkedHashMap::new));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(ConversationMemory::getContent)
                .toList();
    }

    // ==================== G.32: Memory Deletion ====================

    /**
     * Delete a single memory by ID.
     */
    public void deleteMemory(Long memoryId) {
        if (memoryId == null) return;
        memoryRepository.deleteById(memoryId);
        log.debug("Deleted memory id={}", memoryId);
    }

    // ==================== Private: Content Filters ====================

    /**
     * Reject memories that contain secrets, credentials, or PII.
     */
    private boolean containsSensitiveContent(String content) {
        if (isBlank(content)) return false;
        String lower = content.toLowerCase(Locale.ROOT);
        // API key patterns — catch "api key: X", "api key= X", "api key is X", "apikey: X"
        if (lower.matches(".*(api[ _-]?key|apikey|secret[ _-]?key|access[ _-]?key|api[ _-]?token|password|passwd)\\s*[:=]\\s*\\S.*")) return true;
        if (lower.matches(".*(api[ _-]?key|apikey|secret[ _-]?key|access[ _-]?key|api[ _-]?token|password|passwd)\\s+is\\s+\\S.*")) return true;
        // JWT / base64 token patterns (long base64 strings)
        if (lower.matches(".*eyJ[a-z0-9_-]{20,}\\.[a-z0-9_-]{20,}\\.[a-z0-9_-]{20,}.*")) return true;
        // Email patterns
        if (lower.matches(".*[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}.*")) return true;
        // Phone number patterns (Chinese + international)
        if (lower.matches(".*(1[3-9]\\d{9}|\\+\\d{7,15}).*")) return true;
        // ID card patterns (Chinese)
        if (lower.matches(".*\\d{17}[\\dxX].*")) return true;
        // Generic credential keywords in memory content
        if (lower.contains("password") || lower.contains("passwd") || lower.contains("secret key")) return true;
        return false;
    }

    /**
     * Reject memories that are trivial chatter, not durable knowledge.
     */
    private boolean isLowValueContent(String content) {
        if (isBlank(content)) return true;
        String lower = content.toLowerCase(Locale.ROOT).trim();
        // Bare greetings
        if (lower.matches("^(hi|hello|hey|ok|okay|thanks|thank you|bye|goodbye|yes|no|好的|谢谢|再见|嗯|哦|对|是|好)[!.]?$")) return true;
        // Too short to be useful (after removing common prefixes)
        String stripped = lower.replaceAll("^(user|assistant|the user|the assistant)\\s+(said|asked|replied|mentioned)\\s+", "");
        if (stripped.length() < 15) return true;
        return false;
    }

    /**
     * Find an existing memory that is similar to the candidate.
     * Match: same memoryType AND content similarity >= threshold.
     */
    private Optional<ConversationMemory> findSimilarMemory(
            List<ConversationMemory> existingMemories,
            String newContent,
            String memoryType
    ) {
        return existingMemories.stream()
                .filter(m -> memoryType.equals(m.getMemoryType()))
                .filter(m -> contentSimilarity(m.getContent(), newContent) >= DEDUP_SIMILARITY_THRESHOLD)
                .max(Comparator.comparingDouble(m -> contentSimilarity(m.getContent(), newContent)));
    }

    // ==================== Private: Scoring Utilities ====================

    /**
     * Recency score: 1.0 for now, decaying to 0.0 over ~7 days.
     */
    private double recencyScore(LocalDateTime updatedAt, LocalDateTime now) {
        if (updatedAt == null) return 0.0;
        long hoursSince = ChronoUnit.HOURS.between(updatedAt, now);
        if (hoursSince <= 0) return 1.0;
        // Half-life of ~24 hours
        return Math.exp(-hoursSince / 34.7); // ln(2)/24 ≈ 1/34.7
    }

    /**
     * Simple keyword overlap score between query and memory content.
     */
    private double keywordOverlap(String query, String content) {
        if (isBlank(query) || isBlank(content)) return 0.0;
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

    /**
     * Jaccard-like word-level similarity for dedup.
     */
    private double contentSimilarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        if (a.equals(b)) return 1.0;
        Set<String> wordsA = wordSet(a);
        Set<String> wordsB = wordSet(b);
        if (wordsA.isEmpty() && wordsB.isEmpty()) return 1.0;
        Set<String> union = new HashSet<>(wordsA);
        union.addAll(wordsB);
        Set<String> intersection = new HashSet<>(wordsA);
        intersection.retainAll(wordsB);
        return (double) intersection.size() / union.size();
    }

    private Set<String> wordSet(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}]+"))
                .filter(w -> w.length() >= 2)
                .collect(Collectors.toSet());
    }

    // ==================== Private: String Utilities ====================

    private String extractJson(String output) {
        if (output == null) {
            return "{}";
        }
        String trimmed = output.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        return start >= 0 && end > start ? trimmed.substring(start, end + 1) : trimmed;
    }

    private String text(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field) == null) {
            return "";
        }
        return node.get(field).asText();
    }

    private String normalize(String value, String fallback, int maxChars) {
        String normalized = isBlank(value) ? fallback : value.trim().toLowerCase().replaceAll("[^a-z0-9_\\-]", "_");
        return truncate(normalized, maxChars);
    }

    private String truncate(String value, int maxChars) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() <= maxChars ? trimmed : trimmed.substring(0, maxChars);
    }

    private String nvl(String value) {
        return value == null ? "" : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ==================== Internal Record ====================

    private record ScoredMemory(ConversationMemory memory, double score) {}
}
