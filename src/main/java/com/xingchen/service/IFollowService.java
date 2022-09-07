package com.xingchen.service;

import com.xingchen.dto.Result;
import com.xingchen.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;


public interface IFollowService extends IService<Follow> {

  Result follow(Long followUserId, Boolean isFollow);

  Result isFollow(Long followUserId);

  Result followCommons(Long id);
}
