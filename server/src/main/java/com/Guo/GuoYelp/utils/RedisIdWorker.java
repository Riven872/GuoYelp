package com.Guo.GuoYelp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Redis全局唯一id生成器
 */
@Component
public class RedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //自定义开始时间戳
    private static final long BEGIN_TIMESTAMP = 1640995200L;

    //序列号的位数
    private static final int COUNT_BITS = 32;

    /**
     * 生成唯一id
     * 符号位固定为0，占一位
     * 时间戳占31位
     * 序列号占32位
     *
     * @param keyPrefix 业务前缀
     * @return
     */
    public long nextId(String keyPrefix) {
        //生成时间戳
        long nowSecond = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);//当前时间秒数
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;//时间差值
        //生成序列号（使用Redis的自增长）
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));//获取当前日期，精确到天
        Long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);//自增长（类似于自动编号）
        //拼接返回（采用位运算，如果是直接相加的话返回的是String）
        return timeStamp << COUNT_BITS | count;//先向左移动32位，空出序列号位，再采用或运算将序列号拼接到后面
    }
}
