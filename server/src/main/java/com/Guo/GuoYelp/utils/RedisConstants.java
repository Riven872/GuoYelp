package com.Guo.GuoYelp.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";//用户登录时，redis的key值业务前缀
    public static final Long LOGIN_CODE_TTL = 2L;//用户登录时，验证码k-v过期时间
    public static final String LOGIN_USER_KEY = "login:token:";//用户登录时，Redis保存用户信息的前缀
    public static final Long LOGIN_USER_TTL = 36000L;//用户登录时，user信息k-v过期时间

    public static final Long CACHE_NULL_TTL = 2L;//存入空值的TTL

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:shop:";

    public static final String LOCK_SHOP_KEY = "lock:shop:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String BLOG_LIKED_KEY = "blog:liked:";
    public static final String FEED_KEY = "feed:";
    public static final String SHOP_GEO_KEY = "shop:geo:";
    public static final String USER_SIGN_KEY = "sign:";
}
