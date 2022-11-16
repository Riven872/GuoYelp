package com.Guo.GuoYelp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.Guo.GuoYelp.dto.LoginFormDTO;
import com.Guo.GuoYelp.dto.Result;
import com.Guo.GuoYelp.dto.UserDTO;
import com.Guo.GuoYelp.utils.RegexUtils;
import com.Guo.GuoYelp.utils.UserHolder;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.Guo.GuoYelp.entity.User;
import com.Guo.GuoYelp.mapper.UserMapper;
import com.Guo.GuoYelp.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.Guo.GuoYelp.utils.SystemConstants;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.Guo.GuoYelp.utils.RedisConstants.*;
import static com.Guo.GuoYelp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     *
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机号码
        if (RegexUtils.isPhoneInvalid(phone)) {
            //如果不符合，返回错误信息
            return Result.fail("手机号码格式错误");
        }

        //符合则生成验证码
        String code = RandomUtil.randomNumbers(6);

        //保存验证码到redis中，且key值加前缀作为业务逻辑的区分
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //发送验证码
        log.info("验证码为：{}", code);

        return Result.ok();
    }

    /**
     * 账号登录
     *
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        //region 从Redis获取验证码并校验
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (cacheCode == null || !cacheCode.equals(loginForm.getCode())) {
            return Result.fail("验证码错误");
        }
        //endregion

        //通过校验，根据手机号查询用户
        User user = query().eq("phone", phone).one();

        //用户不存在，创建新用户并保存
        if (user == null) {
            user = this.createUserWithPhone(phone);
        }

        //region 保存用户信息到redis中
        //随机生成token，作为登录令牌
        String token = UUID.randomUUID().toString(true);
        //将User对象转为HashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //存储User到Redis中
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        //设置token有效期为半小时
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //endregion

        //登录成功，则将验证码从Redis中删除
        stringRedisTemplate.delete(LOGIN_CODE_KEY + phone);

        //返回Token
        return Result.ok(token);
    }

    /**
     * 查看用户主页并返回用户信息
     *
     * @param id
     * @return
     */
    @Override
    public Result getUserById(Long id) {
        User user = this.getById(id);
        if (user == null) {
            return Result.ok();
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    /**
     * 用户签到
     *
     * @return
     */
    @Override
    public Result sign() {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //获取当前日期
        LocalDateTime currentTime = LocalDateTime.now();
        //拼接key
        String keySuffix = currentTime.format(DateTimeFormatter.ofPattern(":yyyyMM"));//日期前缀
        String key = USER_SIGN_KEY + userId + keySuffix;
        //获取今天是该月的第几天
        int dayOfMonth = currentTime.getDayOfMonth();
        //写入Redis，签到成功则为true
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }

    /**
     * 统计签到次数
     *
     * @return
     */
    @Override
    public Result signCount() {
        //获取当前登录用户
        Long userId = UserHolder.getUser().getId();
        //获取当前日期
        LocalDateTime currentTime = LocalDateTime.now();
        //拼接key
        String keySuffix = currentTime.format(DateTimeFormatter.ofPattern(":yyyyMM"));//日期前缀
        String key = USER_SIGN_KEY + userId + keySuffix;
        //获取今天是该月的第几天
        int dayOfMonth = currentTime.getDayOfMonth();
        //获取本月到今天为止的所有签到次数
        List<Long> result = stringRedisTemplate
                .opsForValue()
                .bitField(
                        key,
                        BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0));
        //没有签到结果（因为只get了一次，因此List中只有一个元素）
        if (result == null || result.isEmpty() || result.get(0) == null) {
            return Result.ok();
        }
        //循环遍历处理数据
        Long num = result.get(0);
        int count = 0;//计数器
        //每个数字与1做与运算，得到数字的最后一个bit位，且判断该bit位是否为0
        while (true) {
            if ((num & 1) == 0) {
                //如果没有0，则说明未签到
                break;
            } else {
                //如果不为0，则说明已签到
                count++;
            }
            //无符号右移1位，并将最后一位赋值给num
            num >>>= 1;
        }
        return Result.ok(count);
    }

    /**
     * 根据手机号创建用户
     *
     * @param phone
     */
    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        this.save(user);
        return user;
    }


}
