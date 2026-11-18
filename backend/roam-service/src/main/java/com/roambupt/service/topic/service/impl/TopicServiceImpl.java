package com.roambupt.service.topic.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.api.model.dto.TopicQuery;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.TopicBriefVO;
import com.roambupt.api.model.vo.TopicVO;
import com.roambupt.service.topic.converter.TopicConverter;
import com.roambupt.service.topic.repository.entity.TopicDO;
import com.roambupt.service.topic.repository.mapper.TopicMapper;
import com.roambupt.service.topic.service.PostCountProvider;
import com.roambupt.service.topic.service.TopicService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 主题读服务实现。 */
@Service
public class TopicServiceImpl implements TopicService {

    private static final int MAX_PAGE_SIZE = 50;

    private final TopicMapper topicMapper;
    private final PostCountProvider postCountProvider;

    public TopicServiceImpl(TopicMapper topicMapper, PostCountProvider postCountProvider) {
        this.topicMapper = topicMapper;
        this.postCountProvider = postCountProvider;
    }

    @Override
    public TopicVO detail(long topicId) {
        TopicDO topic = topicMapper.selectById(topicId);
        if (topic == null || topic.getEnabled() == null || topic.getEnabled() != 1) {
            throw new BusinessException(StatusEnum.NOT_FOUND, "主题不存在");
        }
        return TopicConverter.toVO(topic, postCountProvider.countOnlineByTopic(topicId));
    }

    @Override
    public PageResult<TopicVO> list(TopicQuery query) {
        int page = Math.max(1, query.page());
        int size = Math.min(Math.max(1, query.pageSize()), MAX_PAGE_SIZE);

        var wrapper = Wrappers.<TopicDO>lambdaQuery()
                .eq(TopicDO::getEnabled, 1)
                .eq(StringUtils.isNotBlank(query.type()), TopicDO::getType, query.type())
                .eq(StringUtils.isNotBlank(query.category()), TopicDO::getCategory, query.category())
                .like(StringUtils.isNotBlank(query.q()), TopicDO::getName, query.q())
                .orderByDesc(TopicDO::getHeat)
                .last("LIMIT " + ((page - 1) * size) + ", " + size);

        List<TopicVO> list = topicMapper.selectList(wrapper).stream()
                .map(t -> TopicConverter.toVO(t, postCountProvider.countOnlineByTopic(t.getId())))
                .toList();

        long total = topicMapper.selectCount(Wrappers.<TopicDO>lambdaQuery()
                .eq(TopicDO::getEnabled, 1)
                .eq(StringUtils.isNotBlank(query.type()), TopicDO::getType, query.type())
                .eq(StringUtils.isNotBlank(query.category()), TopicDO::getCategory, query.category())
                .like(StringUtils.isNotBlank(query.q()), TopicDO::getName, query.q()));
        return PageResult.of(total, page, size, list);
    }

    @Override
    public List<TopicBriefVO> findBriefs(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return topicMapper.selectList(Wrappers.<TopicDO>lambdaQuery()
                        .in(TopicDO::getId, ids)
                        .eq(TopicDO::getEnabled, 1))
                .stream().map(t -> TopicConverter.toBrief(t, null)).toList();
    }

    @Override
    public Set<Long> findEnabledPlaceIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return topicMapper.selectList(Wrappers.<TopicDO>lambdaQuery()
                        .in(TopicDO::getId, ids)
                        .eq(TopicDO::getEnabled, 1)
                        .eq(TopicDO::getType, "PLACE"))
                .stream().map(TopicDO::getId).collect(Collectors.toSet());
    }
}
