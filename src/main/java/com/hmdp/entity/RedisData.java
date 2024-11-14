package com.hmdp.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * RedisData
 *
 * @author sundae
 * @date 2024/11/14
 * @description
 */
@Data
public class RedisData implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 数据
     */
    private Object data;

    /**
     * 到期时间
     */
    private LocalDateTime expireTime;
}

