package com.paperagent.dto;

public record WritingEvidence(
        int index,
        Long chunkId,
        Long paperId,
        String paperTitle,
        Integer chunkIndex,
        String content,
        Double similarity
) {}
