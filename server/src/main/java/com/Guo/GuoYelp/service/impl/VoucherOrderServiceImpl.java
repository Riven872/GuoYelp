package com.Guo.GuoYelp.service.impl;

import com.Guo.GuoYelp.dto.Result;
import com.Guo.GuoYelp.dto.UserDTO;
import com.Guo.GuoYelp.entity.SeckillVoucher;
import com.Guo.GuoYelp.entity.VoucherOrder;
import com.Guo.GuoYelp.mapper.VoucherOrderMapper;
import com.Guo.GuoYelp.service.ISeckillVoucherService;
import com.Guo.GuoYelp.service.IVoucherOrderService;
import com.Guo.GuoYelp.utils.RedisIdWorker;
import com.Guo.GuoYelp.utils.SimpleRedisLock;
import com.Guo.GuoYelp.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private IVoucherOrderService voucherOrderService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;

    /**
     * 抢购秒杀券
     *
     * @param voucherId 优惠券id
     * @return
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        //查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        //判断秒杀是否开始或已经结束
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //未开始
            return Result.fail("秒杀尚未开始");
        }
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            //已经结束
            return Result.fail("秒杀已经结束");
        }
        //判断库存是否充足
        if (voucher.getStock() < 1) {
            //库存不足
            return Result.fail("库存不足");
        }
        Long userId = UserHolder.getUser().getId();

        //region 基于Redisson实现单体Redis的分布式简单锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);//创建锁对象
        boolean isLock = lock.tryLock();//获取锁，无参默认不等待，失败不重试，超时时间30s
        if (!isLock) {
            return Result.fail("不允许重复下单");//获取锁失败，直接返回
        }
        try {
            return voucherOrderService.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();//释放锁
        }
        //endregion

        //region 基于Redis的分布式锁解决分布式或集群模式下的一人一单问题
        ////创建锁对象
        //SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
        ////获取锁
        //boolean isLock = lock.tryLock(1000);
        ////判断是否获取锁成功
        //if (!isLock) {
        //    //获取失败，返回错误（可以根据业务场景选择返回错误或重试）
        //    return Result.fail("不允许重复下单");
        //}
        ////获取成功，创建订单
        //try {
        //    return voucherOrderService.createVoucherOrder(voucherId);
        //}
        ////无论是否异常，一定要释放锁
        //finally {
        //    lock.unLock();
        //}
        //endregion

        //region 悲观锁解决单体项目下的一人一单问题
        //synchronized (userId.toString().intern()) {
        //    //获取事务的代理对象
        //    //IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
        //    //return proxy.createVoucherOrder(voucherId);
        //
        //    return voucherOrderService.createVoucherOrder(voucherId);
        //}
        //endregion
    }

    /**
     * 一人一单创建订单
     *
     * @param voucherId
     * @return
     */
    @Transactional
    public Result createVoucherOrder(Long voucherId) {
        //一人一单
        long userId = UserHolder.getUser().getId();
        //查询订单
        int count = this.query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        //判断用户和券已经存在订单
        if (count > 0) {
            //存在订单
            return Result.fail("该用户已经购买过一次");
        }
        //扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)//乐观锁解决超卖问题
                .update();
        //扣减失败
        if (!success) {
            return Result.fail("库存不足");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);//订单id
        voucherOrder.setUserId(userId);//用户id
        voucherOrder.setVoucherId(voucherId);//代金券id
        this.save(voucherOrder);
        //返回订单id
        return Result.ok(orderId);
    }
}
