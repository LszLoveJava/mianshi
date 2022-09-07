package com.xingchen.service;

import com.xingchen.dto.Result;
import com.xingchen.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IVoucherOrderService extends IService<VoucherOrder> {

  Result seckillVoucher(Long voucherId);

  Result createVoucherOrder(Long voucherId);

  void createVoucherOrder(VoucherOrder voucherOrder);
}
