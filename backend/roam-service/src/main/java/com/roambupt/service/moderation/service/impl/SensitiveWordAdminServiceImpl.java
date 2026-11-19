package com.roambupt.service.moderation.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.api.model.enums.StatusEnum;
import com.roambupt.api.model.exception.BusinessException;
import com.roambupt.api.model.vo.PageResult;
import com.roambupt.api.model.vo.SensitiveWordVO;
import com.roambupt.core.sensitive.SensitiveWordFilter;
import com.roambupt.service.moderation.repository.entity.SensitiveWordDO;
import com.roambupt.service.moderation.repository.mapper.SensitiveWordMapper;
import com.roambupt.service.moderation.service.SensitiveWordAdminService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** 敏感词维护实现。变更后刷新内存词表。 */
@Service
public class SensitiveWordAdminServiceImpl implements SensitiveWordAdminService {

    private static final int MAX_PAGE_SIZE = 50;

    private final SensitiveWordMapper sensitiveWordMapper;
    private final SensitiveWordFilter sensitiveWordFilter;

    public SensitiveWordAdminServiceImpl(SensitiveWordMapper sensitiveWordMapper,
                                         SensitiveWordFilter sensitiveWordFilter) {
        this.sensitiveWordMapper = sensitiveWordMapper;
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    @Override
    public PageResult<SensitiveWordVO> page(int page, int pageSize) {
        int pageNo = Math.max(1, page);
        int size = Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE);
        List<SensitiveWordVO> list = sensitiveWordMapper.selectList(Wrappers.<SensitiveWordDO>lambdaQuery()
                        .orderByDesc(SensitiveWordDO::getId)
                        .last("LIMIT " + ((pageNo - 1) * size) + ", " + size))
                .stream()
                .map(row -> new SensitiveWordVO(row.getId(), row.getWord(),
                        row.getEnabled() != null && row.getEnabled() == 1))
                .toList();
        long total = sensitiveWordMapper.selectCount(null);
        return PageResult.of(total, pageNo, size, list);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(String word) {
        String normalized = StringUtils.trimToEmpty(word);
        if (normalized.isEmpty()) {
            throw new BusinessException(StatusEnum.PARAM_ERROR, "敏感词不能为空");
        }
        SensitiveWordDO entity = new SensitiveWordDO();
        entity.setWord(normalized);
        entity.setEnabled(1);
        entity.setCreatedTime(LocalDateTime.now());
        try {
            sensitiveWordMapper.insert(entity);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(StatusEnum.CONFLICT, "敏感词已存在");
        }
        refresh();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(long id) {
        sensitiveWordMapper.deleteById(id);
        refresh();
    }

    private void refresh() {
        if (sensitiveWordFilter instanceof com.roambupt.service.moderation.service.impl.DbSensitiveWordFilter filter) {
            filter.refresh();
        }
    }
}
