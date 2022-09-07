package com.xingchen;

import cn.hutool.json.JSONUtil;
import com.xingchen.entity.Shop;
import com.xingchen.service.impl.ShopServiceImpl;
import com.xingchen.utils.CacheClient;
import com.xingchen.utils.RedisConstants;
import com.xingchen.utils.RedisIdWorker;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SpringBootTest(classes = HmDianPingApplication.class)
@RunWith(SpringRunner.class)
public class HmDianPingApplicationTests {

  @Resource
  ShopServiceImpl shopServiceImpl;

  @Resource
  CacheClient cacheClient;

  @Autowired
  RedisIdWorker redisIdWorker;

  @Autowired
  StringRedisTemplate stringRedisTemplate;

  private ExecutorService executorService = Executors.newFixedThreadPool(20);

  @Test
  public void testCopy() {
    UserTest userTest = new UserTest();
    userTest.setId(1);
    userTest.setName("张三");
    userTest.setMessage("hello world");
    UserTestDto userTestDto = JSONUtil.toBean(JSONUtil.toJsonStr(userTest), UserTestDto.class);
    System.out.println("userDto: " + userTestDto);
  }

  @Test
  public void testIdWorker() throws InterruptedException {
    CountDownLatch countDownLatch = new CountDownLatch(100);
    Runnable task = () -> {
      for (int i = 0; i <100; i++) {
        long id = redisIdWorker.nextId("order");
        System.out.println("id = " + id);
      }
      countDownLatch.countDown();
    };
    long begin = System.currentTimeMillis();
    for (int i = 0; i < 100; i++) {
      executorService.submit(task);
    }
    countDownLatch.await();
    long end = System.currentTimeMillis();
    System.out.println("花费时间：" + (end - begin));
  }

  @Test
  public void testSaveShop() throws InterruptedException {
    Shop shop = shopServiceImpl.getById(1);
    cacheClient.setWithLogicalExpire(RedisConstants.CACHE_SHOP_KEY + 1L,shop,10L, TimeUnit.SECONDS);
  }

  @Test
  public void loadShopData() {
    //查询店铺信息
    List<Shop> list = shopServiceImpl.list();
    //把店铺分组，根据typeId分组
    Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
    //分批完成redis写入
    for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
      Long typeId = entry.getKey();
      String key = "shop:geo:" + typeId;
      List<Shop> value = entry.getValue();
      List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(value.size());
      for (Shop shop : value) {
        locations.add(new RedisGeoCommands.GeoLocation<>(shop.getId().toString(), new Point(shop.getX(), shop.getY())));
      }
      stringRedisTemplate.opsForGeo().add(key,locations);
    }
  }

  @Test
  public void testHyperLogLog() {
    String[] values = new String[1000];
    int j = 0;
    for (int i = 0; i < 1000000; i++) {
      j = i % 1000;
      values[j] = "user_" + j;
      if (j == 999) {
        stringRedisTemplate.opsForHyperLogLog().add("hl2",values);
      }
    }
    Long count = stringRedisTemplate.opsForHyperLogLog().size("hl2");
    System.out.println("count = " + count);
  }
}
