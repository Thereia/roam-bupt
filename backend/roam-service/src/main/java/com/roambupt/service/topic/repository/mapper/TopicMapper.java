package com.roambupt.service.topic.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.roambupt.service.topic.repository.entity.TopicDO;
import org.apache.ibatis.annotations.Mapper;

/** 主题 Mapper。 */
@Mapper
public interface TopicMapper extends BaseMapper<TopicDO> {
}
