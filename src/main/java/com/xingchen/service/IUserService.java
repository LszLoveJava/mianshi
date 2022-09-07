package com.xingchen.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.xingchen.dto.LoginFormDTO;
import com.xingchen.dto.Result;
import com.xingchen.entity.User;


public interface IUserService extends IService<User> {

  Result sendCode(String phone);

  Result login(LoginFormDTO loginForm);

  Result sign();

  Result signCount();
}
