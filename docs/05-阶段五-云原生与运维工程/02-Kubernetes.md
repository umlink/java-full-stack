# 阶段五 · 小点 2：Kubernetes

> 所属：阶段五 云原生与运维工程
> 定位：企业级部署的事实标准。目标不是「会背对象清单」，而是掌握三件事：核心对象的心智模型、Spring Boot 上 K8s 的适配点、以及一套背下来的故障排查路径。

## 快速入门

> 本节为「K8s 速览」：先认识 K8s 是什么、解决什么问题、几个核心对象；「网络边界、发布策略」留在正文提高部分。

### 本讲核心关键词速查

| 关键词 | 一句话大白话 | 例子 |
|-|-|-|
| Kubernetes（K8s） | 容器编排平台（管理大量容器） | 自动部署、扩缩、自愈 |
| Pod | K8s 最小运行单元（一个或多个容器） | 一个应用实例 |
| Deployment | 管理 Pod 副本（部署清单） | 声明要几个副本 |
| Service | 稳定的访问入口 | 负载均衡到 Pod |
| Ingress | 外部访问的入口（L7 路由） | 域名→Service |
| 探针 | 健康检查（liveness/readiness/startup） | 判断容器活没活/就绪没 |
| 滚动发布 | 逐个替换 Pod 更新 | 不中断服务升级 |
| HPA | 自动扩缩容 | 按 CPU 加副本 |

### 本讲在解决什么问题

- **问题**：容器多了不好管——谁来调度、扩缩、自愈、更新？K8s 负责编排：声明「要什么」，它自动让集群变成那个样子。
- **你要带走的一句话**：**Pod** 是最小运行单元，**Deployment** 管副本，**Service** 提供稳定入口，**Ingress** 对外路由。核心是「声明期望状态，K8s 自动收敛」——你写 YAML 描述"要什么"，K8s 负责"变成那样"。

### 最简可运行示例（照抄能跑）

```yaml
# Deployment: 声明这个应用要有 3 个副本, 用哪个镜像, 探针怎么探
apiVersion: apps/v1
kind: Deployment
metadata:
  name: my-app
spec:
  replicas: 3                       # 期望 3 个副本
  selector: { matchLabels: { app: my-app } }
  template:
    metadata: { labels: { app: my-app } }
    spec:
      containers:
        - name: app
          image: registry/my-app:1.0
          ports: [ { containerPort: 8080 } ]
          startupProbe:   # 慢启动保护: 启动期别掐
            httpGet: { path: /actuator/health, port: 8080 }
            initialDelaySeconds: 15
          readinessProbe: # 就绪才接流量
            httpGet: { path: /actuator/health, port: 8080 }
          livenessProbe:  # 挂了重启容器
            httpGet: { path: /actuator/health, port: 8080 }
```

> 代码备注（逐行解释）：
> - `replicas: 3`：期望 3 个副本，K8s 会保证始终有这么多个在跑。
> - `template`：定义每个 Pod 用哪个镜像、端口、探针。
> - **三种探针**：`startupProbe`（慢启动保护）、`readinessProbe`（就绪才接流量）、`livenessProbe`（挂了重启）——这是 Spring Boot 上 K8s 最关键的适配点（用 Actuator 的 `/actuator/health`）。
> - **声明式**：你只写"要 3 个、健康才接流量、挂了重启"，剩下的 K8s 自动调度——这就是「编排」的核心。

### 关键概念说明

| 概念 | 干什么 | 最易踩的坑 |
|-|-|-|
| Pod | 最小运行单元 | 一个 Pod 通常一个主容器 |
| Deployment | 管理副本 | 声明式：要几个给几个 |
| Service | 稳定入口 | 负载均衡到 Pod |
| Ingress | L7 路由 | ingressClassName 指向集群控制器 |
| 三种探针 | 健康检查 | startup/readiness/liveness 别混用 |
| 滚动发布 | 逐个替换 | 配 preStop + gracePeriod 保优雅停机 |
| HPA | 自动扩缩 | 阈值要和容量规划对齐（阶段六第 6 讲） |

