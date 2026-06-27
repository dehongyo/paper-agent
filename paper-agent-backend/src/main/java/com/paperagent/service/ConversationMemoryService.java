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

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private static final double MEMORY_IMPORTANCE_THRESHOLD = 0.70;
    private static final int MEMORY_CONTENT_MAX_CHARS = 1200;

    private final ChatClient chatClient;
    private final ChatSessionService chatSessionService;
    private final ConversationMemoryRepository memoryRepository;
    private final ObjectMapper objectMapper;

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
                      "memoryType": "preference|decision|project_fact|paper_fact|workflow_rule",
                      "content": "atomic durable memory",
                      "importance": 0.0,
                      "confidence": 0.0
                    }
                  ]
                }

                Memory write gate:
                - shouldRemember=true only for durable preferences, confirmed decisions, project facts, paper facts, or workflow rules.
                - Do not remember greetings, transient status, unsupported guesses, secrets, or low-value chatter.
                - Keep memoryCandidates atomic and non-duplicative.

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

    private void writeMemoryCandidates(Long sessionId, JsonNode candidates) {
        if (candidates == null || !candidates.isArray()) {
            return;
        }
        for (JsonNode candidate : candidates) {
            if (!candidate.path("shouldRemember").asBoolean(false)) {
                continue;
            }
            double importance = candidate.path("importance").asDouble(0.0);
            double confidence = candidate.path("confidence").asDouble(0.0);
            String content = text(candidate, "content");
            if (importance < MEMORY_IMPORTANCE_THRESHOLD || isBlank(content)) {
                continue;
            }
            memoryRepository.save(ConversationMemory.builder()
                    .sessionId(sessionId)
                    .scope(normalize(candidate.path("scope").asText("session"), "session", 30))
                    .memoryType(normalize(candidate.path("memoryType").asText("project_fact"), "project_fact", 50))
                    .content(truncate(content, MEMORY_CONTENT_MAX_CHARS))
                    .importance(importance)
                    .confidence(confidence)
                    .source("conversation_gate")
                    .build());
        }
    }

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
}
