package com.xingchen.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xingchen.dto.Result;
import com.xingchen.dto.ScrollResult;
import com.xingchen.dto.UserDTO;
import com.xingchen.entity.Blog;
import com.xingchen.entity.Follow;
import com.xingchen.entity.User;
import com.xingchen.mapper.BlogMapper;
import com.xingchen.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xingchen.service.IFollowService;
import com.xingchen.utils.RedisConstants;
import com.xingchen.utils.SystemConstants;
import com.xingchen.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;


@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

  @Autowired
  private UserServiceImpl userService;

  @Autowired
  private IFollowService followService;

  @Autowired
  private StringRedisTemplate stringRedisTemplate;

  @Override
  public Result queryHotBlog(Integer current) {
    // 根据用户查询
    Page<Blog> page = query()
            .orderByDesc("liked")
            .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
    // 获取当前页数据
    List<Blog> records = page.getRecords();
    //查询用户
    records.forEach(blog -> {
      this.queryBlogUser(blog);
      this.isBlogLiked(blog);
    });
    return Result.ok(records);
  }

  @Override
  public Result queryBlogById(Long id) {
    Blog blog = getById(id);
    if (blog == null) {
      return Result.fail("博客不存在");
    }
    queryBlogUser(blog);
    //查询blog是否被当前用户点赞
    isBlogLiked(blog);
    return Result.ok(blog);
  }

  private void isBlogLiked(Blog blog) {
    Long userId = UserHolder.getUser().getId();
    //判断当前的用户有没有点赞
    String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
    Boolean result = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
    blog.setIsLike(BooleanUtil.isTrue(result));
  }

  @Override
  public Result likeBlog(Long id) {
    Long userId = UserHolder.getUser().getId();
    //判断当前的用户有没有点赞
    String key = RedisConstants.BLOG_LIKED_KEY + id;
    Boolean result = stringRedisTemplate.opsForSet().isMember(key, userId.toString());
    if (BooleanUtil.isFalse(result)) {  //未点赞，可以点赞
      update().setSql("liked = liked + 1").eq("id", id).update();
      stringRedisTemplate.opsForSet().add(key,userId.toString());
    } else {  //已点赞，取消点赞
      update().setSql("liked = liked - 1").eq("id", id).update();
      stringRedisTemplate.opsForSet().remove(key,userId.toString());
    }
    return null;
  }

  @Override
  public Result queryBlogLikes(Long id) {
    //查询Top5的点赞用户
    String key = RedisConstants.BLOG_LIKED_KEY + id;
    Set<String> ids = stringRedisTemplate.opsForSet().members(key);
    if (ids == null || ids.isEmpty()) {
      return Result.ok(Collections.emptyList());
    }
    List<Long> list = ids.stream().map(Long::valueOf).collect(Collectors.toList());
    List<UserDTO> userDTOS = userService.listByIds(list).stream()
                                                        .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                                                        .collect(Collectors.toList());
    return Result.ok(userDTOS);
  }

  @Override
  public Result saveBlog(Blog blog) {
    UserDTO user = UserHolder.getUser();
    blog.setUserId(user.getId());
    //保存blog
    boolean isSuccess = save(blog);
    if (!isSuccess) {
      Result.fail("推送笔记失败！");
    }
    //查询笔记作者的粉丝
    List<Follow> followList = followService.query().eq("follow_user_id", user.getId()).list();
    //推送笔记给所有粉丝
    for (Follow follow : followList) {
      Long userId = follow.getUserId();
      String key = RedisConstants.FEED_KEY + userId;  //用户的收件箱
      stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());
    }
    return Result.ok();
  }

  @Override
  public Result queryBlogOfFollow(Long max, Integer offset) {
    //找到收件箱
    Long userId = UserHolder.getUser().getId();
    String key = RedisConstants.FEED_KEY + userId;
    Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(key, 0, max, offset, 2);
    if (typedTuples == null || typedTuples.isEmpty()) {
      return Result.ok();
    }
    //解析数据：blogId、minTime、offset
    ArrayList<Long> ids = new ArrayList<>(typedTuples.size());
    long minTime = 0;
    int os = 1;  //score相同的值的个数（即偏移量），默认为1
    for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
      ids.add(Long.valueOf(tuple.getValue()));
      long time = tuple.getScore().longValue();
      if (time == minTime) {
        os++;
      } else {
        minTime = time;
        os = 1;
      }
    }
    //根据id查询blog
    String idStr = StrUtil.join(",", ids);
    List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
    for (Blog blog : blogs) {
      queryBlogUser(blog);
      isBlogLiked(blog);
    }
    ScrollResult result = new ScrollResult();
    result.setList(blogs);
    result.setOffset(os);
    result.setMinTime(minTime);
    return Result.ok(result);
  }

  private void queryBlogUser(Blog blog) {
    Long userId = blog.getUserId();
    User user = userService.getById(userId);
    blog.setName(user.getNickName());
    blog.setIcon(user.getIcon());
  }
}
