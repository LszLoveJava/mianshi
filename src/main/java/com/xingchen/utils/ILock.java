package com.xingchen.utils;

public interface ILock {

  /**
   * 获取锁
   * @param timeoutSec  获取不到锁时等待的时间
   * @return  成功返回true;失败返回false
   */
  boolean tryLock(int timeoutSec);

  /**
   * 释放锁
   */
  void unlock();
}
