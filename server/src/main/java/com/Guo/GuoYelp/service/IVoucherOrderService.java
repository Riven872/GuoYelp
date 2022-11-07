package com.Guo.GuoYelp.service;

import com.Guo.GuoYelp.dto.Result;
import com.Guo.GuoYelp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 * 服务类
 * </p>
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);
}
