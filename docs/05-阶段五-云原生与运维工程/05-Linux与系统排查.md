# 阶段五 · 小点 5：Linux 与系统排查

> 所属：阶段五 云原生与运维工程
> 定位：问题最终都落在「某台机器的某个进程」上。这一讲把 OS 层工具与 Java 进程诊断工具接起来，形成**从现象到代码行**的完整排查链——并背下一个必考范式的五步流程。

## 快速入门

> 本节为「故障排查速览」：先认识排查要用哪些命令、各自看什么；「五步范式、火焰图」留在正文提高部分。

### 本讲核心关键词速查

| 关键词 | 一句话大白话 | 例子 |
|-|-|-|
| 进程 / 线程 | 运行中的程序 / 进程内的执行单元 | `ps` / `top` |
| CPU 100% | 进程一直在算 | 死循环/自旋 |
| 内存 | 进程占用内存 | 堆/直接内存 |
| jstack | 打印 Java 线程栈 | 看线程在干嘛 |
| jmap | 看堆内存快照 | OOM 分析 |
| jstat | 看 GC 统计 | 内存健康 |
| Arthas | 在线诊断工具 | 不改代码看方法耗时 |
| 火焰图 | 看 CPU 热点的方法 | 一眼看到热点 |

### 本讲在解决什么问题

- **问题**：线上 CPU 飙高、内存 OOM、连接池耗尽——你只能看日志猜？这一讲教你用 OS 工具 + Java 诊断工具，**从「现象」定位到「代码行」**。
- **你要带走的一句话**：排查主线是「**top 看进程 → top -Hp 看线程 → printf 线程 id 转 16 进制 → jstack 看线程栈 → 定位到代码**」。CPU 100% 用这条链，一步到代码行。

### 最简可运行示例（照抄能跑）

```bash
# CPU 100% 排查五步: 从进程到代码行
top                                          # ① 看哪个进程 CPU 高(记下 PID)
top -Hp <PID>                                # ② 看该进程里哪个线程 CPU 高(记下 TID)
printf "%x\n" <TID>                         # ③ 把线程 10 进制转 16 进制(nid 用)
jstack <PID> > dump.txt                      # ④ 抓该进程的线程栈
grep -A20 "<16进制nid>" dump.txt             # ⑤ 在栈里找到那个线程, 看它卡在哪段代码
```

> 命令备注（逐行解释）：
> - `top`：看哪个进程 CPU 高，记下 PID。
> - `top -Hp <PID>`：看这个进程里哪个**线程** CPU 高，记下 TID。
> - `printf "%x\n" <TID>`：把线程 ID 转成 16 进制——因为 `jstack` 输出里线程号是 16 进制（`nid=0x...`）。
> - `jstack <PID>`：抓整个进程的线程栈，保存成文件。
> - `grep -A20 "0x..."`：在栈里找到那个高 CPU 的线程，看它卡在哪段代码——**从现象到代码行**。

### 关键工具说明

| 工具 | 干什么 | 最易踩的坑 |
|-|-|-|
| `top` / `top -Hp` | 看进程/线程 CPU | 记下 PID/TID 再抓栈 |
| `jstack` | 线程栈 | 线程号是 16 进制，要转换 |
| `jmap` / `jstat` | 堆 / GC 统计 | OOM 分析用 |
| `Arthas` | 在线诊断 | `dashboard` / `thread -n 5` / `trace` |
| `printf "%x\n"` | 十进制转十六进制 | 匹配 jstack 的 nid |
| 火焰图 | CPU 热点 | 最宽的不是最高（栈宽=占比） |

### 常用约定 / 命名提示

- **排查模板**：CPU 高用五步（top→top -Hp→printf→jstack→grep）；连接池耗尽看 `HikariPool-1 - wait`（正文场景二）。
- **容器里优先 `kill -3`**：某些容器环境不方便上 Arthas 时，`kill -3 <pid>` 也能触发线程栈输出（正文展开）。
- **Arthas 三连**：`dashboard`（总览）→ `thread -n 5`（最忙线程）→ `trace`（跟踪方法耗时）。

## 精简大纲

1. 基础命令：进程 / 网络 / 磁盘
2. Java 进程诊断四件套：jstack / jmap / jstat / Arthas
3. 火焰图：async-profiler
4. 实战范式：CPU 100% 从现象到代码行
5. 实战场景二：连接池耗尽（HikariCP）

