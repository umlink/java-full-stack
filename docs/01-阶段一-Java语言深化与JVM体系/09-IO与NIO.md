# 阶段一 · 小点 9：IO 与 NIO

> 所属：阶段一 Java 语言深化与 JVM 体系
> 定位：理解 NodeJS 事件循环与 Java NIO 的异同，为 WebFlux（阶段二第 4 讲）和 RPC 框架（阶段四第 5 讲）打基础。这一讲的关键不是「会写 NIO 代码」（业务层极少直接写），而是**看懂线程模型图**——出连接数 / 直接内存问题时能推理到这一层。

## 快速入门

> 本节为「IO 模型速览」：先认识几种 IO 模型的长什么样、为什么一个够一个不够；「零拷贝、Netty 三件套」留在正文提高部分。

### 本讲核心关键词速查

| 关键词 | 一句话大白话 | 例子 |
|-|-|-|
| IO | 程序读/写文件、网络、键盘等设备 | 读文件、发 HTTP |
| BIO | 阻塞 IO：一个线程等一个连接，等的时候干不了别的 | 老式 Socket |
| NIO | 非阻塞 IO：一个线程管多个连接，谁就绪处理谁 | `Selector` 多路复用 |
| 多路复用 | 一个线程同时监听多个连接的事件 | `select` / `epoll` |
| 零拷贝 | 数据不经过用户态多趟拷贝，直接到网卡 | `sendfile` |
| Netty | 基于 NIO 的高性能网络框架 | RPC / WebFlux 底层 |
| Channel / ByteBuf | Netty 的连接 / 数据缓冲区 | 网络编程基本件 |

### 本讲在解决什么问题

- **问题**：服务器要同时处理很多网络连接。如果用「一个连接一个线程」的阻塞模型，几千个连接就撑爆线程；NIO 用「一个线程管理多个连接」解决高并发连接。
- **你要带走的一句话**：**BIO 是一对一**（一个线程等一个连接，阻塞），**NIO 是一对多**（一个线程用 Selector 管多个连接，谁就绪处理谁）。这就是为什么高并发网络要用 NIO / Netty。

### 最简可运行示例（照抄能跑）

```java
// NIO 多路复用的骨架: 一个线程监听多个连接的就绪事件
import java.nio.channels.*;
import java.net.*;
import java.nio.*;

public class NioDemo {
    public static void main(String[] args) throws Exception {
        Selector selector = Selector.open();                 // 多路复用器
        ServerSocketChannel server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(8080));
        server.configureBlocking(false);                     // 关键: 非阻塞
        server.register(selector, SelectionKey.OP_ACCEPT);   // 只关心"有新连接"事件

        while (true) {
            selector.select();                               // 阻塞等, 有事件才继续
            for (var key : selector.selectedKeys()) {
                if (key.isAcceptable()) {
                    var conn = server.accept();              // 接受新连接
                    conn.configureBlocking(false);
                    conn.register(selector, SelectionKey.OP_READ);   // 该连接关心"可读"
                }
            }
            selector.selectedKeys().clear();
        }
    }
}
```

> 代码备注（逐行解释）：
> - `Selector.open()`：一个「多路复用器」，能同时管理多个 Channel 就绪事件。
> - `configureBlocking(false)`：**非阻塞**——读写不挂线程，没数据就立刻返回。
> - `register(selector, OP_ACCEPT/OP_READ)`：把连接「注册」到 Selector，声明我只关心「可接受/可读」事件。
> - `selector.select()`：阻塞等待，**只要有任一注册的事件就绪就返回**——一个线程就能管理成千上万个连接。
> - 对比 BIO：BIO 一个连接占一个线程，NIO 一个线程管所有连接——这就是「高并发连接」的答案。

### 关键概念说明

| 概念 | 干什么 | 最易踩的坑 |
|-|-|-|
| `Selector` | 多路复用器，监听多个 Channel 事件 | `select()` 后记得 `selectedKeys().clear()` |
| `ByteBuffer` | 数据缓冲区 | 读写要 `flip()` 切换读写模式，忘了会乱 |
| `FileChannel.transferTo` | 零拷贝发送 | 目标是 `WritableByteChannel`（不是 OutputStream） |
| 虚拟线程 + BIO | 同步 BIO + 虚拟线程已够多数业务 | 手工 NIO Selector 代码几乎不该再写（阶段一第 7 讲） |

### 常用约定 / 命名提示

- **业务层几乎不直接写 NIO**：NIO/Netty 是框架层的事，你更可能是「用 Spring WebFlux / Netty」的人——看懂模型即可，别手撸 Selector。
- **零拷贝的价值**：`transferTo` 底层走 `sendfile`，数据不进用户态，Kafka 高吞吐就靠它（阶段三第 5 讲）。
- **虚拟线程时代**：同步 BIO 写法 + 虚拟线程对多数业务已够用，不用为了「性能」硬上 NIO。

## 精简大纲

1. BIO / NIO / AIO 三代模型
2. 多路复用：select / poll / epoll
3. 零拷贝：sendfile / mmap
4. Netty 三件套：EventLoop / Channel / ByteBuf

