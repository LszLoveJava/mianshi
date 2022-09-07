package com.xingchen.service;

import com.xingchen.dto.Result;
import com.xingchen.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IShopTypeService extends IService<ShopType> {

  Result queryTypeList();
}
