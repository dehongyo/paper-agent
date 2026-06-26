package com.paperagent.dto;

import java.util.List;

public record SaveVersionRequest(
    String topic,
    String outline,
    String draft,
    List<ReferenceItem> references
) {}
