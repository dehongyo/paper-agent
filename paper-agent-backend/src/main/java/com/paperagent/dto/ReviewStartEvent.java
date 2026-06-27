package com.paperagent.dto;

public record ReviewStartEvent(
        String type,    // "text", "done", "error"
        String content,
        String message
) {
    public static ReviewStartEvent text(String content) {
        return new ReviewStartEvent("text", content, null);
    }
    public static ReviewStartEvent done() {
        return new ReviewStartEvent("done", null, "Review complete");
    }
    public static ReviewStartEvent error(String message) {
        return new ReviewStartEvent("error", null, message);
    }
}
