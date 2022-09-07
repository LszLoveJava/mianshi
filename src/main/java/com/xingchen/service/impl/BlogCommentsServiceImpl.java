package com.xingchen.service.impl;

import com.xingchen.entity.BlogComments;
import com.xingchen.mapper.BlogCommentsMapper;
import com.xingchen.service.IBlogCommentsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;


@Service
public class BlogCommentsServiceImpl extends ServiceImpl<BlogCommentsMapper, BlogComments> implements IBlogCommentsService {

}
