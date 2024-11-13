package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.Duration;

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

    @Override
    public Result getShopById(Long id) {
        // 1. 从 redis 查询商铺缓存
        String shopKey = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(shopKey);
        // 2. 命中真实数据，返回商铺信息
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        // 3. 命中空值，返回错误信息
        if ("".equals(shopJson)) {
            return Result.fail("商铺不存在！");
        }
        // 4. 未命中，则查询数据库
        Shop shop = getById(id);
        // 5. 若不存在，则返回错误信息
        if (shop == null) {
            // 将空值写入 Redis，并设置过期时间
            stringRedisTemplate.opsForValue().set(shopKey, "", Duration.ofMinutes(CACHE_NULL_TTL));
            // 返回错误信息
            return Result.fail("商铺不存在！");
        }
        // 6. 若存在，则将商铺数据写入 Redis，并设置过期时间
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop), Duration.ofMinutes(CACHE_SHOP_TTL));
        // 7. 返回商铺信息
        return Result.ok(shop);
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
}
