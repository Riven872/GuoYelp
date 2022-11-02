package com.Guo.GuoYelp.utils;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设置逻辑过期时间的实体
 */
@Data
public class RedisData {
    private LocalDateTime expireTime;//逻辑过期时间

    private Object data;//存数据的实体
}