## 学习内容详情

### 1. 基础命令（按问题类型选）

```bash
# 进程与资源：先看全局负载再看单进程
top                     # 概览: 1 看 load average(运行队列长度), 3 看 %wa(IO等待) CPU st(steal)
vmstat 1                # 每秒一行: r(运行队列) b(阻塞) si/so(换页) us/sy/wa —— 趋势定位层
iostat -x 1             # 磁盘: %util 接近 100 + await 高 = 磁盘饱和
pidstat -p <pid> 1      # 单进程 CPU/IO 明细

# 网络：连接状态是第一现场
ss -s                   # 汇总: TCP 连接数分布, TIME-WAIT/CLOSE-WAIT 计数
ss -tanp | grep order   # 某服务的连接清单(含进程)
ss -k <五元组>          # 单连接的拥塞窗口/RTT —— 慢链路排查
lsof -p <pid> | wc -l   # 文件描述符占用(连接耗尽先看这个)
tcpdump -i any host 10.0.0.5 and port 3306 -w db.pcap   # 抓包终审(阶段一第10讲伏笔): 拿 pcap 进 Wireshark

# 磁盘
df -h                   # 空间满了: 日志没轮转 / dump 忘删 是 K8s 外的另一类"驱逐"
du -sh /var/log/*       # 找占用大头
```

### 2. Java 进程诊断四件套

| 工具 | 看什么 | 典型场景 |
|-|-|-|
| **jstack** | 线程快照 | 死锁（直接 `Found one Java-level deadlock`）、高 CPU 线程（配合第 4 节）、线程池饿死 |
| **jmap** | 堆直方图 / dump | `jmap -histo:live <pid> | head -20` 快速看谁占堆 → `-dump:live` 留现场给 MAT |
| **jstat** | GC 实时统计 | `jstat -gcutil <pid> 1000`：O 列持续 >85% + FGC 递增 → 内存泄漏画像（第 6 讲） |
| **Arthas** | 在线方法级诊断 | 不重启进程看参数 / 耗时链 / 类加载来源 |

```bash
# Arthas 三连（attach 到进程后）：
$ dashboard                  # 全景: 线程TOP/内存/GC 实时面板 —— 先看面
$ thread -n 5                # 最忙的 5 个线程的栈 —— 直接给"凶手行号"
[Busy Threads] Id=88 "http-nio-8080-exec-15" RUNNABLE, cpu 99.9%
    at com.demo.report.RegexValidator.check(RegexValidator.java:41)   # ← 定位到了
$ trace com.demo.OrderService detail                  # 方法内部耗时分解(火焰图的定点版)
`---ts=...-#164 method=detail duration=1834ms
    +---[15ms] orderRepo.find()
    `---[1800ms] priceClient.query()                 # ← 慢在下游调用
$ watch com.demo.PriceClient query '{params, returnObj}' -x 2   # 抓一次真实出入参复现脏数据
```

- **Arthas 纪律**：`watch` / `trace` 有开销且会打全量日志到终端——生产用完即 `stop` 退出，别常驻；`-n 3` 限制采样次数。

### 3. 火焰图

```bash
# async-profiler: 采样 30s 生成 CPU 火焰图(对目标进程 99 倍频采样, 开销 <1%)
./asprof -d 30 -f cpu.html <pid>
```

```text
怎么读(横宽 = 占样本比例, 纵高 = 调用深度):
  ┌────────────────────────────────────────────┐
  │              java.lang.Thread.run          │
  │  ┌──────────────┬───────────────┐          │
  │  │  GC 线程 35% │ OrderService 40% │       │  ← GC 占 1/3 宽度 = 分配速率过高(回查 new)
  │  └──────────────┴───────┬───────┘          │  ← 单个业务方法 40% = 里面必有慢段, 点开展开
  │                ┌────────┴────────┐         │
  │                │ RegexValidator 38% │      │  ← 热点收敛到一处: 优化目标出现
  │                └─────────────────┘         │
  └────────────────────────────────────────────┘
  目标: 找"最宽的板", 不是最高的塔; 优化前后两张图 = 绩效证据
```

