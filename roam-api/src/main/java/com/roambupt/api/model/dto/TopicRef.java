package com.roambupt.api.model.dto;

/** 发帖时挂的一个主题。sortNo 只对 PLACE 有效。 */
public record TopicRef(Long topicId, Integer sortNo) {
}
