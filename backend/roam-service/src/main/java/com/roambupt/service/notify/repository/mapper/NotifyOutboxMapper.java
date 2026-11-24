package com.roambupt.service.notify.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.roambupt.service.notify.repository.entity.NotifyOutboxDO;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/** 通知重投暂存 Mapper。 */
@Mapper
public interface NotifyOutboxMapper extends BaseMapper<NotifyOutboxDO> {

    /** 清理已经投递成功的历史行，避免 outbox 无限增长。 */
    @Delete("DELETE FROM notify_outbox WHERE status = 'SENT' AND updated_time < #{before}")
    int deleteSentBefore(@Param("before") LocalDateTime before);
}
