package com.roambupt.service.search.embedding;

/** 文本向量化客户端；不可用或失败时抛 {@link EmbeddingException}。 */
public interface EmbeddingClient {

    float[] embed(String text);
}
