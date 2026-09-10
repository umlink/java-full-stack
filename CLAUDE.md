# CLAUDE.md

面向「Java 企业级全栈学习路线」仓库（前端 / Node / Go 背景转 Java 的学习者）。双定位：**docs/** 是学习资料（8 阶段，入口 [docs/README.md](docs/README.md)），**boot-server/** 是配套实操工程。所有改动服务「学习者」：内容准确、结构稳定、规范可循。

## 规则入口

**规则不在本文件定义，只在下列文件定义一次。** 执行任务前按场景先读：

| 场景 | 先读 |
|-|-|
| 一切任务 | [AGENTS.md](AGENTS.md)：权威来源、单卡闭环、全局红线、Git 工作流 |
| 当前该做什么 | [实践计划与进度](docs/10-实战产品蓝图/实践计划/README.md)：进度与当前卡片的唯一来源 |
| 改文档 | [docs/AGENTS.md](docs/AGENTS.md)：模板、写作规则、链接审计 |
| 改后端 | [boot-server/AGENTS.md](boot-server/AGENTS.md)：分层、命名、Web、SQL、事务、编码约定 |
| 改前端 | [admin-client/AGENTS.md](admin-client/AGENTS.md) / [user-client/AGENTS.md](user-client/AGENTS.md)，配合 [前端开发规范](docs/10-实战产品蓝图/前端开发规范.md) |

规则冲突时，以更具体的子项目规则为准；与当前实践卡片冲突时，以卡片为准。

## 技术基线（以 docs/ 为准）

| 组件 | 版本 / 要点 |
|-|-|
| Java | 25（LTS）——唯一前置要求，任何机器自装（Temurin/Corretto 均可） |
| Spring Boot | 4.0.0（BOM import 管理） |
| MyBatis-Plus | 3.5.17（Boot4 专用 starter；**3.5.9+ `ServiceImpl` 在 `spring.service.impl` 包**） |
| H2 | BOM 托管；数据落 `boot-server/data/`（前期学习免安装，后期可切 MySQL） |
| Maven | 3.9.16（仓库自带 Maven Wrapper；无需全局安装） |
| Lombok | 1.18.42（父 pom 的 annotationProcessorPaths 已配） |

## 构建环境要点

不绑定任何机器路径。

- **唯一前置：JDK 25（LTS）**，任何机器自行安装；确认 `java -version` 输出为 25。
- **Maven 无需安装**：仓库自带 Maven Wrapper（`boot-server/mvnw` / `mvnw.cmd`），自动下载 3.9.16 到用户 `~/.m2/wrapper`；已装全局 Maven 3.9+ 的机器也可直接用 `mvn`。
- **改过 pom（如 `-parameters`）后增量编译会跳过旧类**——必须 `clean install` 才生效。
- 首次构建需要下载依赖；Wrapper 发行包地址已由仓库配置。网络环境确有需要时由开发者自行配置 Maven 镜像，代理不得修改用户级 `~/.m2/settings.xml`。

## 常用链接

- 阶段零起点：[01-能力迁移对照.md](docs/00-阶段零-起点盘点与补课/01-能力迁移对照.md)
- 实战产品蓝图：[docs/10-实战产品蓝图/README.md](docs/10-实战产品蓝图/README.md)
- 工程说明：[boot-server/README.md](boot-server/README.md)
- AI 协助式学习方法论：[LEARNING.md](LEARNING.md)
