# CLAUDE.md

面向「Java 企业级全栈学习路线」仓库（前端 / Node / Go 背景转 Java 的学习者）。双定位：**docs/** 是学习资料（8 阶段 60+ 篇，入口 `docs/README.md`），**boot-app/** 是配套实操工程。所有改动服务「学习者」：内容准确、结构稳定、规范可循。

## 技术基线（以 docs/ 为准）

| 组件 | 版本 / 要点 |
|-|-|
| Java | 25（LTS）——唯一前置要求，任何机器自装（Temurin/Corretto 均可） |
| Spring Boot | 4.0.0（BOM import 管理） |
| MyBatis-Plus | 3.5.17（Boot4 专用 starter；**3.5.9+ `ServiceImpl` 在 `spring.service.impl` 包**） |
| H2 | BOM 托管；数据落 `boot-app/data/`（前期学习免安装，后期可切 MySQL） |
| Maven | 3.9.16（`~/tools/`）；阿里云镜像已配 `~/.m2/settings.xml` |
| Lombok | 1.18.42（父 pom 的 annotationProcessorPaths 已配） |

## 构建环境（可移植——不绑定任何机器的路径）

- **唯一前置：JDK 25（LTS）**，任何机器自行安装；确认 `java -version` 为 25
- **Maven 无需安装**：仓库自带 Maven Wrapper（`boot-app/mvnw` / `mvnw.cmd`），自动下载 3.9.16 到用户 `~/.m2/wrapper`；已装全局 Maven 3.9+ 也可直接用 `mvn`
- 常用命令（`boot-app/` 下；Windows 用 `mvnw.cmd`，macOS/Linux 用 `./mvnw`）：`mvnw -pl services/user-service -am install -DskipTests`（构建 + 装 common-core）→ `mvnw -pl services/user-service spring-boot:run`（启动，别带 -am）→ `mvnw -pl services/user-service -am test`
- 改过 pom（如 `-parameters`）后增量编译会跳过旧类——用 `clean install` 才能生效
- 首次构建慢是正常的（拉依赖）；国内可自配 `~/.m2/settings.xml` 阿里云镜像（wrapper 的 distributionUrl 已指向阿里云镜像）

## ✅ 可以

1. 阅读 / 讲解 docs 与 boot-app 全部内容
2. 按下方「文档写作规范」增强 docs/（前置框 / 类比 / Mermaid / 术语解释）
3. 在 boot-app/ 按 `infrastructure/`（公共底座）+ `services/`（独立服务）结构开发
4. 用 `~/tools` 环境构建 / 测试 / 启动验证；`boot-app/data/`（H2 数据）随仓库提交记录
5. 提交走 commit-push 流程（Conventional Commits 中文）——**push 前必须经用户确认**

## ❌ 不可以（红线）

**文档**
- 不破坏模板：阶段零~七 7 段式（快速入门 → 精简大纲 → 学习内容详情 → 坑点提醒 → 本节自检 → 本节配套思考题 → 常见面试题，快速入门统一 5 小节）；**阶段八是独立模板勿套**；坑点提醒在阶段一/二为 H3 是原有格式
- 不删既有组件：`⏸️ 短期可以不学` 框、代码块、面试题三层答法、前端对照——「增强」不是「重写」
- 重命名文件 / 目录必须同步审计全部交叉引用与路径约定（如 H2 相对 `../../data` 依赖模块层级）
- Mermaid 可使用**规范支持的全部图表类型**（flowchart / sequenceDiagram / stateDiagram / classDiagram / erDiagram / gantt / mindmap / timeline / pie 等），**按内容自动选择合适类型**（时序用 sequenceDiagram、流程用 flowchart、状态用 stateDiagram、类关系用 classDiagram、排期用 gantt 等），classDef / style 等规范语法均可用；但节点文本含半角引号 / 括号须 `["..."]` 包裹或改中文「」（否则渲染报错），且若目标渲染器（GitHub / VSCode 预览）不支持某类型则回退为基础 flowchart

**代码**
- 新增依赖必须同步父 pom `dependencyManagement`（BOM 锁版本范式）
- 不改 H2 数据文件路径与 schema.sql 幂等语义（「仅空库灌初始数据」——用户改动须跨重启保留）
- 不提交 `target/`、IDE 文件；环境改动只动 `~/tools/` 与 `~/.m2/`，**不安装系统级软件**
- boot-app 遵循学习者工程规范：禁 `Executors.newFixedThreadPool/newCachedThreadPool`（一律显式 `new ThreadPoolExecutor`）；POJO 属性用包装类型；日志用 `{}` 占位符；Bean 构造器注入优先；命名驼峰语义化（对齐阶段零 02 讲阿里规约）

**协作与内容**
- 不直接 push（确认门）；不做 `rebase -i` / `force push` 历史改写；不静默包含密钥、调试残留、临时文件
- 版本 / API 细节以 docs/ 与实测为准，不凭记忆写（如 MP 包结构、Boot 4 的 `-parameters`）；类比不得与正文矛盾（双段式：生活版 → 落回技术结论）

## 文档写作规范

1. **术语首现即解释**：任何技术词（关键字 / 类名 / 库名 / 算法名）首次出现处跟白话或类比；只出现一次的词更要解释
2. **手法优先级**：流程 / 机制式内容（A→B→C 因果链）首选 Mermaid 图；**图表类型按内容自动选择**（时序 sequenceDiagram / 流程 flowchart / 状态 stateDiagram / 类关系 classDiagram / 时间规划 gantt / 结构 mindmap 等），节点带白话标注；概念对比用生活化场景类比；可图 + 类比结合
3. **🧩 前置框**（大概念补课）：`> 🧩 前置 30 秒：XXX——白话 + 前端对照 +「细节在第 X 讲展开」`，与 `⏸️` 框同风格
4. **类比双段式**：先「生活版」故事，再「换成 Java/Spring」落回技术结论
5. **前端对照**：读者是前端背景，优先用 NestJS / npm / RxJS 心智做锚点
6. **简体中文**：短句、加粗关键词、「」引号；与各阶段既有风格一致

## 常用链接

- 学习入口：`docs/README.md` ｜ 工程说明：`boot-app/README.md` ｜ 阶段零起点：`docs/00-阶段零-起点盘点与补课/01-能力迁移对照.md` ｜ 实战路线：`docs/10-实战产品蓝图/README.md` ｜ AI 协助式学习方法论：`LEARNING.md`
