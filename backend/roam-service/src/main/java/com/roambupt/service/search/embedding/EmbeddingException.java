package com.roambupt.service.search.embedding;

/** 向量化失败（网络、额度、格式）。调用方应降级为纯 BM25。 */
public class EmbeddingException extends RuntimeException {

    public EmbeddingException(String message, Throwable cause) {
        super(message, cause);
    }

    public EmbeddingException(String message) {
        super(message);
    }
}
