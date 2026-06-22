package com.paperagent.service;

import com.paperagent.dto.WritingEvidence;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class WritingService {

    private final ChatClient chatClient;

    public String generateOutline(
            String topic,
            String writingType,
            String language,
            String length,
            List<WritingEvidence> evidence
    ) {
        String prompt = """
                You are an academic writing assistant. Create a structured outline only from the supplied evidence.
                Do not invent papers, authors, years, data, methods, or conclusions.
                If evidence is insufficient, say so clearly.
                Topic: %s
                Writing type: %s
                Language: %s
                Target length: %s

                Evidence:
                %s

                Return a concise outline with section titles and bullet points. Use source markers like [1].
                """.formatted(topic, writingType, language, length, formatEvidence(evidence));

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    public String generateDraft(
            String topic,
            String outline,
            String writingType,
            String language,
            String length,
            List<WritingEvidence> evidence
    ) {
        String prompt = """
                You are a careful academic writing assistant. Write a literature-review style draft only from the supplied evidence.
                Do not invent papers, authors, years, data, methods, or conclusions.
                Every important claim should include source markers like [1] or [2].
                If evidence is insufficient for a claim, state that the local evidence is insufficient.
                Topic: %s
                Writing type: %s
                Language: %s
                Target length: %s

                Outline:
                %s

                Evidence:
                %s

                Return the draft text only.
                """.formatted(topic, writingType, language, length, emptyToFallback(outline, "No outline provided."), formatEvidence(evidence));

        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }

    private String formatEvidence(List<WritingEvidence> evidence) {
        StringBuilder builder = new StringBuilder();
        for (WritingEvidence item : evidence) {
            builder.append("[")
                    .append(item.index())
                    .append("] ")
                    .append(item.paperTitle())
                    .append(" chunk#")
                    .append(item.chunkIndex())
                    .append("\n")
                    .append(item.content())
                    .append("\n\n");
        }
        return builder.toString();
    }

    private String emptyToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