## 学习内容详情

### 1. BIO / NIO / AIO 三代模型

| 模型 | 一句话 | 线程与连接的关系 |
|-|-|-|
| BIO（Blocking IO） | 一连接一线程，读写阻塞 | 连接数 = 线程数，连接多则线程爆炸 |
| NIO（Non-blocking IO） | 多路复用，少量线程管理大量连接 | 一个 Selector 可管成千上万连接 |
| AIO（Async IO） | 内核完成 IO 后回调通知 | Windows IOCP 完整，Linux 落地有限 |

#### 1.1 BIO 的困境（代码看到痛处）

```java
import java.io.*;
import java.net.*;

public class BioServer {
    public static void main(String[] args) throws IOException {
        var server = new ServerSocket(8080);
        while (true) {
            Socket client = server.accept();      // 阻塞点①: 没有新连接就干等
            // 每个连接必须开一个线程 —— 因为 read 也会阻塞, 会把 accept 卡死
            new Thread(() -> {
                try (client;
                     var in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                     var out = new PrintWriter(client.getOutputStream(), true)) {
                    String line;
                    while ((line = in.readLine()) != null) {   // 阻塞点②: 没数据就挂在这
                        out.println("echo: " + line);
                    }
                } catch (IOException ignored) {}
            }).start();                              // 1 万个连接 = 1 万个线程 ≈ 1万MB 栈内存
        }
    }
}
```

> **痛点本质**：阻塞的是**线程**，而平台线程是昂贵资源（约 1MB 栈 + 内核调度实体）。BIO 的解法只有两条路：让线程变廉价（虚拟线程），或者让一个线程能管多个连接（NIO）。

### 2. 多路复用：select / poll / epoll

- **多路复用（Multiplexing）**：把「一堆连接谁就绪了」的询问交给内核，线程只处理「就绪的连接」——NodeJS 的事件循环（libuv）在 Linux 上就是构建于 epoll 之上，机制同源。

```java
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;

public class NioEchoServer {
    public static void main(String[] args) throws IOException {
        var selector = Selector.open();                       // 多路复用器(底层 Linux=epoll)
        var server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(8080));
        server.configureBlocking(false);                      // 非阻塞: 所有调用立即返回
        server.register(selector, SelectionKey.OP_ACCEPT);    // 把"关心 ACCEPT 事件"登记到 selector

        while (true) {
            selector.select();                                // 阻塞到"至少一个事件就绪"(只等一次)
            Iterator<SelectionKey> it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = it.next();
                it.remove();                                  // 必须手动移除, 否则下轮重复处理
                if (key.isAcceptable()) {                     // 新连接
                    SocketChannel ch = server.accept();
                    ch.configureBlocking(false);
                    ch.register(selector, SelectionKey.OP_READ);   // 登记读事件 —— 一个线程管 N 个连接
                } else if (key.isReadable()) {                // 某连接有数据可读
                    SocketChannel ch = (SocketChannel) key.channel();
                    ByteBuffer buf = ByteBuffer.allocate(256);
                    int n = ch.read(buf);
                    if (n == -1) { ch.close(); continue; }    // 对端关闭
                    buf.flip();                               // 写转读模式: limit=position, position=0
                    ch.write(buf);                            // echo 回去
                }
            }
        }
    }
}
```

> **对比上面 BIO 版**：单线程处理所有连接——`select()` 一次醒来，返回的只有就绪的连接（epoll 的功劳），没有线程在无谓地等。Tomcat NIO / Netty / Redis 的事件模型全部是这个循环的工程化强化版。

#### 2.1 select / poll / epoll 对比

| 维度 | select / poll | epoll（Linux） |
|-|-|-|
| fd 传递 | 每次调用全量拷进内核 | 注册一次，内核维护红黑树 |
| 就绪检测 | 内核线性扫描全部 fd | 事件回调填就绪链表，只返回就绪的 |
| 复杂度 | O(n) | O(1) 事件通知 |
| 连接数上限 | select 有 FD_SETSIZE 限制 | 无硬上限（受内存约束） |

- macOS 用 kqueue、Windows 用 IOCP——Netty 这类跨平台框架的存在意义就是抹平这层差异。

### 3. 零拷贝

- **零拷贝（Zero-Copy）**：让文件数据从磁盘直达网卡、不经过用户态——传统路径要 4 次拷贝 + 4 次上下文切换，`sendfile` 把中间两次砍掉。

```java
import java.io.FileInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class ZeroCopySend {
    public static void main(String[] args) throws IOException {
        var server = ServerSocketChannel.open();
        server.bind(new InetSocketAddress(9090));
        try (SocketChannel socket = server.accept();   // SocketChannel 实现了 WritableByteChannel
             FileChannel file = new FileInputStream("big-file.bin").getChannel()) {
            // transferTo 底层即 Linux sendfile: 文件 → 网卡, 数据不进用户态
            // Kafka 消息发送、Nginx sendfile、静态文件下发全是这条路
            long sent = 0, total = file.size();
            while (sent < total) {
                sent += file.transferTo(sent, total - sent, socket);  // 目标是 WritableByteChannel, 不是 OutputStream
            }
        }
    }
}
```