### 4. 实战范式：线上 CPU 100%，五步定位到代码行

```bash
# 步骤 1: 定位进程
top -c                                  # 按 P 排序, 找到 %CPU 爆表的 pid

# 步骤 2: 定位线程
top -Hp <pid>                           # -H 展开线程列表; 记下最耗 CPU 的线程 TID(如 12345)

# 步骤 3: 线程号转十六进制
printf '%x\n' 12345                     # → 3039

# 步骤 4: 线程栈里找对应 nid
jstack <pid> | grep -A 30 'nid=0x3039'
#   "http-nio-8080-exec-15" #88 daemon prio=5 os_prio=0 tid=0x... nid=0x3039 runnable
#       at java.util.regex.Pattern$CurV...match(Pattern.java:4500)   ← 正则回溯!
#       at com.demo.report.RegexValidator.check(RegexValidator.java:41)
#       at com.demo.OrderController.export(OrderController.java:66)
#   （输出格式随 JDK 版本有差异——盯「线程名 / 状态 / nid」三要素即可，别背格式）

# 步骤 5: 判性质并止血
#   RUNNABLE + 栈顶业务代码 → 热点代码, 回滚/摘流量后修
#   GC 线程忙                → 是内存问题伪装成 CPU(第 6 讲 jstat)
#   RUNNABLE 但栈在 native  → 查 syscall(网络 read? 磁盘 io?)
```

- 常见结局速查：正则灾难性回溯（嵌套量词 `(a+)+b`）、死循环、GC 满负荷（其实是堆问题）、热点方法里的同步 IO、加密 / 序列化风暴。
- K8s 场景变体：CPU 100% 被 **throttling**（`container_cpu_cfs_throttled_periods` 指标高）≠ 真 CPU 不够——先对齐 requests/limits 与 JVM 参数（第 2 讲）。

### 5. 实战场景二：连接池耗尽（HikariCP）

- **现象**：日志刷 `HikariPool-1 - Connection is not available, request timed out`——请求全在等连接，不一定是 DB 挂了。
- **排查路径**：连接池 metrics 看活跃连接数与等待队列（`hikaricp_connections_active` 是否顶满 maximumPoolSize、`hikaricp_connections_pending` 排多长）→ 慢 SQL 拖住连接不归还 → 代码层连接 / 事务泄漏（未关闭、长事务）。

### 坑点提醒

- **jstack 要在「事发时」抓**：事后重启现场就没了——线上容器带 `kill -3 <pid>`（打印线程栈到标准输出）的肌肉记忆，比装完 Arthas 再 attach 快。
- **OOM dump 没留现场**：`-XX:+HeapDumpOnOutOfMemoryError` 必须在启动参数里（第 6 讲），事后 `jmap` 的 `-dump:live` 只抓得到活对象、还会触发一次 Full GC。
- **jstack 输出里 BLOCKED 很多 ≠ 死锁**：死锁是互相等（jstack 直接标注）；大量 BLOCKED 是锁竞争热点——顺着等锁栈找持锁者。
- **tcpdump 忘 -w 文件**：默认打终端，海量包把终端刷死——抓包落盘再分析；生产抓包加 BPF 过滤（host/port）缩小量。

## 本节自检

- [ ] 线上 Java 进程 CPU 100%，用 jstack / Arthas 怎么定位到代码行？（五步完整复述）
- [ ] 能说出 jstack / jmap / jstat / Arthas 各自回答什么问题
- [ ] 能读懂火焰图的横轴含义，说出「最宽的板」读图法
- [ ] 能区分「CPU 真高」「GC 伪装」「容器 throttling」三种 CPU 100% 现象与下一步
- [ ] 能用 ss / lsof 定位连接数异常，用 tcpdump 的过滤语法抓指定链路

## 本节配套思考题

1. 「load average 20 但 CPU 空闲 90%」——load 的计数里谁在充数（提示：D 状态），第一反应查哪两个命令？
2. 你的 jstack 显示所有 http 线程都在等 `HikariPool-1 - wait`——从这条线往下画一条完整排查链到根因（结合阶段六第 2 讲的瓶颈清单）。
3. Arthas `trace` 与火焰图都能找慢方法——什么时候选 trace（已知入口验证假设），什么时候选火焰图（无方向全局采样）？
