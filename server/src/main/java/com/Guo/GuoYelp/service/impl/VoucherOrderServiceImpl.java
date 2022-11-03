package com.Guo.GuoYelp.service.impl;

import com.Guo.GuoYelp.dto.Result;
import com.Guo.GuoYelp.dto.UserDTO;
import com.Guo.GuoYelp.entity.SeckillVoucher;
import com.Guo.GuoYelp.entity.VoucherOrder;
import com.Guo.GuoYelp.mapper.VoucherOrderMapper;
import com.Guo.GuoYelp.service.ISeckillVoucherService;
import com.Guo.GuoYelp.service.IVoucherOrderService;
import com.Guo.GuoYelp.utils.RedisIdWorker;
import com.Guo.GuoYelp.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

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
        synchronized (userId.toString().intern()) {
            //获取事务的代理对象
            //IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            //return proxy.createVoucherOrder(voucherId);
            return voucherOrderService.createVoucherOrder(voucherId);
        }
    }

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
