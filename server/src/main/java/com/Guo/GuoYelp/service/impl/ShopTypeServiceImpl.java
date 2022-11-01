package com.Guo.GuoYelp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.Guo.GuoYelp.dto.Result;
import com.Guo.GuoYelp.entity.ShopType;
import com.Guo.GuoYelp.mapper.ShopTypeMapper;
import com.Guo.GuoYelp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.Guo.GuoYelp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.Guo.GuoYelp.utils.RedisConstants.CACHE_SHOP_TTL;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 查询店铺类型列表
     *
     * @return
     */
    @Override
    public Result queryTypeList() {
        //从Redis中查询店铺类型列表信息
        String shopList = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY);
        //判断列表是否存在
        if (StrUtil.isNotBlank(shopList)) {
            //存在则直接返回（因为是列表，需要转成List类型）
            List<ShopType> shopTypes = JSONUtil.toList(shopList, ShopType.class);
            return Result.ok(shopTypes);
        }
        //不存在则查询数据库
        List<ShopType> shopTypes = this.query().orderByAsc("sort").list();
        //数据库中不存在则直接返回错误
        if (shopTypes.isEmpty()) {
            return Result.fail("店铺类型不存在");
        }
        //数据库中存在则写入Redis中
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY, JSONUtil.toJsonStr(shopTypes),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //返回从数据库中查询到的数据
        return Result.ok(shopTypes);
    }
}
