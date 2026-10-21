# javalinky —— 《Flask Web 开发 · Javalin 重写版》的配套代码

这是 [`docs/javalin/`](../docs/javalin/) 那套教程的可运行版本：一个用
**Javalin 7 + JTE + Hibernate ORM 7 + Flyway** 写的社交博客，功能对应原书
《Flask Web Development》（Miguel Grinberg）第 2 版的 Flasky。

每一章的结束状态都打了 Git 标签，想看哪一章就 checkout 哪一章：

```bash
git checkout jv-ch05      # 第 5 章结束时的样子
cd javalinky && mvn -q compile exec:exec
```

| 标签 | 章节 | 这一章新增了什么 |
|---|---|---|
| `jv-ch01` | 安装 | pom.xml、Javalin 依赖、项目骨架 |
| `jv-ch02` | 应用的基本结构 | `Javalin.create(config -> …)`、路由、钩子 |
| `jv-ch03` | 模板 | JTE 布局与页面、静态文件、错误页、`Routes` |
| `jv-ch04` | Web 表单 | Bean Validation、会话、Flash、CSRF |
| `jv-ch05` | 数据库 | Hibernate、Flyway、`UnitOfWork`、仓储 |
| `jv-ch06` | 电子邮件 | Angus Mail、HTML + 纯文本两套模板、虚拟线程异步 |
| `jv-ch07` | 大型应用的结构 | 配置三档、应用工厂、路由模块、第一组测试、可执行 jar |
| `jv-ch08` | 用户认证 | bcrypt、JWT 令牌、注册/确认/改密码/改邮箱 |
| `jv-ch09` | 用户角色 | 位标志权限、`RouteRole` + `beforeMatched` 授权 |
| `jv-ch10` | 用户资料 | 资料字段、Gravatar、两套编辑表单 |
| `jv-ch11` | 博客文章 | 文章、分页、Markdown 渲染与清洗、假数据 |
| `jv-ch12` | 关注 | 自引用多对多、时间线查询、标签页 |
| `jv-ch13` | 用户评论 | 评论、协管隐藏/恢复、批量评论数 |
| `jv-ch14` | 应用编程接口 | `/api/v1`、DTO、Bearer 令牌、JSON 错误 |
| `jv-ch15` | 测试 | `TestFixtures`、JaCoCo、Playwright 端到端、CI |
| `jv-ch16` | 性能 | 查询计数、`Server-Timing`、N+1 回归测试 |
| `jv-ch17` | 部署 | 预编译模板、日志、Docker、systemd、nginx |

`master` 上就是第 18 章（也就是最终）的状态。

## 跑起来

需要 **JDK 21+** 和 **Maven 3.9+**（第 1 章讲了怎么装）。

```bash
cd javalinky

# 1. 建库（默认用 H2 文件库，存在 ./data/，不需要装任何数据库）
#    顺手把角色灌进去，否则注册时会报“没有默认角色”
mvn -q compile exec:exec@cli -Dcli.args="deploy"

# 2.（可选）造点假数据：100 个用户、400 篇文章
mvn -q exec:exec@cli -Dcli.args="fake 100 400"

# 3. 启动，打开 http://localhost:7070
mvn -q compile exec:exec
```

假数据里的用户密码统一是 `password`。想要管理员账号，就用
`.env` 里 `ADMIN_EMAIL` 指定的邮箱去注册 —— 注册时会自动拿到“管理员”角色。

想收邮件（注册确认、重置密码）的话，起一个本地假邮箱：

```bash
docker run -d --name mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit
# 然后打开 http://localhost:8025 看收到的信
```

### 常用命令

```bash
mvn -q compile exec:exec                      # 跑应用（开发档位，模板改了就生效）
mvn -q exec:exec@cli -Dcli.args="migrate"     # 只跑数据库迁移
mvn -q exec:exec@cli -Dcli.args="seed-roles"  # 创建/更新角色（幂等）
mvn -q exec:exec@cli -Dcli.args="fake 50 200" # 造假数据
mvn -q exec:exec@cli -Dcli.args="deploy"      # 部署任务：迁移 + 角色 + 自关注

mvn test                                      # 48 个测试（surefire 自动用 TEST 档位）
mvn verify                                    # 顺带生成 JaCoCo 覆盖率报告
mvn -Pe2e test                                # 连 Playwright 端到端测试一起跑

mvn clean package                             # 打出 target/javalinky.jar（自带全部依赖）
java -jar target/javalinky.jar                # 直接运行
```

