package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RedissonConfig
 *
 * @author sundae
 * @date 2024/11/18
 * @description Redission配置类
 */
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        // 配置
        Config config = new Config();
        // 添加Redis地址，这里添加的单点的地址，也可以用config.useClusterServers()添加集群地址
        config.useSingleServer().setAddress("redis://127.0.0.1:6379")
                .setPassword("123321");
        // 创建RedissonClient对象
        return Redisson.create(config);
    }
}

