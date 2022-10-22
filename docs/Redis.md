##### 基本配置

###### 1、启动Redis

- 进入安装目录`/usr/local/redis-7.0.5`
- 使用配置文件启动`redis-server redis.conf`
- 设置日志文件，记录位置为当前工作目录`redis.log`

###### 2、开机自启

- 新建系统服务文件`vi /etc/systemd/system/redis.service`
- 内容为

> [Unit]
>
> Description=redis-server
>
> After=network.target
>
> 
>
> [Service]
>
> Type=forking
>
> ExecStart=/usr/local/redis-7.0.5/src/redis-server /usr/local/redis-7.0.5/redis.conf
>
> PrivateTmp=true
>
> 
>
> [Install]
>
> WantedBy=multi-user.target

- 重新加载系统服务`systemctl daemon-reload`
- 设置开机自启`systemctl enable redis`