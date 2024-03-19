Lealone 高度兼容 MySQL 的协议和 SQL 语法，可以使用 MySQL 的各种客户端访问 Lealone。


## 编译运行需要

* [JDK 1.8+](https://www.oracle.com/java/technologies/downloads/)

* Maven 3.8+

* MySQL 版本支持 5.x 到 8.x 系列


## 打包插件

运行 `mvn clean package -Dmaven.test.skip=true`

生成 jar 包 `target\lealone-mysql-plugin-6.0.0.jar`

假设 jar 包的绝对路径是 `E:\lealone\lealone-plugins\mysql\target\lealone-mysql-plugin-6.0.0.jar`


## 下载 Lealone

[lealone-6.0.0.jar](https://github.com/lealone/Lealone-Docs/releases/download/lealone-docs-1.0.0/lealone-6.0.0.jar)

Lealone 只有一个 jar 包，下载下来之后随意放到一个目录即可

也可以从源代码构建最新版本，请阅读文档: [从源码构建 Lealone](https://github.com/lealone/Lealone-Docs/blob/master/%E5%BA%94%E7%94%A8%E6%96%87%E6%A1%A3/%E4%BB%8E%E6%BA%90%E7%A0%81%E6%9E%84%E5%BB%BALealone.md)


## 启动 Lealone 数据库

打开一个新的命令行窗口，运行: `java -jar lealone-6.0.0.jar`

```java
Lealone version: 6.0.0
Use default config
Base dir: E:/lealone/lealone-6/target/lealone_data
Init storage engines: 4 ms
Init transaction engines: 32 ms
Init sql engines: 4 ms
Init protocol server engines: 9 ms
Init lealone database: 104 ms
TcpServer started, host: 127.0.0.1, port: 9210
Total time: 261 ms (Load config: 2 ms, Init: 258 ms, Start: 1 ms)
Exit with Ctrl+C
```

要停止 Lealone，直接按 Ctrl + C


## 运行插件

打开一个新的命令行窗口，运行: `java -jar lealone-6.0.0.jar -client`

然后执行以下命令创建并启动插件：

```sql
create plugin mysql
  implement by 'com.lealone.plugins.mysql.MySQLPlugin' 
  class path 'E:\lealone\lealone-plugins\mysql\target\lealone-mysql-plugin-6.0.0.jar'
  --端口号默认就是3306，如果被其他进程占用了可以改成别的
  parameters (port=3306);
 
start plugin mysql;
```

要 stop 和 drop 插件可以执行以下命令：

```sql
stop plugin mysql;

drop plugin mysql;
```

执行 stop plugin 只是把插件对应的服务停掉，可以再次通过执行 start plugin 启动插件

执行 drop plugin 会把插件占用的内存资源都释放掉，需要再次执行 create plugin 才能重新启动插件


## 用 MySQL 客户端访问 Lealone 数据库

执行以下命令启动 MySQL 客户端:

`mysql --no-beep -h 127.0.0.1 -P 3306 -u root`

```sql
Welcome to the MySQL monitor.  Commands end with ; or \g.
Your MySQL connection id is 4
Server version: 5.7.35 Lealone-6.0.0 Community Server - SSPL

Copyright (c) 2000, 2021, Oracle and/or its affiliates.

Oracle is a registered trademark of Oracle Corporation and/or its
affiliates. Other names may be trademarks of their respective
owners.

Type 'help;' or '\h' for help. Type '\c' to clear the current input statement.

mysql> create table if not exists pet(name varchar(20), age int);
Query OK, 0 rows affected (0.00 sec)

mysql> insert into pet values('pet1', 2);
Query OK, 1 row affected (0.01 sec)

mysql> select count(*) from pet;
+----------+
| COUNT(*) |
+----------+
|        1 |
+----------+
1 row in set (0.01 sec)

mysql>
```