### 常用约定 / 命名提示

- **Spring Boot on K8s 适配**：探针用 Actuator 的 `/actuator/health`；优雅停机配 `preStop` + `gracePeriodSeconds`；容器资源写 request/limit。
- **故障排查三步**：先看状态（`kubectl get pods`）→ 看事件和日志（`describe`/`logs`）→ 定位是 CrashLoopBackOff/Pending/Service 不通。
- **安全基线**：K8s 网络默认全通，东西向要显式声明 NetworkPolicy；生产用 Pod Security（restricted）。

## 精简大纲

1. 从 Compose 到 K8s：为什么需要「编排」
2. 核心对象与第一个完整部署（Deployment + Service YAML 精读）
3. 网络三层边界：Service / Ingress / Gateway
4. 发布策略：滚动 / 蓝绿 / 金丝雀
5. Spring Boot on K8s 适配清单：探针 / 优雅停机 / 资源 / HPA
6. Helm 与故障排查路径
7. 安全基线：NetworkPolicy / PSS / 资源配额

## 学习内容详情

### 1. 从 Compose 到 K8s：为什么需要「编排」

- **Compose 管「一台机器上的多容器」**；**K8s 管「一堆机器上的大量容器」**：谁挂在哪台机器、挂了谁顶上、流量怎么找到它、发布怎么不中断——这四件事就是「编排」。

> 🧩 前置 30 秒：**声明式 vs 命令式**——生活版：你是来「点菜」还是来「给厨师一步步下指令」？点菜只说要什么结果，后厨自己切配、下锅、装盘；回到 K8s：你 `kubectl apply` 提交一张「订单」（YAML 期望状态），编排系统自己让集群变成那个样子。前端心智锚点就是 React：声明 UI 长什么样，框架负责 diff 并更新 DOM。

- **声明式 API**：你提交「期望状态」（YAML），K8s 的**控制器（Controller）**通过**调谐循环（Reconcile Loop）**持续把实际状态向期望收敛——类比 React 的 `setState`。控制回路长这样：

```mermaid
flowchart TD
    A["期望状态：Deployment 说『要 3 个副本』"] --> C["调谐循环 Reconcile Loop<br/>持续比对期望 vs 实际"]
    B["实际状态：现在只有 1 个 Pod 在跑"] --> C
    C -->|"有差距？补一个"| D["ReplicaSet 创建新 Pod"]
    D --> E["节点上的 kubelet<br/>拉镜像、起容器"]
    E --> B
    C -->|"没差距？躺平等差变"| B
```

- **排障心法**：别问「我改了什么」，问「期望状态和实际状态差在哪」。
- **控制器是「带眼睛的循环」而不是一次性执行**：Pod 被杀它再拉起、节点挂了它把副本调度到别处——这就是「自愈」的底层机制。

### 2. 核心对象与第一个完整部署

> **先看全景：一个 K8s 集群由谁组成？** 分「控制面」（做决策的大脑）与「数据面」（干活的节点），各组件各司其职：

```mermaid
flowchart TD
    K["你在终端敲 kubectl apply"] --> A["kube-apiserver<br/>总入口：收 YAML、校验、落库"]
    A --> E["etcd<br/>集群状态的『唯一记账本』"]
    A --> S["kube-scheduler<br/>选节点：新 Pod 住哪台机器<br/>（看资源够不够）"]
    A --> C["kube-controller-manager<br/>管家：盯着「期望 vs 实际」，差了就补"]
    N["每台节点上的 kubelet<br/>手脚：真正拉镜像、起/停容器"] --> A
    S --> N
    C --> N
```

| 类别 | 对象 | 职责 |
|-|-|-|
| 工作负载 | Pod / Deployment / StatefulSet / DaemonSet / Job / CronJob | 运行单元与副本管理 |
| 服务发现 | Service / Ingress | 稳定访问入口 / 七层路由 |
| 配置存储 | ConfigMap / Secret / PV / PVC | 配置注入与持久卷 |

