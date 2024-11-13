package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据 id 获取商铺信息
     *
     * @param id id
     * @return {@link Result }
     */
    Result getShopById(Long id);

    /**
     * 根据 id 更新商铺信息
     *
     * @param shop 商店
     * @return {@link Result }
     */
    Result updateShop(Shop shop);
}
