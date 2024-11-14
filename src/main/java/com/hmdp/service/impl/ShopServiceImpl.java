package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.RedisData;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;

    /**
     * 定义一个固定大小的线程池用于缓存重建任务
     * 选择固定大小的线程池可以确保并发执行任务的数目被控制，
     * 这样可以防止过多的并发任务耗尽系统资源
     * 线程池的大小设定为10，表示可以同时执行10个任务，
     * 超过的任务将在队列中等待执行
     */
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result getShopById(Long id) {
        // 解决缓存穿透
        // Shop shop = queryWithPassThrough(id);

        // 利用逻辑过期解决缓存击穿
        Shop shop = queryWithLogicalExpire(id);

        return shop == null ? Result.fail("商铺不存在！") : Result.ok(shop);
    }

    /**
     * 查询商铺信息（使用逻辑过期解决缓存击穿）
     *
     * @param id 商铺id
     * @return {@link Shop }
     */
    public Shop queryWithLogicalExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1. 从redis查询商铺缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 2. 判断是否命中
        if (StrUtil.isBlank(json)) {
            // 3.未命中，直接返回空值
            return null;
        }
        // 4.命中，需要先把json反序列化为对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        // 5.判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 6. 未过期，直接返回店铺信息
            return shop;
        }
        // 7. 已过期，需要缓存重建
        // 8. 缓存重建
        // 8.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
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
                shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
                return shop;
            }
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 重建缓存
                    this.saveShop2Redis(id, 20L);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    // 释放锁
                    unlock(lockKey);
                }
            });
        }
        // 8. 返回过期的商铺信息
        return shop;
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

    @Override
    @Transactional // 删除操作出现异常时将事务回滚
    public Result updateShop(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺 id 不能为空！");
        }
        // 1. 修改数据库
        updateById(shop);
        // 2. 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    /**
     * 缓存预热，提前将数据存到Redis中。
     *
     * @param id            商铺id
     * @param expireSeconds 过期时间
     */
    public void saveShop2Redis(Long id, Long expireSeconds) {
        // 1. 查询店铺数据
        Shop shop = getById(id);
        // 2. 封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        // 3. 写入Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 释放锁
     *
     * @param key 键
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 查询商铺信息（解决缓存穿透）
     *
     * @param id 商铺id
     * @return {@link Shop }
     */
    public Shop queryWithPassThrough(Long id) {
        // 1. 从 redis 查询商铺缓存
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2. 命中真实数据，返回商铺信息
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        // 3. 命中空值，返回错误信息
        if ("".equals(shopJson)) {
            return null;
        }
        // 4. 未命中，则查询数据库
        Shop shop = getById(id);
        // 5. 若不存在，则返回错误信息
        if (shop == null) {
            // 将空值写入 Redis，并设置过期时间
            stringRedisTemplate.opsForValue().set(shopKey, "", Duration.ofMinutes(CACHE_NULL_TTL));
            // 返回错误信息
            return null;
        }
        // 6. 若存在，则将商铺数据写入 Redis，并设置过期时间
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), Duration.ofMinutes(CACHE_SHOP_TTL));
        // 7. 返回商铺信息
        return shop;
    }
}