- **Pod**：最小调度单元 = 一个或一组共生容器（共享网络与存储）——生活版：一个 Pod 就像「合租的一套房」，里面的容器共享水电（网络）和楼道门禁（探针），要么一起住、要么一起搬。
- **Deployment → ReplicaSet → Pod**：Deployment 不直接管 Pod——副本数量交给它创建的 **ReplicaSet（副本控制器）** 去保证，Deployment 只负责版本演进（这三层关系是面试高频）。
- **StatefulSet**：管有状态服务（数据库类，稳定网络标识 + 顺序伸缩）。
- **DaemonSet**：每节点跑一个（日志采集器）。
- **Job / CronJob**：跑一次性 / 周期任务。
>
> ⏸️ **短期可以不学**：StatefulSet 的深入运维（扩缩容、备份恢复）只在自运维数据库 / 中间件时才需要——多数公司直接用云托管（RDS 等），主线理解概念即可。**何时回来学**：你需要自运维有状态服务、或面试目标岗位明确做中间件自研时。**面试最低要求**：能说出 StatefulSet 与 Deployment 的三个差异（稳定网络标识 / 顺序伸缩 / 独立持久卷）。

```yaml
# deployment.yaml：Spring Boot 服务的完整生产级声明（逐段注释）
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
  labels: { app: order-service }        # 标签是 K8s 一切"选中"机制的基础
spec:
  replicas: 3                            # 期望副本数（HPA 会改这个字段）
  selector:
    matchLabels: { app: order-service }  # Deployment 认领 Pod 的方式：按标签匹配
  strategy:
    rollingUpdate:
      maxSurge: 1                        # 滚动时最多多起 1 个新 Pod（控制峰值资源）
      maxUnavailable: 0                  # 滚动时最少可用数不减（零中断发布的关键）
  template:                              # Pod 模板：从这里开始描述"每个 Pod 长什么样"
    metadata:
      labels: { app: order-service }     # 必须匹配上面的 selector，否则认领失败
    spec:
      containers:
        - name: order
          image: harbor.local/apps/order-service@sha256:abc123   # 用 digest 锁定版本
          ports: [ { containerPort: 8080 } ]
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: "prod"
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:            # 密钥从 Secret 注入，不进镜像也不进 YAML 明文
                  name: order-db-cred
                  key: password
          resources:
            requests:                    # 调度依据：声明"我至少要多少"，调度器按此选节点
              cpu: 500m                  # 500m = 0.5 核
              memory: 1Gi
            limits:                      # 上限：超内存 → OOM Kill；超 CPU → 限流(不杀)
              cpu: "1"
              memory: 1536Mi
          startupProbe:                  # 启动保护：Java 启动慢，先等它起来再谈存活
            httpGet: { path: /actuator/health/liveness, port: 8080 }
            failureThreshold: 30         # 最多容忍 30 次失败(30×2s=60s 启动窗口)
            periodSeconds: 2
          livenessProbe:                 # 存活：失败就重启容器（治"假死"，如死锁）
            httpGet: { path: /actuator/health/liveness, port: 8080 }
            periodSeconds: 10
          readinessProbe:                # 就绪：失败就摘流量（治"忙不过来/依赖故障"）
            httpGet: { path: /actuator/health/readiness, port: 8080 }
            periodSeconds: 5
          lifecycle:
            preStop:                     # 优雅停机前半段：见第 5 节详解
              exec: { command: ["sleep", "10"] }
```

```yaml
# service.yaml：给这组 Pod 一个稳定入口
apiVersion: v1
kind: Service
metadata:
  name: order-service
spec:
  selector:
    app: order-service                   # 按标签选中所有同名 Pod，流量自动负载均衡
  ports:
    - port: 80                           # 集群内访问端口
      targetPort: 8080                   # 转发到容器端口
---
# ingress.yaml：让集群外部的 HTTP 流量进来（七层路由）
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: app-ingress
spec:
  ingressClassName: nginx               # nginx 只是常见选项之一（traefik、contour 等）——ingressClassName 指向的是集群里装的控制器
  rules:
    - host: api.example.com
      http:
        paths:
          - path: /orders                # 路径级路由：订单流量 → order-service
            pathType: Prefix
            backend: { service: { name: order-service, port: { number: 80 } } }
          - path: /                      # 其余 → 网关
            pathType: Prefix
            backend: { service: { name: gateway, port: { number: 80 } } }
```

