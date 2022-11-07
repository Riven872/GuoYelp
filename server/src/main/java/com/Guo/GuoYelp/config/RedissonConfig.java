package com.Guo.GuoYelp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 配置Redisson
 */
@Configuration
public class RedissonConfig {
    @Bean
    public RedissonClient redissonClient(){
        //配置
        Config config = new Config();
        //添加Redis地址，这里添加了单点的地址，也可以使用config.userClusterServers()添加集群地址
        config.useSingleServer().setAddress("redis://192.168.63.100:6379").setPassword("123");
        //创建Redisson对象
        return Redisson.create(config);
    }
}
