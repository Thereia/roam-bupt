package com.roambupt.service.post.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.roambupt.service.post.repository.entity.PostTopicDO;
import org.apache.ibatis.annotations.Mapper;

/** 帖子-主题关联 Mapper。 */
@Mapper
public interface PostTopicMapper extends BaseMapper<PostTopicDO> {
}
