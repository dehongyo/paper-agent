package com.paperagent.dto;

public record ChatHistoryMessage(
        String role,
        String content
) {}
