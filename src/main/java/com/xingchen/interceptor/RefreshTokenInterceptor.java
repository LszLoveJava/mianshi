package com.xingchen.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.xingchen.dto.UserDTO;
import com.xingchen.utils.RedisConstants;
import com.xingchen.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {

  private StringRedisTemplate stringRedisTemplate;

  public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    //1.获取请求头中的token
    String token = request.getHeader("authorization");
    if (StrUtil.isBlank(token)) {
      return true;
    }
    //2.获取token获取redis中的用户
    Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(token);
    //3.判断用户是否存在
    if (userMap.isEmpty()) {
      return true;
    }
    //5.将查询到的Hash数据转为UserDTO对象
    UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
    //6.存在，则保存用户信息到ThreadLocal中
    UserHolder.saveUser((UserDTO) userDTO);
    //7.刷新token有效期
    stringRedisTemplate.expire(token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
    //8.放行
    return true;
  }

  @Override
  public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
    UserHolder.removeUser();
  }
}