> 注：`transferTo` 的第三个参数是 `WritableByteChannel`——`SocketChannel` 正是它的实现（把 `OutputStream` 传进去连编译都过不了）。认知重点：**Kafka 的高吞吐 = 顺序写磁盘 + sendfile 零拷贝发送**（阶段三第 5 讲回收此伏笔）。

### 4. Netty 三件套

- **Netty**：对 NIO 的工程化封装（解决空轮询 bug、内存池、粘包拆包）——Dubbo / gRPC-Java / ES 客户端的通信底座。业务开发不直接写它，但连接数 / 直接内存问题要能推理到这层。

```java
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class NettyEchoServer {
    public static void main(String[] args) throws Exception {
        // EventLoopGroup: 一组线程各自持有 Selector; 连接绑定到固定 EventLoop → 无锁
        var boss = new NioEventLoopGroup(1);      // 只管 accept(类似 NioEchoServer 的主循环)
        var workers = new NioEventLoopGroup();    // 管已建立连接的读写(默认 2×CPU 核)

        try {
            var b = new ServerBootstrap();
            b.group(boss, workers)
             .channel(NioServerSocketChannel.class)
             .childHandler(new ChannelInitializer<SocketChannel>() {
                 @Override
                 protected void initChannel(SocketChannel ch) {
                     ch.pipeline()                 // pipeline: 一串 handler 的责任链(≈ Express/Koa 中间件)
                      .addLast(new StringDecoder())     // 入站: 字节 → String(解决粘包的编解码层)
                      .addLast(new StringEncoder())     // 出站: String → 字节
                      .addLast(new ChannelInboundHandlerAdapter() {
                          @Override
                          public void channelRead(ChannelHandlerContext ctx, Object msg) {
                              ctx.writeAndReturn(msg);   // echo: 写回会沿出站方向过 Encoder
                          }
                      });
                 }
             });
            ChannelFuture f = b.bind(8080).sync();   // 启动完成前阻塞等待
            f.channel().closeFuture().sync();        // 保持服务运行
        } finally {
            boss.shutdownGracefully();
            workers.shutdownGracefully();
        }
    }
}
```

- **EventLoop**：事件循环线程，一个连接从生到死只归属一个 EventLoop（免锁竞争）。与 Node 单线程事件循环的差异：Netty 是**多 EventLoop 并行**，每个仍是单线程循环。
- **Channel**：连接抽象（fd + 读写操作 + pipeline）。
- **ByteBuf**：读写指针分离的字节容器——`ByteBuffer` 的 `flip()` 忘调是经典事故（上面 NIO 示例里 flip 那行就是），ByteBuf 用 `readIndex` / `writeIndex` 两个指针消灭了这个心智负担，并支持池化（减少分配与 GC）。
- **编解码的字符集坑**：`StringDecoder` / `StringEncoder` 不传参数时默认字符集是 ISO-8859-1——中文必须显式传 `CharsetUtil.UTF_8`（如 `new StringDecoder(CharsetUtil.UTF_8)`），中文乱码十有八九栽在这。

### 坑点提醒

- `ByteBuffer` 忘 `flip()`：写出全空或读到脏数据——翻转前 limit 还是旧容量位。
- NIO 不等于异步：`select()` 仍然是阻塞等待，只是等待的粒度从「单个连接」变成「一批事件」。
- 虚拟线程时代 BIO 写法复活了：`同步 BIO 代码 + 虚拟线程` 对多数业务已够用（阶段一第 7 讲）——NIO 的手工 Selector 代码几乎不该再手写。
- Netty 的堆外直接内存要 `-XX:MaxDirectMemorySize` 兜底，不然泄漏时表现成「堆很健康但进程 OOM 被杀」。

## 本节自检

- [ ] 能画出 BIO 与 NIO（Reactor）的线程模型对比图，说清「谁在阻塞、阻塞多久」
- [ ] 能解释 epoll 相对 select 的两个核心优势，并说出 NodeJS 事件循环与它的关系
- [ ] 能描述零拷贝省掉了哪几次拷贝，举出两个使用它的中间件
- [ ] 能说清 Netty 的 EventLoop / Channel / ByteBuf 各自解决什么问题

## 本节配套思考题

1. BIO + 虚拟线程（`Executors.newVirtualThreadPerTaskExecutor()` 里跑 accept 循环）和 NIO Selector，各自适合什么规模 / 什么维护成本偏好的团队？
2. 为什么 Netty 把「一个连接固定绑定一个 EventLoop」而不是让任意 EventLoop 处理任意连接的事件？（提示：pipeline 里的 handler 是否需要加锁？）
3. `mmap` 与 `sendfile` 都是零拷贝路径，为什么消息队列类中间件（Kafka）偏爱 sendfile，而 RocksDB 这类存储引擎偏爱 mmap？
