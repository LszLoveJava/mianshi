package com.xingchen.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class CustomRedisLock implements ILock{

  private StringRedisTemplate stringRedisTemplate;
  private String name;
  private static final String KEY_PREFIX = "lock:";
  private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
  private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
  static {
    UNLOCK_SCRIPT = new DefaultRedisScript<>();
    UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
    UNLOCK_SCRIPT.setResultType(Long.class);
  }

  public CustomRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
    this.stringRedisTemplate = stringRedisTemplate;
    this.name = name;
  }

  @Override
  public boolean tryLock(int timeoutSec) {
    String value = ID_PREFIX + Thread.currentThread().getId();
    Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + name,value, timeoutSec, TimeUnit.SECONDS);
    return Boolean.TRUE.equals(result);
  }

  @Override
  public void unlock() {
    stringRedisTemplate.execute(UNLOCK_SCRIPT,
            Collections.singletonList(KEY_PREFIX + name),
            ID_PREFIX + Thread.currentThread().getId());

    /*
    String value = ID_PREFIX + Thread.currentThread().getId();
    String redisValue = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
    if (value.equals(redisValue)) {
      stringRedisTemplate.delete(KEY_PREFIX + name);
    }*/
  }
}
