package com.roambupt.service.moderation.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.roambupt.service.moderation.repository.entity.SensitiveWordDO;
import org.apache.ibatis.annotations.Mapper;

/** 敏感词 Mapper。 */
@Mapper
public interface SensitiveWordMapper extends BaseMapper<SensitiveWordDO> {
}
