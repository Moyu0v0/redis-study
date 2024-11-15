package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * RedisIdWorker
 *
 * @author sundae
 * @date 2024/11/15
 * @description 全局唯一ID生成器
 */
@Component
public class RedisIdWorker {
    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTMAP = 1731663120L;

    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 生成全局唯一ID
     *
     * @param keyPrefix key前缀
     * @return {@link Long }
     */
    public Long nextId(String keyPrefix) {
        // 1. 生成31位时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestmap = nowSecond - BEGIN_TIMESTMAP;
        // 2. 生成32位序列号
        // 2.1 获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.1 利用Redis获取自增长序列号
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);
        // 3. 拼接全局唯一ID（利用或运算实现相加）
        return timestmap << COUNT_BITS | count;
    }

}

