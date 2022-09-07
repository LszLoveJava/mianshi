package com.xingchen.config;

import com.xingchen.interceptor.LoginInterceptor;
import com.xingchen.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MVCConfig implements WebMvcConfigurer {

  @Resource
  private StringRedisTemplate stringRedisTemplate;

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    /*
      order越小，优先级越高（默认为0）。
      如果没有指定优先级，按照拦截器被添加的顺序执行。
     */
    //addPathPatterns("/**")是默认行为
    registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).addPathPatterns("/**").order(0);
    registry.addInterceptor(new LoginInterceptor())
            .excludePathPatterns(
                    "/user/code",
                    "/user/login",
                    "/blog/hot",
                    "/shop/**",
                    "/shop-type/**",
                    "/upload/**",
                    "/voucher/**"
            ).order(1);
  }
}