端到端测试第一次要先装浏览器（约 110 MB，只需一次）：

```bash
mvn -q dependency:build-classpath -Dmdep.includeScope=test -Dmdep.outputFile=target/test-cp.txt
java -cp "target/test-classes:$(cat target/test-cp.txt)" com.microsoft.playwright.CLI install chromium
```

### 为什么是 `exec:exec` 而不是 `exec:java`

`exec:java` 在 Maven 自己的 JVM 里用一个独立的类加载器跑应用，而 JTE 在开发模式下
会在运行时把模板编译成类、再用 `URLClassLoader` 加载 —— 那个加载器的父加载器是
**系统类加载器**，看不到 Maven 塞进来的依赖，于是一访问页面就
`NoClassDefFoundError: gg/jte/Content`。`exec:exec` 另起一个真正的 JVM，
和部署时 `java -jar` 的行为完全一致。

## 生产模式

```bash
mvn clean package
APP_PROFILE=prod \
SITE_BASE_URL=https://你的域名 \
DATABASE_URL=jdbc:postgresql://localhost:5432/javalinky \
DATABASE_USER=javalinky DATABASE_PASSWORD=... \
SECRET_KEY=$(openssl rand -hex 32) \
ADMIN_EMAIL=you@example.com \
java -Dlogback.configurationFile=logback-prod.xml -jar target/javalinky.jar
```

`APP_PROFILE=prod` 会：要求上面这些环境变量必须存在（缺一个就启动失败）、
用 jar 里**预编译好的**模板、关掉所有开发辅助、会话 Cookie 只走 HTTPS。

容器方式：

```bash
docker build -t javalinky .
# 或者连数据库一起起来（需要先在 .env 里设 DB_PASSWORD 和 SECRET_KEY）
docker compose up -d
```

传统服务器部署的 systemd unit、nginx 配置和 `deploy.sh` 在 [`deploy/`](deploy/) 目录里。

## 目录结构

```
javalinky/
├── pom.xml
├── Dockerfile  compose.yaml  Procfile
├── deploy/                        # systemd / nginx / deploy.sh / env 模板
└── src/
    ├── main/
    │   ├── java/com/example/javalinky/
    │   │   ├── App.java           # 入口：读配置 → 建应用 → start
    │   │   ├── Cli.java           # migrate / seed-roles / fake / deploy
    │   │   ├── JavalinkyApp.java  # 应用工厂：所有装配都在这里
    │   │   ├── Sessions.java
    │   │   ├── api/               # REST API：DTO、认证、错误体
    │   │   ├── config/            # Profile / Env / AppConfig
    │   │   ├── db/                # Db、UnitOfWork、各仓储、Page、QueryStats
    │   │   ├── mail/              # MailConfig / Mailer / MailService
    │   │   ├── model/             # User / Role / Permission / Post / Comment / Follow
    │   │   ├── service/           # AuthService / SeedService
    │   │   ├── util/              # Passwords / Tokens / Markdown / Gravatar
    │   │   └── web/               # 过滤器、页面模型、路由模块、表单
    │   ├── jte/                   # HTML 模板（ContentType.Html）
    │   ├── jte-text/              # 纯文本邮件模板（ContentType.Plain）
    │   └── resources/
    │       ├── META-INF/persistence.xml
    │       ├── db/migration/      # Flyway 的 V1…V7
    │       ├── logback.xml  logback-prod.xml
    │       └── public/            # css / js / favicon
    └── test/java/com/example/javalinky/
        ├── TestFixtures.java  TestHttp.java
        ├── *Test.java              # 单元 + 集成测试
        ├── web/  api/              # 按层组织的集成测试
        └── e2e/                    # Playwright（默认不跑）
```

## 和教程文字的差异

教程是先写的，这份代码是把它真正跑起来之后的结果 —— 过程中发现了几十处
API 对不上、顺序不对、少了文件的地方，都在 [`TUTORIAL-DIFFS.md`](TUTORIAL-DIFFS.md) 里逐条记着。
**代码是准的**：每一章的标签都经过编译、测试和真实运行的验证。
