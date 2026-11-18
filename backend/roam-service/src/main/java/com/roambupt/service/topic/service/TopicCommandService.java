package com.roambupt.service.topic.service;

import java.util.Collection;

/** 主题写服务。其他域通过它改动主题数据，不直接写 topic 表。 */
public interface TopicCommandService {

    /** 帖子发布后给挂载的地点加热度。 */
    void addHeat(Collection<Long> topicIds);
}
