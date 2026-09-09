# M1-07：JWT 认证过滤器

状态：**待开始**  前置：M1-06 已完成

返回 [M1 任务列表](README.md)。

## 目标

让受保护接口能够从请求中的 Bearer token 恢复当前登录用户；缺失、篡改或过期 token 一律返回 401。

## 范围

- 配置 Spring Security 的最小 Security Filter Chain。
- 从 `Authorization: Bearer <token>` 提取并校验 JWT。
- 校验成功后写入 SecurityContext。
- 定义公开接口与受保护接口的最小白名单。

## 学习点

Filter Chain 顺序、SecurityContext、认证与授权的区别、401 的语义。

## 验收

1. 不带 token 访问受保护接口返回 401。
2. 篡改或过期 token 返回 401。
3. 有效 token 可以访问已认证但未限制角色的接口。

## 不做

不做角色校验、刷新 token、黑名单、OAuth2 登录；这些不是认证过滤器的职责。

## 完成记录

日期：

提交：

测试与接口证据：
