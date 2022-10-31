##### 基本配置

###### 1、redis.conf配置

- 监听的地址默认是127.0.0.1，限制只可以访问localhost，修改为0.0.0.0或注释掉可以在任意IP访问，不要在prd环境配置为任意IP

    > bind 127.0.0.1

- 守护进程，修改为yes后即可后台运行不用霸屏

    > daemonize no

- 密码，设置后访问redis必须输入密码，默认不需要密码，命令行登录时，需要用`auth`指令手动输入密码

- 可选配置

    - 监听的端口，默认6379

        > port 6379

    - 工作目录，默认当前目录，运行redis-server时的命令，日志、持久化等文件会保存在这个目录

        > dir .

    - 数据库数量，设置为1，代表只使用1个库，默认16个库，编号0-15

        > databases 1

    - 设置redis能够使用的最大内存

        > maxmemory 512mb

    - 日志文件，默认为空，不记录日志，可以指定日志文件名

        > logfile "redis.log"

###### 2、启动Redis

- 进入安装目录`/usr/local/redis-7.0.5`
- 使用配置文件启动`redis-server redis.conf`
- 设置日志文件，记录位置为当前工作目录`redis.log`

###### 3、开机自启

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