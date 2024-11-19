package com.hmdp.utils;

/**
 * Redis常量
 *
 * @author sundae
 * @date 2024/11/19
 */
public class RedisConstants {
    /**
     * 验证码前缀
     */
    public static final String LOGIN_CODE_KEY = "login:code:";

    /**
     * 验证码过期时间 默认为2分钟
     */
    public static final Long LOGIN_CODE_TTL = 2L;

    /**
     * 登录用户token前缀
     */
    public static final String LOGIN_USER_KEY = "login:token:";

    /**
     * token过期时间 默认为30分钟
     */
    public static final Long LOGIN_USER_TTL = 30L;

    /**
     * 空对象缓存过期时间 默认为2分钟
     */
    public static final Long CACHE_NULL_TTL = 2L;

    /**
     * 商铺信息过期时间 默认为30分钟
     */
    public static final Long CACHE_SHOP_TTL = 30L;

    /**
     * 商铺信息前缀
     */
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    /**
     * 缓存重建互斥锁前缀
     */
    public static final String LOCK_SHOP_KEY = "lock:shop:";

    /**
     * 缓存重建互斥锁过期时间 默认为30秒
     */
    public static final Long LOCK_SHOP_TTL = 10L;

    /**
     * 秒杀优惠券库存前缀
     */
    public static final String SECKILL_STOCK_KEY = "seckill:stock:";

    /**
     * 优惠券订单前缀
     */
    public static final String VOUCHER_ORDER_KEY = "order";

    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
