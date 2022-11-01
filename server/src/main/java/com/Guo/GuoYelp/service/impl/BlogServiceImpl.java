package com.Guo.GuoYelp.service.impl;

import com.Guo.GuoYelp.entity.Blog;
import com.Guo.GuoYelp.mapper.BlogMapper;
import com.Guo.GuoYelp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 *  服务实现类
 * </p>
 *

 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

}
