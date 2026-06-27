package com.paperagent.dto;

public record ReviewStartEvent(
        String type,
        String content,
        String message,
        String phase,
        String sectionTitle,
        Integer current,
        Integer total
) {
    public static ReviewStartEvent text(String content) {
        return new ReviewStartEvent("text", content, null, null, null, null, null);
    }

    public static ReviewStartEvent status(String message) {
        return status(message, null, null, null, null);
    }

    public static ReviewStartEvent status(String message, String phase, String sectionTitle, Integer current, Integer total) {
        return new ReviewStartEvent("status", null, message, phase, sectionTitle, current, total);
    }

    public static ReviewStartEvent done() {
        return new ReviewStartEvent("done", null, "Review complete", "done", null, null, null);
    }

    public static ReviewStartEvent error(String message) {
        return new ReviewStartEvent("error", null, message, "error", null, null, null);
    }
}