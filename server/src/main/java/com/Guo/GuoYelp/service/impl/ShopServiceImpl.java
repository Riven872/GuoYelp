package com.Guo.GuoYelp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.Guo.GuoYelp.dto.Result;
import com.Guo.GuoYelp.entity.Shop;
import com.Guo.GuoYelp.mapper.ShopMapper;
import com.Guo.GuoYelp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import static com.Guo.GuoYelp.utils.RedisConstants.CACHE_SHOP_KEY;

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
        //从Redis中查询商户信息（以返回String而不是Hash类型为例）
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //判断商户是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            //存在则直接返回商户信息（需要将String类型的反序列化成对象）
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }
        //不存在则根据id查询数据库
        Shop shop = this.getById(id);
        //数据库中不存在则直接返回错误
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        //数据库中存在则写入Redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));
        //返回从数据库中查询到的商户信息
        return Result.ok(shop);
    }
}
