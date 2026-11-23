package com.roambupt.service.notify.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.roambupt.service.notify.repository.entity.NotifyOutboxDO;
import org.apache.ibatis.annotations.Mapper;

/** 通知重投暂存 Mapper。 */
@Mapper
public interface NotifyOutboxMapper extends BaseMapper<NotifyOutboxDO> {
}
