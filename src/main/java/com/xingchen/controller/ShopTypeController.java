package com.xingchen.controller;


import com.xingchen.dto.Result;
import com.xingchen.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;


@RestController
@RequestMapping("/shop-type")
public class ShopTypeController {
  @Resource
  private IShopTypeService typeService;

  @GetMapping("list")
  public Result queryTypeList() {
    return typeService.queryTypeList();
  }
}
