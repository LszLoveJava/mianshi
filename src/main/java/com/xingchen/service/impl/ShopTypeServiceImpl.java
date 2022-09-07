package com.xingchen.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.xingchen.dto.Result;
import com.xingchen.entity.ShopType;
import com.xingchen.mapper.ShopTypeMapper;
import com.xingchen.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

  @Autowired
  private RedisTemplate redisTemplate;

  @Override
  public Result queryTypeList() {
    String key = "cache:list";
    Object jsonObj = redisTemplate.opsForValue().get(key);
    if (BeanUtil.isNotEmpty(jsonObj)) {
      List<ShopType> typeList = (List<ShopType>) jsonObj;
      return Result.ok(typeList);
    }
    List<ShopType> typeList = query().orderByAsc("sort").list();
    redisTemplate.opsForValue().set(key,typeList);
    return Result.ok(typeList);
  }
}
