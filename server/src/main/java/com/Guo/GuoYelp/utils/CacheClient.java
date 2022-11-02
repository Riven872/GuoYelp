package com.Guo.GuoYelp.utils;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.Guo.GuoYelp.utils.RedisConstants.*;

/**
 * Redis缓存工具类
 */
@Slf4j
@Component
public class CacheClient {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //创建新的线程池，且池中有10个线程
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    /**
     * 将任意Java对象序列化成Json并存储在string类型的key中，并且可以设置TTL
     *
     * @param key   指定的缓存key
     * @param value 指定的缓存value，并序列化成Json存储
     * @param time  TTL
     * @param unit  TTL单位
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 将任意Java对象序列化成Json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
     *
     * @param key   指定的缓存key
     * @param value 指定的缓存value，并序列化成Json存储
     * @param time  逻辑过期时间
     * @param unit  逻辑过期时间单位
     */
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //传进来的value不一定有逻辑过期时间字段，因此可以封装到自定义的实体中
        RedisData redisData = new RedisData();
        redisData.setData(value);//封装数据
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));//设置逻辑过期时间

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
     *
     * @param keyPrefix  要查询的缓存key的前缀
     * @param id         根据id查询，类型为泛型
     * @param type       要求的返回值类型，类型为泛型
     * @param <T>        查询的id类型
     * @param <U>        返回值的类型
     * @param dbFallBack 缓存未命中，每个业务查询数据库的逻辑
     * @param time       TTL
     * @param unit       TTL单位
     * @return
     */
    public <T, U> T setWithPassThrough(String keyPrefix, U id, Class<T> type, Function<U, T> dbFallBack, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        //从Redis中查询信息
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isNotBlank(json)) {
            //存在则直接（需要将String类型的反序列化成对象）
            return JSONUtil.toBean(json, type);
        }
        //判断命中的是否为空值（解决缓存穿透）
        if (json != null) {
            return null;
        }
        //不存在则根据id查询数据库
        T apply = dbFallBack.apply(id);
        //数据库中不存在则直接将空值写入Redis
        if (apply == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //数据库中存在则写入Redis中
        this.set(key, apply, time, unit);
        //返回从数据库中查询到的商户信息
        return apply;
    }

    /**
     * 根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
     *
     * @param keyPrefix  要查询的缓存key的前缀
     * @param id         根据id查询，类型为泛型
     * @param type       要求的返回值类型，类型为泛型
     * @param <T>        查询的id类型
     * @param <U>        返回值的类型
     * @param dbFallBack 缓存未命中，每个业务查询数据库的逻辑
     * @param time       逻辑过期时间
     * @param unit       逻辑过期时间单位
     * @return
     */
    public <T, U> T setWithLogicalExpire(String keyPrefix, U id, Class<T> type, Function<U, T> dbFallBack, Long time, TimeUnit unit) {
        String key = keyPrefix + id;

        //从Redis中查询信息
        String json = stringRedisTemplate.opsForValue().get(key);
        //判断是否存在
        if (StrUtil.isBlank(json)) {
            //未命中直接返回空
            return null;
        }
        //命中则判断是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        T data = JSONUtil.toBean((JSONObject) redisData.getData(), type);//取出存放的数据
        LocalDateTime expireTime = redisData.getExpireTime();//取出逻辑过期时间
        if (expireTime.isAfter(LocalDateTime.now())) {
            //没有过期，直接返回数据
            return data;
        }
        //已过期，准备开始重建缓存，先获取互斥锁
        //判断是否获取锁成功
        if (tryLock(key)) {
            //获得锁之后，二次检测缓存是否过期，未过期则直接返回
            if (expireTime.isAfter(LocalDateTime.now())) {
                data = JSONUtil.toBean((JSONObject) redisData.getData(), type);
                return data;
            }
            //开启独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    //查询数据库
                    T apply = dbFallBack.apply(id);
                    //写入Redis
                    this.setWithLogicalExpire(key, apply, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    this.unLock(key);
                }
            });
        }

        //否则返回过期的信息（如果已过期，主线程继续返回过期的信息，副线程进行缓存的重建）
        return data;
    }

    /**
     * 上锁
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return isLock;
    }

    /**
     * 释放锁
     *
     * @param key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
