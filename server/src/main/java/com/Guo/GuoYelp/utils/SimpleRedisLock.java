package com.Guo.GuoYelp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * 基于Redis实现分布式锁
 */
public class SimpleRedisLock implements ILock {
    private StringRedisTemplate stringRedisTemplate;

    private String name;//业务名称，作为锁的名称

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    private static final String KEY_PREFIX = "lock:";//锁的统一前缀
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";//进程的唯一标识前缀

    /**
     * 尝试获取锁（非阻塞式）
     *
     * @param timeoutSec 锁持有的超时时间，过期后自动释放
     * @return true表示获取锁成功，false表示获取锁失败
     */
    @Override
    public boolean tryLock(long timeoutSec) {
        //获取当前线程的唯一标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + this.name, threadId, timeoutSec, TimeUnit.SECONDS);
        //返回结果，返回值是基本类型但是success是包装类，因此要注意拆装箱的空指针风险
        return Boolean.TRUE.equals(success);
    }

    /**
     * 释放锁
     */
    @Override
    public void unLock() {
        //获取当前线程的唯一标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取当前锁的唯一标识
        String lockId = stringRedisTemplate.opsForValue().get(KEY_PREFIX + this.name);
        //一致则释放锁
        if(threadId.equals(lockId)) {
            stringRedisTemplate.delete(KEY_PREFIX + this.name);
        }
    }
}
