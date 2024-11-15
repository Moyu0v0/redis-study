package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.RedisData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;

/**
 * CacheClient
 *
 * @author sundae
 * @date 2024/11/14
 * @description 缓存工具类
 */
@Component
@Slf4j
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 定义一个固定大小的线程池用于缓存重建任务
     * 选择固定大小的线程池可以确保并发执行任务的数目被控制，
     * 这样可以防止过多的并发任务耗尽系统资源
     * 线程池的大小设定为10，表示可以同时执行10个任务，
     * 超过的任务将在队列中等待执行
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间。
     *
     * @param key   键
     * @param value 值
     * @param time  过期时间
     * @param unit  时间单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题。
     *
     * @param key   键
     * @param value 值
     * @param time  过期时间
     * @param unit  时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        // 设置逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入Redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 根据指定的key查询缓存，如果缓存中没有，则通过指定的方法从数据库中查询。
     * 并反序列化为指定实体类型，利用缓存空值的方式解决缓存穿透问题。
     *
     * @param keyPrefix  key前缀
     * @param id         id
     * @param type       实体类类型
     * @param dbFallback 数据库查询方法
     * @param time       过期时间
     * @param unit       时间单位
     * @return {@link R }
     */
    public <R, T> R queryWithPassThrough(String keyPrefix, T id, Class<R> type, Function<T, R> dbFallback, Long time, TimeUnit unit) {
        // 1. 从 redis 查询缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 命中真实数据，返回实体类信息
        if (StrUtil.isNotBlank(json)) {
            return JSONUtil.toBean(json, type);
        }
        // 3. 命中空值，返回空值
        if ("".equals(json)) {
            return null;
        }
        // 4. 未命中，则查询数据库
        R r = dbFallback.apply(id);
        // 5. 若不存在，则返回空值
        if (r == null) {
            // 将空值写入 Redis，并设置过期时间
            this.set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            // 返回空值
            return null;
        }
        // 6. 若存在，则将实体类数据写入 Redis，并设置过期时间
        this.set(key, r, time, unit);
        // 7. 返回实体类信息
        return r;
    }

    /**
     * 根据指定的key查询缓存，如果缓存已过期，则进行缓存重建。
     * 并反序列化为指定实体类型，利用逻辑过期解决缓存击穿问题。
     *
     * @param keyPrefix     key前缀
     * @param id            id
     * @param type          类型
     * @param lockKeyPrefix lock前缀
     * @param dbFallBack    数据库查询方法
     * @param time          过期时间
     * @param unit          时间单位
     * @return {@link R }
     */
    public <R, T> R queryWithLogicalExpire(String keyPrefix, T id, Class<R> type, String lockKeyPrefix, Function<T, R> dbFallBack, Long time, TimeUnit unit) {
        String key = keyPrefix + id;
        // 1. 从redis查询实体类缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否命中
        if (StrUtil.isBlank(json)) {
            // 3. 未命中，直接返回空值
            return null;
        }
        // 4. 命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5. 判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 6. 未过期，直接返回实体类信息
            return r;
        }
        // 7. 已过期，需要缓存重建
        // 8. 缓存重建
        // 8.1 获取互斥锁
        String lockKey = lockKeyPrefix + id;
        boolean isLock = tryLock(lockKey);
        // 8.2 判断是否获取锁成功
        if (isLock) {
            // double check 获取锁成功，再次检查redis缓存是否过期（有可能在这段时间别的线程已经重建缓存了）
            json = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isBlank(json)) {
                return null;
            }
            redisData = JSONUtil.toBean(json, RedisData.class);
            expireTime = redisData.getExpireTime();
            if (expireTime.isAfter(LocalDateTime.now())) {
                // 缓存未过期，直接返回店铺信息
                r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
                return r;
            }
            // 缓存已过期，重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 查数据库
                    R r1 = dbFallBack.apply(id);
                    // 写入Redis并设置逻辑过期时间
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 8. 返回过期的实体类信息
        return r;
    }

    /**
     * 获取锁。成功插入key的线程我们认为它就是获得到锁的线程。
     *
     * @param key 键
     * @return boolean 如果Redis中没有这个key，则插入成功，返回true；如果有这个key，则插入失败，返回false。
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10L, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     *
     * @param key 键
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}

