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

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

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
        // 2. 命中，则直接返回商铺信息
        if (StrUtil.isNotBlank(shopJson)) {
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        // 3. 未命中，则查询数据库
        Shop shop = getById(id);
        // 4. 若不存在，则返回错误信息
        if (shop == null) {
            return Result.fail("商铺不存在！");
        }
        // 5. 若存在，则将商铺数据写入 Redis
        stringRedisTemplate.opsForValue().set(shopKey, JSONUtil.toJsonStr(shop));
        // 6. 返回商铺信息
        return Result.ok(shop);
    }
}
