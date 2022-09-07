package com.xingchen.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Component
@Slf4j
public class CacheClient {

  private final StringRedisTemplate stringRedisTemplate;

  private static final ExecutorService REBUILD_CACHE = Executors.newSingleThreadExecutor();

  public CacheClient(StringRedisTemplate stringRedisTemplate) {
    this.stringRedisTemplate = stringRedisTemplate;
  }

  public void set(String key, Object value, Long time, TimeUnit unit) {
    stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(value),time,unit);
  }

  public void setWithLogicalExpire(String key,Object value,Long time,TimeUnit unit) {
    RedisData redisData = new RedisData();
    redisData.setData(value);
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
    stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
  }


  //解决查询商品的缓存穿透
  public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type,
                                       Function<ID,R> dbFallback,Long time,TimeUnit unit) {
    String key = keyPrefix + id;
    String json = stringRedisTemplate.opsForValue().get(key);
    if (StrUtil.isNotBlank(json)) {
      return JSONUtil.toBean(json, type);
    }
    //判断命中的值是否是空值
    if (json != null) {
      return null;
    }
    R result = dbFallback.apply(id);
    if (result == null) {
      //将空值写入redis，解决缓存穿透
      stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
      return null;
    }
    set(key,result,time,unit);
    return result;
  }


  public <R,ID> R queryWithLogicalExpire(String keyPrefix,ID id,Class<R> type,
                                          Function<ID,R> dbFallback,Long time,TimeUnit unit) {
    String key = keyPrefix + id;
    String json = stringRedisTemplate.opsForValue().get(key);
    if (StrUtil.isBlank(json)) {  //不存在直接返回
      return null;
    }
    //存在则将Json反序列化为对象
    RedisData redisData = JSONUtil.toBean(json,RedisData.class);
    R result = JSONUtil.toBean((JSONObject) redisData.getData(),type);
    LocalDateTime expireTime = redisData.getExpireTime();
    //判断是否过期
    if (expireTime.isAfter(LocalDateTime.now())) {
      //未过期，直接返回信息
      return result;
    }
    //已过期，需要缓存重建
    //获取互斥锁
    String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
    boolean isLock = tryLock(lockKey);
    //判断是否获取锁成功
    if (isLock) {
      //成功，开启独立线程实现缓存重建
      REBUILD_CACHE.submit( () -> {
        try {
          //查询数据库
          R r1 = dbFallback.apply(id);
          //写入redis
          setWithLogicalExpire(key,r1,time,unit);
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          unlock(lockKey);
        }
      });
    }
    return result;
  }

  private boolean tryLock(String key) {
    Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    return BooleanUtil.isTrue(result);
  }

  private void unlock(String key) {
    stringRedisTemplate.delete(key);
  }
}