> 上面的规则最终由「Ingress Controller」执行：它运行在集群里、监听 Ingress 资源并生成代理配置，请求按 host / 路径命中哪条规则就转到对应的 Service：

```mermaid
flowchart LR
    U["浏览器输入<br/>api.example.com/orders"] --> D1["DNS 解析到集群入口"] --> IC["Ingress Controller<br/>（如 nginx-ingress）"]
    IC -->|"匹配 host + 前缀 /orders"| S1["order-service 的 Service"]
    S1 --> P1["order-service 的 Pod"]
    IC -->|"其余路径"| S2["gateway 的 Service"]
    S2 --> P2["业务网关再转发"]
```

### 3. 网络三层边界（高频考点）

```mermaid
flowchart LR
    U[外部流量] --> I[Ingress Controller<br/>L7: host/路径路由]
    I --> G[Spring Cloud Gateway<br/>业务层: 鉴权/灰度/限流]
    G --> S[Service<br/>L4: 稳定虚拟IP+负载均衡]
    S --> P[Pod]
```

| 层 | 解决的问题 | 不解决的问题 |
|-|-|-|
| **Service** | 「集群内怎么找到一组会生会死的 Pod」——稳定虚拟 IP + 自动负载均衡 | 不懂 HTTP 路径/Header |
| **Ingress** | 「外部 HTTP(S) 流量怎么路由到哪个 Service」——七层规则、TLS 终止 | 不做业务鉴权、不做灰度策略 |
| **Gateway** | 「业务级路由」——统一鉴权、按 Header/权重灰度、业务限流 | 不直接暴露给公网（通常藏在 Ingress 后面） |

> 🧩 前置 30 秒：**Service 发现 = 公司前台总机**——生活版：你找某个人不记他手机号，拨「总机」转接，人换了工位总机号不变；回到 K8s：Pod 生生死死、IP 换来换去，Service 就是这台永远不变的总机号，其他服务只要记 Service 名即可。

- Service 三种类型：`ClusterIP`（默认，仅集群内）/ `NodePort`（每节点开一个端口）/ `LoadBalancer`（云厂商 SLB）。
- **为什么 Service 的 IP 是「虚拟」的**：它不挂在任何网卡上，由每台节点上的 kube-proxy（流量转发代理）写入 iptables / IPVS 转发规则——Pod 的 IP 随生随死，这个 VIP 永远不变。转发链路：

```mermaid
flowchart LR
    A["客户端访问 Service 的虚拟 IP<br/>（服务名:80）"] --> P["kube-proxy 写入的转发规则<br/>（iptables / IPVS）"]
    P --> B["Pod1<br/>192.168.1.10:8080"]
    P --> C["Pod2<br/>192.168.1.11:8080"]
    B -->|"Pod1 挂了被换"| X["新 Pod 换个 IP 上线<br/>VIP 不变、规则自动跟着改"]
```

> ⏸️ **短期可以不学**：Gateway API（Ingress 的下一代标准，由 Kubernetes SIG 维护，按角色分层、支持多集群与策略组合）已进入主流视野，但存量集群迁移需要时间。**何时回来学**：团队开始引入 Gateway API、或你要做多集群统一入口时。**面试最低要求**：一句话——「Gateway API 是 Ingress 的继任标准，解决 Ingress 的资源角色混杂与扩展性瓶颈」。

### 4. 发布策略

