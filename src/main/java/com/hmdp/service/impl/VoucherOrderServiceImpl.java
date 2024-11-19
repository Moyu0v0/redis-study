package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

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

    // 异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();
    private final BlockingDeque<VoucherOrder> orderTasks = new LinkedBlockingDeque<>(1024 * 1024);
    private IVoucherOrderService proxy;

    // 在类初始化之后执行，因为当这个类初始化好了之后，随时都是有可能要执行的
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

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
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userId);
        voucherOrder.setVoucherId(voucherId);
        orderTasks.add(voucherOrder);
        // 4. 获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        // 5. 返回订单id
        return Result.ok(orderId);
    }


    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Transactional
    @Override
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        Long voucherId = voucherOrder.getVoucherId();
        Long userId = voucherOrder.getUserId();
        // 一人一单判断
        int count = query().eq("voucher_id", voucherId)
                .eq("user_id", userId)
                .count();
        if (count > 0) {
            log.error("不允许重复下单！");
            return;
        }
        // 扣减库存
        boolean success = iSeckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("库存不足！");
            return;
        }
        save(voucherOrder);
    }

    // 用于线程池处理的任务
    // 当初始化完毕后，就会去从对列中去拿信息
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }

        private void handleVoucherOrder(VoucherOrder voucherOrder) {
            // 获取用户
            Long userId = voucherOrder.getUserId();
            // 注意：由于是spring的事务是放在threadLocal中，此时的是多线程，事务会失效
            proxy.createVoucherOrder(voucherOrder);
        }
    }
}
