package com.hmdp;

import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
class ShopServiceImplTest {
    @Resource
    private ShopServiceImpl shopService;

    @Test
    void saveShop2Redis() {
        // 提前缓存数据
        shopService.saveShop2Redis(1L, 10L);
    }
}