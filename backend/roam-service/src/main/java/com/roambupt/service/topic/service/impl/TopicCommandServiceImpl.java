package com.roambupt.service.topic.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.service.topic.repository.entity.TopicDO;
import com.roambupt.service.topic.repository.mapper.TopicMapper;
import com.roambupt.service.topic.service.TopicCommandService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

/** 主题写服务实现。 */
@Service
public class TopicCommandServiceImpl implements TopicCommandService {

    private final TopicMapper topicMapper;

    public TopicCommandServiceImpl(TopicMapper topicMapper) {
        this.topicMapper = topicMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void addHeat(Collection<Long> topicIds) {
        if (topicIds == null || topicIds.isEmpty()) {
            return;
        }
        topicMapper.update(null, Wrappers.<TopicDO>lambdaUpdate()
                .in(TopicDO::getId, topicIds)
                .setSql("heat = heat + 1"));
    }
}
