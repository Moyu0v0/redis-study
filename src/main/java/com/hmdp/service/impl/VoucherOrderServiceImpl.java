package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Collections;

import static com.hmdp.utils.RedisConstants.VOUCHER_ORDER_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService iSeckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result seckillVoucher(Long voucherId) {
        // 1. 获取用户
        Long userId = UserHolder.getUser().getId();
        // 2. 执行lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        // 3. 判断结果是否为0
        int r = 0;
        if (result != null) {
            r = result.intValue();
        }
        if (r != 0) {
            // 3.1 不为0，代表没有购买资格
            return r == 1 ? Result.fail("库存不足！") : Result.fail("不允许重复下单！");
        }
        // 3.2 为0，有购买资格，把下单信息保存到阻塞队列
        Long orderId = redisIdWorker.nextId("order");
        // 4. 返回订单id
        return Result.ok(orderId);
    }

    @Override
    @Transactional
    public Result createVoucherOrder(Long voucherId, Long userId) {
        // 5. 一人一单判断
        int count = query().eq("voucher_id", voucherId)
                .eq("user_id", userId)
                .count();
        if (count > 0) {
            return Result.fail("不允许重复下单！");
        }
        // 6. 扣减库存
        boolean success = iSeckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("库存不足！");
        }
        // 7. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1 订单id
        Long orderId = redisIdWorker.nextId(VOUCHER_ORDER_KEY);
        voucherOrder.setId(orderId);
        // 7.2 用户id
        voucherOrder.setUserId(userId);
        // 7.3 代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);
        // 8. 返回订单id
        return Result.ok(orderId);
    }
}
