package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {
    /**
     * 秒杀下单优
     *
     * @param voucherId 券id
     * @return {@link Result }
     */
    Result seckillVoucher(Long voucherId);

    /**
     * 在MySQL中创建秒杀优惠券订单
     *
     * @param voucherOrder 优惠券订单
     */
    void createVoucherOrder(VoucherOrder voucherOrder);
}
