package com.Guo.GuoYelp.service;

import com.Guo.GuoYelp.dto.Result;
import com.Guo.GuoYelp.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *

 */
public interface IFollowService extends IService<Follow> {

    Result follow(Long id, boolean isFollow);

    Result isFollow(Long id);

    Result followCommons(Long id);
}