- **滚动更新**：默认策略——新 Pod 逐个起、旧 Pod 逐个停，`maxSurge/maxUnavailable` 控制节奏；`kubectl rollout undo` 一键回滚。
- **蓝绿**：新旧两套全量并存，流量一次性切换——回滚最快、资源翻倍。
- **金丝雀**：Argo Rollouts 按流量比例渐进（1% → 10% → 50% → 100%），每一步自动分析指标，异常自动回退——「滚动 + 权重 + 自动分析」的组合体。

> 其中滚动更新是默认策略，`maxSurge / maxUnavailable` 就是它的「保险丝」。「新起旧停、逐个替换」的时序如下：

```mermaid
sequenceDiagram
    participant U as 用户流量
    participant S as Service（endpoints 视角）
    participant N as 新 ReplicaSet
    participant O as 旧 ReplicaSet
    loop 每一轮替换，直到新全起旧全停
        N->>N: 起一个新 Pod（maxSurge 允许）
        N->>S: 新 Pod 通过 readiness 探针，进 endpoints
        S-->>U: 流量开始切到新 Pod
        S->>O: 停一个旧 Pod（maxUnavailable 允许）
    end
    Note over S: 回滚 = kubectl rollout undo<br/>把「期望状态」改回旧版本，再走一遍同样的时序
```

### 5. Spring Boot on K8s 适配清单

#### 5.1 探针语义差异（配错就是事故）

> 🧩 前置 30 秒：**探针（Probe）= 定时体检**——生活版：保安看「工牌」判断你这秒能不能进大厦（readiness），但工牌过期不该把大楼拆了重盖，只有急救医生摸「脉搏」没了才抢救（liveness）；回到 K8s：kubelet 定时向 `/actuator/health` 发 HTTP「问一句」——问诊失败，处理力度完全不同，见下。

- **liveness = 「要不要重启你」**：只该检查进程自身健康（死锁、假死）。
- **readiness = 「要不要给你流量」**：可以检查依赖（DB / 下游），依赖抖动 → 摘流量等恢复，进程不该被重启。
- **startup = 「启动慢的应用先别测我」**：Java 应用冷启动 + 预热期的保护。

> **经典事故**：liveness 探针配到了依赖下游的接口上——下游抖动 → liveness 失败 → K8s 认为「进程坏了」**把容器反复重启** → 重启期间服务全不可用 → 把「下游部分故障」放大成「本服务全灭」。依赖检查只能给 readiness。

#### 5.2 优雅停机：在途请求不丢的双保险

Pod 被杀时的时序：K8s 先发 `SIGTERM` →（同时）Endpoint 从 Service 摘除。**问题**：摘除是异步的，可能 SIGTERM 都到了、流量还在往这个 Pod 打。双保险：

```yaml
# 保单一（YAML 侧）：preStop 拖时间 —— 等 Endpoint 传播完成再开始停
lifecycle:
  preStop:
    exec: { command: ["sleep", "10"] }   # 拖住 10s：这期间摘流量已传播完，不会再有新请求
```

```yaml
# 保单二（应用侧）：graceful shutdown —— 收到 SIGTERM 后，处理完在途请求再退出
# application.yml
server:
  shutdown: graceful                      # SIGTERM 后不再接新请求，等存量请求完成
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s       # 最多等 30s，超过强制退出
```

- **规则**：`preStop` 与 `graceful` 必须成对，缺一不可。
- **例外（只配一个必出事）**：只有 preStop 没有 graceful → 拖够了时间但停机瞬间仍有在途请求被切；只有 graceful 没有 preStop → 摘流量还没传播就停机，新请求照样撞死。

#### 5.3 资源对齐与 HPA

```yaml
# hpa.yaml：按 CPU 自动扩缩（指标来自 Metrics Server）
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: order-service
spec:
  scaleTargetRef: { apiVersion: apps/v1, kind: Deployment, name: order-service }
  minReplicas: 3
  maxReplicas: 20                         # 上限 = 容量规划结论(阶段六第 6 讲)
  metrics:
    - type: Resource
      resource:
        name: cpu
        target: { type: Utilization, averageUtilization: 65 }  # 均值超 65% 扩容
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300     # 缩容保守：5 分钟窗口内不连续缩，防抖动
```

