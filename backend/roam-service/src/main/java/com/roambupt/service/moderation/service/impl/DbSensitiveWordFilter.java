package com.roambupt.service.moderation.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.roambupt.core.sensitive.SensitiveWordFilter;
import com.roambupt.service.moderation.repository.entity.SensitiveWordDO;
import com.roambupt.service.moderation.repository.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 敏感词过滤：词表加载到内存，变更时刷新。
 *
 * <p>P1 用包含判断；P2 词量上来再换 DFA。</p>
 */
@Service
public class DbSensitiveWordFilter implements SensitiveWordFilter {

    private static final Logger log = LoggerFactory.getLogger(DbSensitiveWordFilter.class);

    private final SensitiveWordMapper sensitiveWordMapper;
    private volatile Set<String> words = Set.of();

    public DbSensitiveWordFilter(SensitiveWordMapper sensitiveWordMapper) {
        this.sensitiveWordMapper = sensitiveWordMapper;
    }

    @PostConstruct
    public void load() {
        refresh();
    }

    /** 词表变更后调用。 */
    public synchronized void refresh() {
        try {
            words = sensitiveWordMapper.selectList(Wrappers.<SensitiveWordDO>lambdaQuery()
                            .eq(SensitiveWordDO::getEnabled, 1))
                    .stream().map(SensitiveWordDO::getWord).collect(Collectors.toUnmodifiableSet());
            log.info("sensitive.words.loaded count={}", words.size());
        } catch (Exception e) {
            log.warn("sensitive.words.load.failed reason={}", e.getMessage());
        }
    }

    @Override
    public boolean hit(String text) {
        if (text == null || text.isEmpty() || words.isEmpty()) {
            return false;
        }
        for (String word : words) {
            if (text.contains(word)) {
                return true;
            }
        }
        return false;
    }
}
