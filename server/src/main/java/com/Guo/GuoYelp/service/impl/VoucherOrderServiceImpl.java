package com.Guo.GuoYelp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
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
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

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

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;//提前将脚本加载进来，防止运行时没必要的IO流操作

    static {
        //使用静态代码块进行脚本的初始化
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));//设置脚本位置
        SECKILL_SCRIPT.setResultType(Long.class);//设置返回值
    }

    private BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);//创建阻塞队列，若队列中没有元素，则阻塞

    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();//线程池

    //region 阻塞队列处理方法
    //新线程的动作，不断从线程池中获取订单
    //private class VoucherOrderHandler implements Runnable {
    //    @Override
    //    public void run() {
    //        while (true) {
    //            try {
    //                //获取队列中的订单信息，如果没有元素则阻塞
    //                VoucherOrder voucherOrder = orderTasks.take();
    //                //创建订单
    //                handleVoucherOrder(voucherOrder);
    //            } catch (InterruptedException e) {
    //                e.printStackTrace();
    //            }
    //        }
    //    }
    //}
    //endregion

    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    //获取消息队列中的订单信息 XREAD GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS stream.orders >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),//c1消费者，g1消费者组
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds((2))),//读一条，如果没有消息则阻塞读取2s
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())//从最新的开始
                    );
                    //如果获取失败则说明没有消息，继续下一次循环
                    if (list == null || list.isEmpty()) {
                        continue;
                    }
                    //解析消息中的订单
                    MapRecord<String, Object, Object> record = list.get(0);//因为是COUNT 1 因此确定的是get(0)
                    Map<Object, Object> values = record.getValue();//返回值为键值对形式，且值为需要的返回值，因此只拿value
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), false);//获得订单实体
                    //如果获取成功则可以下单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", record.getId());//record.getId()为消息的id
                } catch (Exception e) {
                    log.error("处理订单异常消息：" + e);
                    //处理pending-list中已处理但未ack的消息
                    handlePendingList();
                }
            }
        }

        //处理Pending-list中的消息
        private void handlePendingList() {
            while (true) {
                try {
                    //获取pending-list中的订单信息 XREAD GROUP g1 c1 COUNT 1 STREAMS stream.orders 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),//c1消费者，g1消费者组
                            StreamReadOptions.empty().count(1),//读一条，阻塞读取2s
                            StreamOffset.create("stream.orders", ReadOffset.from("0"))//从第一条开始
                    );
                    //如果获取失败则说明pending-list没有消息，跳出循环
                    if (list == null || list.isEmpty()) {
                        break;
                    }
                    //解析pending-list中的订单
                    MapRecord<String, Object, Object> record = list.get(0);//因为是COUNT 1 因此确定的是get(0)
                    Map<Object, Object> values = record.getValue();//返回值为键值对形式，且值为需要的返回值，因此只拿value
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(values, new VoucherOrder(), false);//获得订单实体
                    //如果获取成功则可以下单
                    handleVoucherOrder(voucherOrder);
                    //ACK确认
                    stringRedisTemplate.opsForStream().acknowledge("stream.orders", "g1", record.getId());//record.getId()为消息的id
                } catch (Exception e) {
                    log.error("处理pending-list订单异常消息：" + e);//不用递归，有while(true)的原因下次还会获取到处理异常的消息
                }
            }
        }
    }

    /**
     * 该类初始化完毕之后执行，新线程从队列中开始取
     */
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    /**
     * 开启的新线程进行数据库的读写操作（创建订单）
     *
     * @param voucherOrder
     */
    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //region 严格来说不用获取锁对象，因为在Lua脚本中已经用Redis去判断了
        ////获取用户（因为是新线程，因此不能用UserHolder去取）
        //Long userId = voucherOrder.getUserId();
        ////创建锁对象
        //RLock lock = redissonClient.getLock("lock:oder:" + userId);
        ////获取锁
        //boolean isLock = lock.tryLock();
        ////判断锁是否获取成功
        //if (!isLock) {
        //    return;
        //}
        //try {
        //    voucherOrderService.createVoucherOrder(voucherOrder.getVoucherId());
        //} finally {
        //    lock.unlock();
        //}
        //endregion

        //不获取锁对象，直接调用业务
        voucherOrderService.createVoucherOrder(voucherOrder);
    }

    /**
     * 优化并发性能抢购秒杀券，并采用Redis的Stream作为消息队列
     *
     * @param voucherId
     * @return
     */
    public Result seckillVoucher(Long voucherId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //获取订单id
        long orderId = redisIdWorker.nextId("order");
        //执行Lua脚本
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString(),
                String.valueOf(orderId)//发送订单信息到消息队列
        );
        //判断脚本执行结果
        int res = result.intValue();
        //返回结果不为0，则没有购买资格
        if (res != 0) {
            return Result.fail(res == 1 ? "库存不足" : "不能重复下单");
        }
        //否则返回订单id
        return Result.ok(orderId);
    }

    //region 优化并发性能抢购秒杀券功能但采用的是阻塞队列
    ///**
    // * 优化并发性能抢购秒杀券功能但采用的是阻塞队列
    // * @param voucherId
    // * @return
    // */
    //public Result seckillVoucher(Long voucherId) {
    //    //获取当前用户
    //    Long userId = UserHolder.getUser().getId();
    //    //执行Lua脚本
    //    Long result = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
    //    int res = result.intValue();
    //    //判断不为0
    //    if (res != 0) {
    //        return Result.fail(res == 1 ? "库存不足" : "不能重复下单");
    //    }
    //    //为0，则有购买资格，把下单信息封装到阻塞队列
    //    long orderId = redisIdWorker.nextId("order");
    //    VoucherOrder voucherOrder = new VoucherOrder();
    //    voucherOrder.setId(orderId);//订单id
    //    voucherOrder.setUserId(userId);//用户id
    //    voucherOrder.setVoucherId(voucherId);//代金券id
    //    //放入阻塞队列
    //    orderTasks.add(voucherOrder);
    //    //返回订单id
    //    return Result.ok(orderId);
    //}
    //endregion

    //region 抢购秒杀券未考虑并发性能问题
    ///**
    // * 抢购秒杀券
    // *
    // * @param voucherId 优惠券id
    // * @return
    // */
    //@Override
    //public Result seckillVoucher(Long voucherId) {
    //    //查询优惠券
    //    SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
    //    //判断秒杀是否开始或已经结束
    //    if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
    //        //未开始
    //        return Result.fail("秒杀尚未开始");
    //    }
    //    if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
    //        //已经结束
    //        return Result.fail("秒杀已经结束");
    //    }
    //    //判断库存是否充足
    //    if (voucher.getStock() < 1) {
    //        //库存不足
    //        return Result.fail("库存不足");
    //    }
    //    Long userId = UserHolder.getUser().getId();
    //
    //    //region 基于Redisson实现单体Redis的分布式简单锁
    //    RLock lock = redissonClient.getLock("lock:order:" + userId);//创建锁对象
    //    boolean isLock = lock.tryLock();//获取锁，无参默认不等待，失败不重试，超时时间30s
    //    if (!isLock) {
    //        return Result.fail("不允许重复下单");//获取锁失败，直接返回
    //    }
    //    try {
    //        return voucherOrderService.createVoucherOrder(voucherId);
    //    } finally {
    //        lock.unlock();//释放锁
    //    }
    //    //endregion
    //    //region 基于Redis的分布式锁解决分布式或集群模式下的一人一单问题
    //    ////创建锁对象
    //    //SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order:" + userId);
    //    ////获取锁
    //    //boolean isLock = lock.tryLock(1000);
    //    ////判断是否获取锁成功
    //    //if (!isLock) {
    //    //    //获取失败，返回错误（可以根据业务场景选择返回错误或重试）
    //    //    return Result.fail("不允许重复下单");
    //    //}
    //    ////获取成功，创建订单
    //    //try {
    //    //    return voucherOrderService.createVoucherOrder(voucherId);
    //    //}
    //    ////无论是否异常，一定要释放锁
    //    //finally {
    //    //    lock.unLock();
    //    //}
    //    //endregion
    //
    //    //region 悲观锁解决单体项目下的一人一单问题
    //    //synchronized (userId.toString().intern()) {
    //    //    //获取事务的代理对象
    //    //    //IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
    //    //    //return proxy.createVoucherOrder(voucherId);
    //    //
    //    //    return voucherOrderService.createVoucherOrder(voucherId);
    //    //}
    //    //endregion
    //}
    //endregion

    /**
     * 一人一单创建订单
     *
     * @param voucherOrder
     * @return
     */
    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //region 判断一人一单、库存操作已经由脚本完成，此处只需完成下单业务即可
        ////一人一单
        //long userId = UserHolder.getUser().getId();
        ////查询订单
        //int count = this.query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        ////判断用户和券已经存在订单
        //if (count > 0) {
        //    //存在订单
        //    return Result.fail("该用户已经购买过一次");
        //}
        ////扣减库存
        //boolean success = seckillVoucherService.update()
        //        .setSql("stock = stock - 1")
        //        .eq("voucher_id", voucherId)
        //        .gt("stock", 0)//乐观锁解决超卖问题
        //        .update();
        ////扣减失败
        //if (!success) {
        //    return Result.fail("库存不足");
        //}
        ////创建订单
        //VoucherOrder voucherOrder = new VoucherOrder();
        //long orderId = redisIdWorker.nextId("order");
        //voucherOrder.setId(orderId);//订单id
        //voucherOrder.setUserId(userId);//用户id
        //voucherOrder.setVoucherId(voucherId);//代金券id
        //this.save(voucherOrder);
        ////返回订单id
        //return Result.ok(orderId);
        //endregion

        this.save(voucherOrder);
    }
}
