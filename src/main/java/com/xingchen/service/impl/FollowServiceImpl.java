package com.xingchen.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.xingchen.dto.Result;
import com.xingchen.dto.UserDTO;
import com.xingchen.entity.Follow;
import com.xingchen.mapper.FollowMapper;
import com.xingchen.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingchen.service.IUserService;
import com.xingchen.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Autowired
  private IUserService userService;

  @Override
  public Result follow(Long followUserId, Boolean isFollow) {
    Long userId = UserHolder.getUser().getId();
    String key = "follows:" + userId;
    //判断是关注还是取关
    if (isFollow) {  //关注
      Follow follow = new Follow();
      follow.setUserId(userId);
      follow.setFollowUserId(followUserId);
      boolean isSuccess = save(follow);
      if (isSuccess) {
        stringRedisTemplate.opsForSet().add(key,followUserId.toString());
      }
    } else {  //取关
      boolean isSuccess = remove(new QueryWrapper<Follow>().eq("user_id", userId).eq("follow_user_id", followUserId));
      if (isSuccess) {
        stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
      }
    }
    return Result.ok();
  }

  @Override
  public Result isFollow(Long followUserId) {
    Long userId = UserHolder.getUser().getId();
    Integer count = query().eq("user_id", userId).eq("follow_user_id", followUserId).count();
    return Result.ok(count > 0);
  }

  @Override
  public Result followCommons(Long id) {
    Long userId = UserHolder.getUser().getId();
    String key = "follows:" + userId;
    String key2 = "follows:" + id;
    Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key, key2);
    if (intersect == null || intersect.isEmpty()) {
      return Result.ok(Collections.emptyList());
    }
    List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
    List<UserDTO> users = userService.listByIds(ids)
                                      .stream()
                                      .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                                      .collect(Collectors.toList());
    return Result.ok(users);
  }
}
