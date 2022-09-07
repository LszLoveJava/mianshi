package com.xingchen.config;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.UnknownHostException;

@Configuration
public class RedisConfig {

  @Bean
  public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) throws UnknownHostException {
    //为了简化开发 一般自定义template<String,Object>
    RedisTemplate<String, Object> template = new RedisTemplate<>();
    //new 一个Jackson的序列化对象
    Jackson2JsonRedisSerializer serializer = new Jackson2JsonRedisSerializer(Object.class);
    template.setConnectionFactory(redisConnectionFactory);
    //Json序列化设置
    ObjectMapper om = new ObjectMapper();
    om.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
    om.activateDefaultTyping(LaissezFaireSubTypeValidator.instance, ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
    serializer.setObjectMapper(om);
    //String的序列化
    StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
    //key采用String的序列化方式
    template.setKeySerializer(stringRedisSerializer);
    //hash的key也采用String的序列化方式
    template.setHashKeySerializer(stringRedisSerializer);
    //value的序列化采用jackson
    template.setValueSerializer(serializer);
    //hash的value也采用jackson的序列化
    template.setHashValueSerializer(serializer);
    return template;
  }
}
