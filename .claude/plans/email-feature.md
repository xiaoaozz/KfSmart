# 邮箱登录 + 注册邮箱验证码 实现计划

## Context
前端注册页已有 email 字段，但后端从未保存或使用它。现在需要：
1. 登录支持用户名 **或** 邮箱作为 identifier
2. 注册时必须通过 6 位 OTP（Redis 存储，5 分钟有效期）验证邮箱后才能建账号
3. SMTP 使用 QQ 邮箱（smtp.qq.com:465，SSL）

## 文件清单

### 后端（11 处）
1. `pom.xml` — 加 `spring-boot-starter-mail`
2. `src/main/resources/application.yml` — 加 `spring.mail` 块（QQ SMTP）
3. **NEW** `src/main/java/com/smart/kf/service/EmailService.java` — OTP 发送 + 验证
4. `src/main/java/com/smart/kf/repository/UserRepository.java` — 加 `findByEmail`
5. `src/main/java/com/smart/kf/service/UserService.java` — `registerUser` 增加 email 参数 + 邮箱唯一性校验；`authenticateUser` 加 email 回退查找；保留 2 参数重载兼容测试
6. `src/main/java/com/smart/kf/controller/UserController.java` — 新增 `RegisterRequest` record；注入 `EmailService`；替换 `/register` handler（验证 OTP）；新增 `POST /users/send-email-code` endpoint
7. `src/main/java/com/smart/kf/config/SecurityConfig.java` — `permitAll` 加 `/api/v1/users/send-email-code`

### 前端（3 处）
8. `frontend/src/api/auth.ts` — `RegisterParams` 加 `emailCode`；加 `sendEmailCode` 方法
9. `frontend/src/pages/auth/RegisterPage.tsx` — 加 OTP 输入框 + 60s 倒计时"获取验证码"按钮
10. `frontend/src/pages/auth/LoginPage.tsx` — placeholder 改为"用户名 / 邮箱"

### 文档（1 处）
11. `.claude/plans/api-integration.md` — 新增邮件验证章节

## 关键设计决策

- **邮箱唯一性**：application 层强制（`findByEmail` 查重），DB `email` 列无 UNIQUE 约束，不修改 schema
- **Redis key 规范**：`email:code:{email}`（OTP，5 min TTL）、`email:code:limit:{email}`（限流，60 s TTL）
- **值反序列化**：现有 `RedisConfig` 用 `GenericJackson2JsonRedisSerializer`，取出后 `.toString()` 兼容两种格式
- **2 参数重载**：保留 `registerUser(username, password)` → 委托 3 参数版，避免改测试文件
- **登录统一错误**：用户名/邮箱/密码错误均返回同一条"用户名或密码错误"，不泄露哪个字段不对
- **邮件内容**：HTML 邮件，Google、QQ 字体友好

## QQ SMTP 配置
```yaml
spring:
  mail:
    host: smtp.qq.com
    port: 465
    username: your_qq@qq.com   # 替换为真实 QQ 邮箱
    password: your_smtp_authcode  # QQ 邮箱「授权码」，不是 QQ 密码
    default-encoding: UTF-8
    properties:
      mail.smtp.auth: true
      mail.smtp.ssl.enable: true
      mail.smtp.socketFactory.port: 465
      mail.smtp.socketFactory.class: javax.net.ssl.SSLSocketFactory
      mail.smtp.socketFactory.fallback: false
```

## 验证方式
1. `mvn compile -q` — 0 错误
2. `pnpm typecheck` — 0 错误
3. 手动测试：注册时不输验证码 → 400；输错误验证码 → 400；正确验证码 → 200
4. 用邮箱登录 → 200；用用户名登录 → 200；错误密码 → 401
5. 60 s 内重复点"获取验证码" → 429
