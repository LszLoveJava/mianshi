package com.xingchen.service;

import com.xingchen.dto.Result;
import com.xingchen.entity.Voucher;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IVoucherService extends IService<Voucher> {

  Result queryVoucherOfShop(Long shopId);

  void addSeckillVoucher(Voucher voucher);
}