- JVM 内存与 cgroup 对齐：容器 limits 给 1536Mi，JVM 用 `-XX:MaxRAMPercentage=75.0`（堆约占 1.1G，余量留给元空间 / 线程栈 / 直接内存）——不配的话 JVM 可能按宿主机内存自作主张，直接被 OOM Kill。

### 6. Helm 与故障排查路径

**Helm**：K8s manifest 的模板引擎——`{{ .Values.image.tag }}` 占位 + values 文件分环境注入，类比前端的「同一套组件、不同环境变量构建不同产物」。

```yaml
# values-prod.yaml：只覆盖与默认值不同的键
image:
  tag: "1.4.2"
replicas: 5
resources:
  limits: { cpu: "1", memory: 1536Mi }

# 用法：helm upgrade --install order ./charts/order -f values-prod.yaml -n prod
```

**故障排查路径**（三个场景背下来）：

```bash
# 场景一：Pod 一直 Pending（调度失败 —— 资源不足 / 亲和性 / PV 未绑定）
kubectl describe pod order-service-7d9f-xyz | tail -20
# Events 输出示例（注释即预期）：
#   Warning  FailedScheduling  ...  0/5 nodes are available:
#   5 Insufficient cpu.                     ← requests 加起来没有节点能满足 → 扩节点或降 requests
#   (或) 1 node(s) had untolerated taint... ← 污点（Taint）/ 容忍（Toleration）、亲和性（Affinity）问题 → 检查 nodeSelector/tolerations
#   (或) 2 pod has unbound immediate PersistentVolumeClaims  ← PVC 没绑上 PV

# 场景二：CrashLoopBackOff（容器反复崩溃）
kubectl logs order-service-7d9f-xyz --previous      # --previous: 看上一次崩溃的日志(关键!)
kubectl logs order-service-7d9f-xyz --previous | tail -30
# 常见结局: ① 启动报连接 DB 失败(配置/Secret) ② OOMKilled(见 describe 里 Reason) ③ 抛异常退出
kubectl get pod order-service-7d9f-xyz -o jsonpath='{.lastState.terminated.reason}'
# > OOMKilled   ← limits 内存给小了或 JVM 堆超配

# 场景三：Service 不通（Pod 活着但访问 502/超时）
kubectl get endpoints order-service
# > order-service   <none>       ← endpoints 为空! Service 的 selector 与 Pod label 不匹配
kubectl get pod -l app=order-service --show-labels    # 核对 Pod 实际标签拼写
# endpoints 非空仍不通 → 检查 targetPort(8080) 是否等于容器真实端口
```

### 7. 安全基线

- **NetworkPolicy**：K8s 网络默认全通，东西向流量（服务间）要显式声明放行——别裸奔。
- **Pod Security Standards**：privileged / baseline / restricted 三档，生产用 restricted。
- **ResourceQuota / LimitRange**：命名空间（Namespace）的资源配额与默认 request/limit——防单个服务吃光集群。

> ⏸️ **短期可以不学**：Operator 开发（用自定义控制器 + CRD 把「运维专家的操作经验」编码成自动化，如 etcd-operator / prometheus-operator）是平台工程方向的能力，业务开发主线不碰。**何时回来学**：你开始做平台工程、或要自研中间件管理时。**面试最低要求**：一句话——「Operator = 自定义控制器 + CRD，把人工运维动作自动化」。

### 坑点提醒

- **liveness 探依赖接口**：下游故障被放大成自我重启风暴（见 5.1，生产事故 Top 级）。
- **镜像用 `latest`**：无法回滚到「上一个真正跑过的版本」，且缓存导致「改了不生效」的错觉。
- **requests 与 limits 不设**：BestEffort 调度 → 资源争抢时最先被驱逐的就是你。
- **只有 graceful 没有 preStop**（或反过来）：滚动更新仍有少量 502，见 5.2 的双保险论证。
- **HPA 缩容太快**：流量抖动引发「扩了又缩、缩了又扩」，务必配 `stabilizationWindowSeconds`。

