package com.xingchen.controller;


import com.xingchen.dto.Result;
import com.xingchen.service.IVoucherOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/voucher-order")
public class VoucherOrderController {

  @Autowired
  private IVoucherOrderService voucherOrderService;

  /**
   * 获取秒杀优惠券
   * @param voucherId 秒杀卷id
   * @return  秒杀结果
   */
  @PostMapping("seckill/{id}")
  public Result seckillVoucher(@PathVariable("id") Long voucherId) {
    return voucherOrderService.seckillVoucher(voucherId);
  }
}
