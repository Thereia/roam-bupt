package com.roambupt.service.notify.repository.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.roambupt.service.notify.repository.entity.NotificationDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 通知 Mapper。 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationDO> {

    @Select("SELECT COUNT(*) FROM notification WHERE user_id = #{uid} AND read_flag = 0")
    long countUnread(@Param("uid") long uid);
}
