package com.paperagent.dto;

public record EvidenceChunk(
        Long chunkId,
        Long paperId,
        String paperTitle,
        Integer chunkIndex,
        String content,
        Double similarity
) {}
