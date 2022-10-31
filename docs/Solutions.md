1、Session共享

- 问题：多台服务器并不共享session存储空间，当通过负载均衡等切换的到不同的服务器进行数据处理时，会导致存到session中的数据丢失
- 措施：
    - × session拷贝，多个服务器共享一个session
        - 但是会造成服务器内存空间的浪费，且拷贝的时间延迟会造成数据不一致性
    - √ 将数据存放到Redis中，访问效率相同，且每台服务器都可以访问到Redis，不会造成数据不一致性的问题

2、Map类型对象以Hash类型放入Redis中时出错

- 问题：stringRedisTemplate的泛型是<string, string>，因此要求key、value都为string类型，而如果Map中的key或value有不为string的类型，则会出现类型转换错误。

    ```java
    //如userDTO中的id为Long类型，则将数据放到Redis中时会报错
    UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
    Map<String, Object> userMap = BeanUtil.beanToMap(userDTO);
    ```

- 措施：huTool工具中，实体类对象转为Map时类型时，可以将字段转换为String类型

    ```java
    UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
    //参数一：转换的实体 参数二：转换目标实体的类型 参数三：自定义规则
    Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                          CopyOptions.create()
                          .setIgnoreNullValue(true) //忽略为null的字段
                          .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString())); //所有字段的类型转为string
    ```

    
