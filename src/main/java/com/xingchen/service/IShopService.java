package com.xingchen.service;

import com.xingchen.dto.Result;
import com.xingchen.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IShopService extends IService<Shop> {

  Result queryById(Long id);

  Result updateShop(Shop shop);

  Result queryShopByType(Integer typeId, Integer current, Double x, Double y);
}
