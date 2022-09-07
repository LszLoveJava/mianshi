package com.xingchen.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {

  private static final long BEGIN_TIMESTAMP = 1640995200L;
  private static final int COUNT_BITS = 32;  //序列号的位数

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  /**
   * 获取全局id
   * @param keyprefix  业务前缀
   * @return  全局id
   */
  public long nextId(String keyprefix) {
    //生成时间戳
    LocalDateTime now = LocalDateTime.now();
    long nowTimestamp = now.toEpochSecond(ZoneOffset.UTC);
    long timestamp = nowTimestamp - BEGIN_TIMESTAMP;
    //生成序列号
    String date = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
    long count = stringRedisTemplate.opsForValue().increment("icr:" + keyprefix + ":" + date);
    return timestamp << COUNT_BITS | count;
  }
}
