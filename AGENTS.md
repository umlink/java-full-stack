# AGENTS.md

本文件是仓库级总控规则。进入子目录工作时，必须同时读取对应子项目的 `AGENTS.md`。

## 1. 权威来源

1. `docs/` 是学习内容与技术决策的权威来源。
2. `docs/10-实战产品蓝图/实践计划/` 是实战任务状态与执行顺序的权威来源。
3. 编码前先读取当前实践卡片，只实现前置已完成且状态为“待开始”的第一张卡片。
4. 一张卡片只交付一个可感知行为；禁止顺手实现后续卡片。
5. 测试通过后，才能更新完成记录、任务列表和上层进度。

当前主线：BootMall 的 `M1-C 管理后台接入`。当前唯一待开始卡片为 [M1-C-03：认证路由守卫](docs/10-实战产品蓝图/实践计划/M1-管理后台接入/03-认证路由守卫.md)。

## 2. 仓库结构

```text
admin-client/       B 端管理后台
user-client/        C 端商城
boot-server/        Spring Boot 后端聚合工程
docs/               学习路线、产品蓝图和实践卡片
```

子项目规则：

- 后端开发：读取 [boot-server/AGENTS.md](boot-server/AGENTS.md)。
- B 端前端：读取 [admin-client/AGENTS.md](admin-client/AGENTS.md)。
- C 端前端：读取 [user-client/AGENTS.md](user-client/AGENTS.md)。
- 文档维护：读取 [docs/AGENTS.md](docs/AGENTS.md)。

## 3. 单卡闭环

1. 读卡定界：确认目标、范围、验收和“不做”。
2. 规范前置：首次引入技术能力前，先补对应子项目规则。
3. 建立契约：先确定输入输出、错误分支和最小测试场景。
4. 最小实现：只改达成验收所必需的模块。
5. 验证复盘：运行自动化测试和卡片要求的真实验证。
6. 记账收口：验证通过后更新实践卡片与进度。

## 4. 全局红线

- 不提交密钥、日志、`target/`、`node_modules/`、`dist/`、IDE 配置和本地 `.env`。
- 不修改用户级环境配置，例如 `~/.m2/settings.xml`、全局 npm/pnpm 配置。
- 不自动暂存或提交 `boot-server/data/bootapp.mv.db`；只有用户明确要求记录数据库快照时才处理。
- 不使用 `git reset --hard`、`git checkout --`、交互式 rebase、强制推送。
- 提交和推送必须由用户明确要求。

## 5. Git 工作流

1. 动手前执行 `git status --short`。
2. 只暂存本任务相关文件。
3. 提交前执行 `git diff --check` 和对应测试。
4. 提交信息使用中文 Conventional Commits，例如 `feat: 新增统一异常处理`、`docs: 更新前端规范`。
5. 推送前确认当前分支和待推送提交。

## 6. 工作方式

- 先读代码、实践卡片和相邻文档，再修改。
- 优先复用现有模式，避免一次性抽象。
- 发现无关问题只记录风险，不扩大本次范围。
- 汇报必须包含改动位置、验证结果和未完成风险。
