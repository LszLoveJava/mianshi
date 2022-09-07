package com.xingchen.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingchen.dto.LoginFormDTO;
import com.xingchen.dto.Result;
import com.xingchen.dto.UserDTO;
import com.xingchen.entity.User;
import com.xingchen.mapper.UserMapper;
import com.xingchen.service.IUserService;
import com.xingchen.utils.RedisConstants;
import com.xingchen.utils.RegexUtils;
import com.xingchen.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.xingchen.utils.SystemConstants;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

  private static final String USER_NICK_NAME_PREFIX = SystemConstants.USER_NICK_NAME_PREFIX;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  /**
   * 发送验证码
   * @param phone  接收验证码的手机号
   * @return  发送结果
   */
  @Override
  public Result sendCode(String phone) {
    //1.校验手机号
    if (RegexUtils.isPhoneInvalid(phone)) {
      //2.如果不符合，返回错误信息
      return Result.fail("手机号格式错误");
    }
    //3.符合则生成验证码
    String code = RandomUtil.randomNumbers(6);
    //4.保存验证码到redis中
    stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
    //5.发送验证码
    log.debug("发送短信验证码成功，验证码:{}",code);
    return Result.ok();
  }

  /**
   * 用户登录
   * @param loginForm 登录的表单信息
   * @return  登录结果
   */
  @Override
  public Result login(LoginFormDTO loginForm) {
    //1.校验手机号
    String phone = loginForm.getPhone();
    if (RegexUtils.isPhoneInvalid(phone)) {
      return Result.fail("手机号格式错误");
    }
    //2.校验验证码
    String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
    String code = loginForm.getCode();
    if (cacheCode == null || !cacheCode.equals(code)) {
      //3.不一致，直接报错
      return Result.fail("验证码错误");
    }
    //4.一致，根据手机号查询用户
    User user = query().eq("phone", phone).one();
    //5.判断用户是否存在
    if (user == null) {
      //6.不存在，则创建新用户并保存
      user = createUserWithPhone(phone);
    }
    //7.保存用户信息到redis中
    //7.1随机生成token，作为登录令牌
    String token = UUID.randomUUID().toString(true);  //true表示不带下划线
    //7.2将User对象转为HashMap存储
    UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
    Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,new HashMap<>(),
            CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName,fieldValue) -> fieldValue.toString()));
    //7.3存储
    String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
    stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
    //7.4设置有效期
    stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL,TimeUnit.MINUTES);
    //8.返回token
    return Result.ok(tokenKey);
  }

  @Override
  public Result sign() {
    //获取当前登录用户
    Long userId = UserHolder.getUser().getId();
    //获取日期
    LocalDateTime now = LocalDateTime.now();
    //拼接key
    String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
    String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
    //获取今天是本月的第几天
    int dayOfMonth = now.getDayOfMonth();
    //写入redis
    stringRedisTemplate.opsForValue().setBit(key,dayOfMonth - 1,true);
    return Result.ok();
  }

  @Override
  public Result signCount() {
    Long userId = UserHolder.getUser().getId();
    LocalDateTime now = LocalDateTime.now();
    String keySuffix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
    String key = RedisConstants.USER_SIGN_KEY + userId + keySuffix;
    int dayOfMonth = now.getDayOfMonth();
    //获取本月截止今天为止的所有签到记录，返回的是一个十进制的数字
    List<Long> result = stringRedisTemplate.opsForValue().bitField(key,
            BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
    if (BeanUtil.isEmpty(result)) {
      return Result.ok(0);
    }
    Long num = result.get(0);
    if (num == null || num == 0) {
      return Result.ok(0);
    }
    //循环遍历
    int count = 0;
    while (true) {
      //让这个数字与1座与运算，得到数字的最后一个bit位
      if ((num & 1) == 0) {  //如果为0，说明未签到，结束
        break;
      } else {  //如果为1，说明已签到，计数器+1
        count++;
        num >>>= 1; //把数字右移一位，抛弃最后一个bit位，继续下一个bit位
      }
    }
    return Result.ok(count);
  }

  private User createUserWithPhone(String phone) {
    User user = new User();
    user.setPhone(phone);
    user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
    save(user);
    return user;
  }
}