## 本节自检

- [ ] Pod 一直 Pending，你的排查步骤是什么？（describe → events → 三类原因定位）
- [ ] liveness 探针配到了一个依赖下游的接口上，会发生什么事故？能完整讲一遍放大链路
- [ ] Service、Ingress、Spring Cloud Gateway 三层各自解决什么问题？
- [ ] 滚动更新时怎么保证在途请求不丢？（preStop + graceful 双保险，能说清缺一不可的原因）
- [ ] 能解释 requests 与 limits 的差异（调度依据 vs 运行上限）以及 OOMKilled 的由来

## 本节配套思考题

1. 你的 Deployment replicas=3，但 `kubectl get endpoints` 显示只有 2 个地址，列出至少 3 种可能原因。
2. 为什么「liveness 失败 → 重启容器」在分布式系统里是危险操作？什么情况下重启反而是唯一解药？
3. 金丝雀发布相比蓝绿，牺牲了什么换来了什么？什么业务绝不能上金丝雀？

## 常见面试题

### Q1：Kubernetes 的核心组件有哪些？一个新 Pod 从提交到运行的完整流程？
**答**：
- **标准结论**：
  - 控制面四件套：kube-apiserver（所有操作的入口与唯一事实源）、etcd（存储集群状态）、kube-scheduler（选节点）、kube-controller-manager（运行各类控制器）。
  - 数据面：每台节点上的 kubelet（Pod 生命周期的执行者）与 kube-proxy（Service 转发规则）。
  - 流程：`kubectl apply` → apiserver 校验并写入 etcd → scheduler 按资源 / 亲和性选出节点 → kubelet 拉镜像起容器 → 探针与就绪状态回写。
- **底层原理**：
  - 核心是声明式 + 调谐循环——Deployment 控制器发现「期望 3 副本、实际 1 个」→ 创建 ReplicaSet → ReplicaSet 创建 Pod → scheduler 绑定节点 → kubelet 干活，每层只关心「期望 vs 实际差多少」。
  - 一致性基础：etcd 是唯一事实源、apiserver 是唯一入口、组件间不直接通信——这是集群一致性与可扩展性的根基。
- **工程实践**：
  - 排障按链分层看：`kubectl get events` 看调度、`logs` 看容器、`describe` 看生命周期。
  - Pod 卡 Pending 大概率调度层（资源不足 / 污点 / PV 未绑）；CrashLoopBackOff 大概率应用层（配置、依赖、OOM）。
  - 加分：能说出 controller-manager 里住着 Deployment / ReplicaSet / Endpoint 等一堆控制器。

### Q2：Service 和 Ingress 有什么区别？
**答**：
- **标准结论**：
  - Service 是集群内部的稳定访问入口——用标签选择器选中一组 Pod，提供虚拟 IP + 负载均衡。
  - Ingress 是集群外部 HTTP(S) 流量进集群的入口，按域名 / 路径把请求路由到不同 Service。注意 Ingress 只是规则，真正干活的是 Ingress Controller。
- **底层原理**：
  - 各层机制：Service 的虚拟 IP 由 kube-proxy 通过 iptables / IPVS 写入每台节点，本质是 L4 转发（不解析 HTTP）；Ingress Controller（如 nginx-ingress）是跑在集群里的反向代理，监听 Ingress 资源并生成代理配置，做 L7 路由与 TLS 终止。
  - 为什么分两层：Pod IP 会生会死，需要 Service 兜底；外部流量形态多样（域名、路径、TLS），需要 L7 层接住。
- **工程实践**：
  - 集群内调用（如 Gateway → 业务服务）用 Service（ClusterIP）；对外只暴露一层 Ingress，别为每个服务开 NodePort / LoadBalancer。
  - 业务鉴权、灰度放业务网关（Spring Cloud Gateway），Ingress 只做流量入口。
  - 加分：说出 Service 三种类型 +「Ingress 是 L7、Service 默认 L4」的分层结论。

