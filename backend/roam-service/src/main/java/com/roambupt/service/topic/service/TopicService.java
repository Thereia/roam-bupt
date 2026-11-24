package com.roambupt.service.topic.service;

import com.roambupt.api.model.dto.TopicQuery;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.TopicBriefVO;
import com.roambupt.api.model.vo.CategoryCountVO;
import com.roambupt.api.model.vo.TopicVO;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** 主题读服务。其他域只通过它拿主题信息，不碰 topic 表。 */
public interface TopicService {

    TopicVO detail(long topicId);

    PageResult<TopicVO> list(TopicQuery query);

    List<TopicBriefVO> findBriefs(Collection<Long> ids);

    /** 过滤出「存在、可见、且是地点」的主题 id。 */
    Set<Long> findEnabledPlaceIds(Collection<Long> ids);

    /** 已启用的主题 id（地点或分类都算），用于校验挂载合法性。 */
    Set<Long> findEnabledIds(Collection<Long> ids);

    /** 按分类统计可见地点数量，供首页左栏使用。 */
    List<CategoryCountVO> stats();

    /** 是否是已启用、可评分的地点（分类不可评分）。 */
    boolean isEnabledPlace(long topicId);

    /** 地点对应的路网入口节点；不是已启用的地点返回 null。 */
    Long findNodeId(long topicId);

    /** 发布页主题联想：按名字模糊匹配，最多 limit 条。 */
    List<TopicBriefVO> suggest(String keyword, int limit);
}
