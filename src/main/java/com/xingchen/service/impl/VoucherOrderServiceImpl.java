package com.xingchen.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.xingchen.dto.Result;
import com.xingchen.entity.VoucherOrder;
import com.xingchen.mapper.VoucherOrderMapper;
import com.xingchen.service.ISeckillVoucherService;
import com.xingchen.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingchen.utils.RedisIdWorker;
import com.xingchen.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;


@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

  @Autowired
  private ISeckillVoucherService seckillVoucherService;

  @Autowired
  private RedisIdWorker redisIdWorker;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Autowired
  private RedissonClient redissonClient;

  private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
  static {
    SECKILL_SCRIPT = new DefaultRedisScript<>();
    SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
    SECKILL_SCRIPT.setResultType(Long.class);
  }

  private static final String QUEUE_NAME = "stream.orders";

  //private static final BlockingQueue<VoucherOrder> orderTasks = new ArrayBlockingQueue<>(1024 * 1024);

  private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

  private IVoucherOrderService proxy;

  @PostConstruct  //可以让该类一被初始化就执行
  private void init() {
    SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
  }

  /*
  private class VoucherOrderHandler implements Runnable {

    @Override
    public void run() {
      while (true) {
        try {
          //获取队列中的队列信息
          VoucherOrder voucherOrder = orderTasks.take();
          //创建订单
          handleVoucherOrder(voucherOrder);
        } catch (InterruptedException e) {
          log.error("订单处理异常：" + e.getMessage());
        }
      }
    }
  }
   */

  private class VoucherOrderHandler implements Runnable {

    @Override
    public void run() {
      while (true) {
        try {
          //获取消息队列中的订单信息
          List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                  Consumer.from("g1", "c1"),
                  StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                  StreamOffset.create(QUEUE_NAME, ReadOffset.lastConsumed())
          );
          if (list == null || list.isEmpty()) {  //如果没有消息，则进入下一次循环
            continue;
          }
          //解析消息中的订单消息
          MapRecord<String, Object, Object> record = list.get(0);  //第一个参数是消息的id；参数类型跟队列的消息格式有关
          Map<Object, Object> map = record.getValue();
          VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(map, new VoucherOrder(), true);
          handleVoucherOrder(voucherOrder);
          stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME,"g1",record.getId());
        } catch (Exception e) {
          handlePendingList();
        }
      }
    }

    private void handlePendingList() {
      while (true) {
        try {
          List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                  Consumer.from("g1", "c1"),
                  StreamReadOptions.empty().count(1),
                  StreamOffset.create(QUEUE_NAME, ReadOffset.from("0"))  //从第一个开始读
          );
          if (list == null || list.isEmpty()) {
            break;
          }
          //解析消息中的订单消息
          MapRecord<String, Object, Object> record = list.get(0);  //第一个参数是消息的id；参数类型跟队列的消息格式有关
          Map<Object, Object> map = record.getValue();
          VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(map, new VoucherOrder(), true);
          handleVoucherOrder(voucherOrder);
          stringRedisTemplate.opsForStream().acknowledge(QUEUE_NAME,"g1",record.getId());
        } catch (Exception e) {
          log.error("处理pending-list订单异常",e);
        }
      }
    }
  }

  private void handleVoucherOrder(VoucherOrder voucherOrder) {
    Long userId = voucherOrder.getUserId();
    //创建锁对象
    RLock lock = redissonClient.getLock("lock:order:" + userId);
    boolean isLock = lock.tryLock();
    //判断是否获取锁成功
    if (!isLock) {
      return;
    }
    try {
      proxy.createVoucherOrder(voucherOrder);
    } finally {
      lock.unlock();
    }
  }

  @Override
  public Result seckillVoucher(Long voucherId) {
    //获取代理对象（因为子线程不能获取父线程中对象的代理对象，所以在父线程中先获取）
    proxy = (IVoucherOrderService) AopContext.currentProxy();
    Long userId = UserHolder.getUser().getId();
    Long orderId = redisIdWorker.nextId("order");
    //执行lua脚本
    Long result = stringRedisTemplate.execute(
            SECKILL_SCRIPT,
            Collections.emptyList(),
            voucherId.toString(), userId.toString(),orderId.toString()
    );
    //判断结果是否为0
    if (result != 0) {
      //不为0，代表没有购买资格
      return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
    }
    return Result.ok(orderId);
  }

  /*
  @Override
  public Result seckillVoucher(Long voucherId) {
    Long userId = UserHolder.getUser().getId();
    //执行lua脚本
    Long result = stringRedisTemplate.execute(
            SECKILL_SCRIPT,
            Collections.emptyList(),
            voucherId.toString(), userId.toString()
    );
    //判断结果是否为0
    if (result != 0) {
      //不为0，代表没有购买资格
      return Result.fail(result == 1 ? "库存不足" : "不能重复下单");
    }
    //为0，代表有购买资格，将下单信息保存到阻塞队列
    long orderId = redisIdWorker.nextId("order");
    VoucherOrder voucherOrder = new VoucherOrder();
    voucherOrder.setId(orderId);
    voucherOrder.setUserId(userId);
    voucherOrder.setVoucherId(voucherId);
    orderTasks.add(voucherOrder);
    //获取代理对象（因为子线程不能获取父线程中对象的代理对象，所以在父线程中先获取）
    proxy = (IVoucherOrderService)AopContext.currentProxy();
    return Result.ok(orderId);
  }

   */

  /*
  @Override
  public Result seckillVoucher(Long voucherId) {
    SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
    if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
      return Result.fail("秒杀未开始");
    }
    if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
      return Result.fail("秒杀已经结束");
    }
    if (voucher.getStock() < 1) {
      return Result.fail("库存不足");
    }
    Long userId = UserHolder.getUser().getId();
    //synchronized (userId.toString().intern()) {
      //获取事务代理对象
      //IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
      //return proxy.createVoucherOrder(voucherId);
    //}
    //创建锁对象
    //CustomRedisLock lock = new CustomRedisLock(stringRedisTemplate, "order:" + userId);
    RLock lock = redissonClient.getLock("lock:order:" + userId);
    boolean isLock = false;
    try {
      isLock = lock.tryLock(1L, TimeUnit.SECONDS);  //获取锁，等待时间为1L
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    //判断是否获取锁成功
    if (!isLock) {
      return Result.fail("不允许重复下单");
    }
    try {
      IVoucherOrderService proxy = (IVoucherOrderService)AopContext.currentProxy();
      return proxy.createVoucherOrder(voucherId);
    } finally {
      lock.unlock();
    }
  }*/

  @Transactional
  public  void createVoucherOrder(VoucherOrder voucherOrder) {
    //扣减库存
    seckillVoucherService.update()
                        .setSql("stock = stock - 1")
                        .eq("voucher_id",voucherOrder.getVoucherId()).update();
    //创建订单
    save(voucherOrder);
  }

  @Transactional
  public  Result createVoucherOrder(Long voucherId) {
    //实现一人一单
    Long id = UserHolder.getUser().getId();
      int count = query().eq("user_id",id).eq("voucher_id",voucherId).count();
      if (count > 0) {
        return Result.fail("用户已经购买过一次!");
      }
      //下订单
      boolean success = seckillVoucherService.update()
                          .setSql("stock = stock - 1")
                          .eq("voucher_id", voucherId)
                          .gt("stock",0)  //防止超卖
                          .update();
      if (!success) {
        return Result.fail("库存不足");
      }
      //创建订单
      VoucherOrder voucherOrder = new VoucherOrder();
      long orderId = redisIdWorker.nextId("order");
      Long userId = UserHolder.getUser().getId();
      voucherOrder.setId(orderId);
      voucherOrder.setUserId(userId);
      voucherOrder.setVoucherId(voucherId);
      save(voucherOrder);
      return Result.ok(voucherOrder);
  }
}