### Q3：Deployment 滚动更新是怎么实现的？如何回滚？
**答**：
- **标准结论**：
  - 滚动更新是默认发布策略——新 ReplicaSet 逐步扩容、旧 ReplicaSet 逐步缩容，`maxSurge` / `maxUnavailable` 控制节奏。
  - 回滚用 `kubectl rollout undo`，Deployment 回到上一个 ReplicaSet。
- **底层原理**：
  - 机制：Deployment 不直接管 Pod，每次更新它创建一个新 ReplicaSet，通过两个「保险丝」控制速度——`maxSurge` = 最多多起几个新 Pod（峰值资源上限），`maxUnavailable` = 最多允许几个旧 Pod 不可用（可用性下限）。
  - 切换过程：滚动时新旧 ReplicaSet 并存，Service 的 endpoints 同时挂着新旧 Pod，流量按 readiness 探针逐渐切换。
  - 回滚本质 = 把期望状态改回旧版本，控制器自动收敛，镜像换回去同样走滚动。
- **工程实践**：
  - 零中断发布要配齐 readinessProbe（新 Pod 就绪才接流量）+ preStop + graceful shutdown（在途请求不丢）。
  - 发布前看 `rollout status`；出问题 `rollout undo` 比重新构建发布快得多。
  - 加分：回滚不是秒级——它也是一个滚动过程，旧镜像要重新拉取。

### Q4：liveness、readiness、startup 探针有什么区别？配置错了会怎样？
**答**：
- **标准结论**：
  - startup 保护慢启动应用（启动期不参与存活判定）；liveness 决定「要不要重启你」（进程自身健康）；readiness 决定「要不要给你流量」（依赖与就绪状态）。
  - 三者都支持 httpGet / tcpSocket / exec 三种探测方式。
- **底层原理**：
  - 失败动作三张表：liveness 失败 → kubelet 按 restartPolicy 重启容器；readiness 失败 → 从 Service endpoints 摘除（只摘流量不重启）；startup 失败 → 容器被重启。
  - 设计动机：重启与摘流量是两种完全不同的「治疗」——摘流量等恢复代价小、重启丢状态代价大，所以只有「进程死了 / 假死」才该走 liveness。
- **工程实践**：
  - 经典事故是 liveness 探依赖接口——下游抖动 → liveness 失败 → 容器反复重启 → 把「下游部分故障」放大成「本服务全灭」。
  - 原则：liveness 只探进程自身，依赖检查一律放 readiness；Java 应用必须配 startupProbe 给足冷启动时间，否则启动期就被 liveness 误杀。
  - 加分：探针路径要分开（liveness 与 readiness 用不同端点）。

### Q5：requests 和 limits 有什么区别？OOMKilled 是怎么来的？
**答**：
- **标准结论**：
  - requests 是调度依据——声明「至少需要多少」，scheduler 按它选节点；limits 是运行上限——超过即被节流或杀死。
  - CPU 超限被节流（throttling），内存超限直接 OOM Kill。
- **底层原理**：
  - requests 参与节点容量计算与 QoS 分级——Pod 按 requests/limits 被分为 Guaranteed（requests=limits）/ Burstable / BestEffort 三档，资源紧张时 kubelet 按 QoS 优先驱逐 BestEffort。
  - limits 由运行时实现：CPU 用 CFS 配额（节流不杀），内存用 cgroup 限制 + OOM Killer（超了直接杀进程，Pod 显示 OOMKilled，`lastState.terminated.reason` 可查）。
- **工程实践**：
  - requests 按稳态用量估（别拍脑袋），limits 留 1.2–1.5 倍余量且必须与 JVM 堆参数联动（如 limits 1536Mi 配 `MaxRAMPercentage=75` 保证堆约 1.1G）。
  - 全部不设 = BestEffort，集群资源紧张第一个被驱逐；limits 太小 = OOMKilled 循环崩溃。
  - 加分：OOMKilled 与 Java 抛 `OutOfMemoryError` 不是一回事——前者是 cgroup 杀进程，可能堆都还没满。
