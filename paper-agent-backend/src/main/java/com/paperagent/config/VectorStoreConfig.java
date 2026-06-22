package com.paperagent.config;

import org.springframework.context.annotation.Configuration;

/**
 * Vector storage is handled by EmbeddingService via raw JDBC.
 * This avoids PgVectorStore schema incompatibility with our custom paper_chunks table.
 */
@Configuration
public class VectorStoreConfig {
    // No beans needed — EmbeddingModel is auto-configured by Spring AI OpenAI starter
}
