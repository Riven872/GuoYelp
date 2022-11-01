package com.Guo.GuoYelp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.Guo.GuoYelp.dto.Result;
import com.Guo.GuoYelp.entity.Shop;
import com.Guo.GuoYelp.mapper.ShopMapper;
import com.Guo.GuoYelp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.Guo.GuoYelp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询商户信息
     *
     * @param id 商户id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        //存储空对象解决缓存穿透
        //Shop shop = this.queryWithPassThrough(id);

        //互斥锁解决缓存击穿
        Shop shop = this.queryWithMutex(id);
        if (shop == null) {
            return Result.fail("商铺不存在");
        }


        return Result.ok(shop);
    }

    /**
     * 新增商铺信息
     *
     * @param shop 商铺数据
     * @return
     */
    @Override
    @Transactional
    public Result update(Shop shop) {
        //id不为空时才能更新数据库
        if (shop.getId() == null) {
            return Result.fail("店铺id不能为空");
        }
        //更新数据库
        this.updateById(shop);
        //删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + shop.getId());

        return Result.ok(shop.getId());
    }

    /**
     * 上锁
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean isLock = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return isLock;
    }

    /**
     * 释放锁
     *
     * @param key
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 存储空对象解决缓存穿透
     *
     * @param id
     * @return
     */
    private Shop queryWithPassThrough(Long id) {
        String key = CACHE_SHOP_KEY + id;

        //从Redis中查询商户信息（以返回String而不是Hash类型为例）
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断商户是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //存在则直接返回商户信息（需要将String类型的反序列化成对象）
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //判断命中的是否为空值
        if (shopJson != null) {
            //!=null则一定是空串
            return null;
        }
        //不存在则根据id查询数据库
        Shop shop = this.getById(id);
        //数据库中不存在则直接将空值写入Redis
        if (shop == null) {
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //数据库中存在则写入Redis中
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回从数据库中查询到的商户信息
        return shop;
    }

    /**
     * 互斥锁解决缓存击穿
     *
     * @param id
     * @return
     */
    private Shop queryWithMutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        Shop shop = null;

        //从Redis中查询商户信息（以返回String而不是Hash类型为例）
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //判断商户是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //存在则直接返回商户信息（需要将String类型的反序列化成对象）
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //判断命中的是否为空值
        if (shopJson != null) {
            //是空值则返回错误信息
            return null;
        }

        //获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        try {
            boolean isLock = tryLock(lockKey);
            //判断是否获取到锁
            if (!isLock) {
                //没有获取到则休眠一会并重试
                Thread.sleep(50);
                return this.queryWithMutex(id);
            }
            //获取到锁先查询Redis是否已经有缓存，如果有则直接返回（二次校验）
            if (StrUtil.isNotBlank(stringRedisTemplate.opsForValue().get(key))) {
                return JSONUtil.toBean(stringRedisTemplate.opsForValue().get(key), Shop.class);
            }
            //获取到锁则查询数据库
            shop = this.getById(id);
            //数据库中不存在则直接将空值写入Redis
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //数据库中存在则写入Redis中
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unLock(lockKey);
        }

        //释放互斥锁

        //返回从数据库中查询到的商户信息
        return shop;
    }
}
