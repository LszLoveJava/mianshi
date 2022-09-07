package com.xingchen.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xingchen.dto.Result;
import com.xingchen.entity.Shop;
import com.xingchen.mapper.ShopMapper;
import com.xingchen.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingchen.utils.CacheClient;
import com.xingchen.utils.RedisConstants;
import com.xingchen.utils.RedisData;
import com.xingchen.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.data.redis.domain.geo.Metrics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  private static final ExecutorService REBUILD_CACHE = Executors.newSingleThreadExecutor();

  @Autowired
  private CacheClient cacheClient;

  /**
   * 查询店铺
   * @param id  店铺id
   * @return
   */
  @Override
  public Result queryById(Long id) {

    //使用互斥锁解决缓存击穿
    /*String key = RedisConstants.CACHE_SHOP_KEY + id;
    String shopJson = stringRedisTemplate.opsForValue().get(key);
    if (StrUtil.isNotBlank(shopJson)) {
      Shop shop = JSONUtil.toBean(shopJson, Shop.class);
      return Result.ok(shop);
    }
    //判断命中的值是否是空值
    if (shopJson != null) {
      return Result.ok("店铺信息不存在");
    }
    Shop shop = getById(id);
    if (shop == null) {
      //将空值写入redis，解决缓存穿透
      stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
      return Result.fail("店铺不存在");
    }
    stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
    return Result.ok(shop);*/

    //解决缓存穿透
    //Shop shop = queryWithPassThrough(id);

    //互斥锁解决缓存击穿
    //Shop shop = queryWithMutex(id);


    //逻辑过期解决缓存击穿
    //Shop shop = queryWithLogicalExpire(id);
    Shop shop = cacheClient.queryWithPassThrough(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
    //Shop shop = cacheClient.queryWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, 10L, TimeUnit.SECONDS);
    if (shop == null) {
      return Result.fail("店铺不存在");
    }
    return Result.ok(shop);
  }

  @Override
  @Transactional
  public Result updateShop(Shop shop) {
    Long id = shop.getId();
    if (id == null) {
      return Result.fail("店铺id不能为空");
    }
    //更新数据库
    updateById(shop);
    //删除缓存
    stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
    return Result.ok();
  }

  @Override
  public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
    //判断是否需要根据坐标查询
    if (x == null || y == null) {
      Page<Shop> page = query().eq("type_id", typeId)
                                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
      return Result.ok(page.getRecords());
    }
    //计算分页参数
    int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
    int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
    //查询redis、按照距离排序、分页。结果：shopId、distance
    String key = RedisConstants.SHOP_GEO_KEY + typeId;
    GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key,
                              GeoReference.fromCoordinate(x, y),
                              new Distance(5, Metrics.KILOMETERS),
                              RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));
    //解析出id
    if (results == null) {
      return Result.ok(Collections.emptyList());
    }
    List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
    if (list.size() <= from) {
      return Result.ok(Collections.emptyList());
    }
    //截取from ~ end的部分
    List<Long> ids = new ArrayList<>(list.size());
    Map<String, Distance> distanceMap = new HashMap<>();
    list.stream().skip(from).forEach(r -> {
      //获取店铺id
      String shopIdStr = r.getContent().getName();
      ids.add(Long.valueOf(shopIdStr));
      //获取距离
      Distance distance = r.getDistance();
      distanceMap.put(shopIdStr,distance);
    });
    //根据id查询Shop
    String idStr = StrUtil.join(",", ids);
    List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
    for (Shop shop : shops) {
      shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
    }
    return Result.ok(shops);
  }

  /**
   * 使用互斥锁解决查询商品的缓存击穿
   * @param id
   * @return
   */
  private Shop queryWithMutex(Long id) {
    String key = RedisConstants.CACHE_SHOP_KEY + id;
    String shopJson = stringRedisTemplate.opsForValue().get(key);
    if (StrUtil.isNotBlank(shopJson)) {
      return JSONUtil.toBean(shopJson, Shop.class);
    }
    //判断命中的值是否是空值
    if (shopJson != null) {
      return null;
    }
    //实现缓存重建
    //获取互斥锁
    String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
    Shop shop = null;
    try {
      boolean isLock = tryLock(lockKey);
      //判断是否获取成功
      if (!isLock) {
        //失败则休眠并重试
        Thread.sleep(50);
        queryWithPassThrough(id);
      }
      //成功则根据id查询数据库
      shop = getById(id);
      if (shop == null) {
        //将空值写入redis，解决缓存穿透
        stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
        return null;
      }
      //存在，则写入redis
      stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    } finally {
      //释放互斥锁
      unlock(lockKey);
    }
    return shop;
  }

  /**
   * 使用逻辑过期解决商品的缓存击穿
   * @param id
   * @return
   */
  private Shop queryWithLogicalExpire(Long id) {
    String key = RedisConstants.CACHE_SHOP_KEY + id;
    String shopJson = stringRedisTemplate.opsForValue().get(key);
    if (StrUtil.isBlank(shopJson)) {  //不存在直接返回
      return null;
    }
    //存在则将Json反序列化为对象
    RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
    Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
    LocalDateTime expireTime = redisData.getExpireTime();
    //判断是否过期
    if (expireTime.isAfter(LocalDateTime.now())) {
      //未过期，直接返回信息
      return shop;
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
          saveShop2Redis(id,20L);
        } catch (InterruptedException e) {
          e.printStackTrace();
        } finally {
          unlock(lockKey);
        }
      });
    }
    return shop;
  }

  /**
   * 解决查询商品的缓存穿透
   * @param id
   * @return
   */
  private Shop queryWithPassThrough(Long id) {
    String key = RedisConstants.CACHE_SHOP_KEY + id;
    String shopJson = stringRedisTemplate.opsForValue().get(key);
    if (StrUtil.isNotBlank(shopJson)) {
      return JSONUtil.toBean(shopJson, Shop.class);
    }
    //判断命中的值是否是空值
    if (shopJson != null) {
      return null;
    }
    Shop shop = getById(id);
    if (shop == null) {
      //将空值写入redis，解决缓存穿透
      stringRedisTemplate.opsForValue().set(key,"",RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
      return null;
    }
    stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
    return shop;
  }

  /**
   * 获取互斥锁
   * @param key
   * @return
   */
  private boolean tryLock(String key) {
    Boolean result = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
    return BooleanUtil.isTrue(result);
  }

  private void unlock(String key) {
    stringRedisTemplate.delete(key);
  }

  /**
   * 向redis中写入店铺信息（带有逻辑过期时间）
   * @param id
   * @param expireSeconds
   */
  public void saveShop2Redis(Long id,Long expireSeconds) throws InterruptedException {
    //查询店铺信息
    Shop shop = getById(id);
    Thread.sleep(200);
    //封装逻辑过期时间
    RedisData redisData = new RedisData();
    redisData.setData(shop);
    redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
    //写入redis
    stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(redisData));
  }
}
