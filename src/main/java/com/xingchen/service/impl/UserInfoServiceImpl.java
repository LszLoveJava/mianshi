package com.xingchen.service.impl;

import com.xingchen.entity.UserInfo;
import com.xingchen.mapper.UserInfoMapper;
import com.xingchen.service.IUserInfoService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {

}
