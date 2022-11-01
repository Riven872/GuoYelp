package com.Guo.GuoYelp.service;

import com.Guo.GuoYelp.dto.Result;
import com.Guo.GuoYelp.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *

 */
public interface IShopTypeService extends IService<ShopType> {

    Result queryTypeList();
}
