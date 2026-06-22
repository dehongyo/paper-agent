package com.paperagent.dto;

import java.util.List;

public record WritingResponse(
        String topic,
        String outline,
        String draft,
        List<ReferenceItem> references,
        List<WritingEvidence> evidence,
        String exportMarkdown,
        String exportLatex
) {}
