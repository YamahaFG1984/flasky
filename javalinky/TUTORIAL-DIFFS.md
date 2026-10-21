# 教程文字 ↔ 配套代码的差异清单

写这份代码的过程中，凡是“照教程抄下来编译不过 / 跑不起来 / 行为不对”的地方都记在这里。
每一条都是真正踩到的，不是看文档猜的。前面几条是 API 层面的硬错误，后面几条是
顺序、缺文件、以及需要补充说明的地方。

> 代码以仓库为准；这份清单同时也是回写教程 HTML 的 TODO。

1. ch03/ch04/ch05: `JavalinJte.directoryTemplateEngine()` 不存在（Kotlin 伴生对象，Java 侧要写
   `JavalinJte.Companion.directoryTemplateEngine()`）。顺带加一段「Javalin 是 Kotlin 写的，
   companion object 在 Java 里叫 .Companion」的说明。
2. ch01–ch05+: `mvn exec:java` 在引入 JTE 后会 NoClassDefFoundError: gg/jte/Content
   （exec:java 的类加载器 ≠ 系统类加载器，jte 运行时编译的模板类加载不到 gg.jte.*）。
   改成 exec:exec（pom 里配 executable=java + <classpath/>），并解释原因。
3. 新增 src/main/resources/logback.xml（root=INFO）：不加的话 Logback 默认 DEBUG，
   Jetty 会把终端刷满。文档第 16 章才出现 logback.xml，应在第 2/3 章先给最小版。
4. ch04 tag/field.jte、tag/checkbox.jte：JTE 不允许在**属性名位置**写 @if
   （报 "Illegal HTML attribute name ... smart attributes will do just that"）。
   改用 JTE 的智能属性：autocomplete="${autocomplete}"、required="${required}"、
   checked="${"1".equals(form.value(name))}" —— 值为 null/false 时属性整体不输出。
5. ch04 App.java：LoginForm 在文档里只有裸 record 片段，仓库里落在
   web/form/LoginForm.java，App.java 需要多一行 import com.example.javalinky.web.form.LoginForm;
6. ch05 App.java 最后一行写的是 Routes.registerMain(config)，但 Routes 在第 3 章是
   URL 常量类、并没有这个方法（拆路由是第 7 章的事）。仓库里第 5 章仍把路由写在 App.java，
   建议文档把这行改成内联路由或注明“第 7 章才会出现”。
7. ch01 .gitignore 少一条 data/（第 5 章 H2 开发库会生成 data/javalinky-dev.mv.db）。
8. ch05 的 IndexPage、NameForm 只以片段出现，仓库里补成
   web/IndexPage.java、web/form/NameForm.java。
9.  ch07 Env.java 漏了 import java.io.UncheckedIOException;（readDotenv 里用到）。
10. ch07 AppConfig.test() 调 MailConfig.disabled()，但第 6 章的 MailConfig 没有这个工厂。
    仓库里补了 disabled() + enabled()，并让 Mailer 在禁用时只记日志不连 SMTP。
11. ch07 JavalinkyApp / Services / Cli 引用了后面章节才出现的类
    （ApiRoutes→14、AuthService→8、PostService/PostRepository→11、RoleRepository→9、
    SeedService→9/11/17）。文档最好注明“这几行等到第 N 章才加”，否则照抄编译不过。
12. ch07 AuthRoutes 片段漏了 import io.javalin.http.Context;。
13. 一个 TemplateEngine 只能有一个 ContentType。纯文本邮件模板要单独放一个源目录
    （仓库用 src/main/jte-text），否则第 17 章预编译时两种 ContentType 会撞在同一个包里。
    相应地 MailService 改为注入两个 TemplateEngine，而不是用 Templates 里的静态常量。
14. 建议在 pom 的 surefire 里配 <environmentVariables><APP_PROFILE>test</APP_PROFILE>，
    这样直接 mvn test 就走 TEST 档位，不必每次记得写 APP_PROFILE=test。
15. **ch07 AppConfig.test() 的 H2 内存库 URL 少了 DB_CLOSE_DELAY=-1**（ch05 和 ch15 里都有）。
    不加的话 Flyway 迁移完关闭连接 → 整个内存库被丢弃 → 测试里报
    “Table USERS not found (this database is empty)”。这是实跑才会暴露的坑。
16. **ch08 加 java-jwt 之后必须引入 jackson-bom**：java-jwt 带来 jackson-databind 2.19，
    传递的 jackson-core 却是 2.15，运行时抛 NoSuchMethodError:
    BufferRecycler.releaseToPool()。ch07 的 properties 里已经写了 jackson.version，
    但从没在 dependencyManagement 里 import jackson-bom —— 要补上，并且正好可以当
    “依赖冲突怎么查、怎么治”的真实案例。
17. ch08 CurrentUser.install 里调了 user.setLastSeen(...)、confirmEmailChange 里调了
    user.setAvatarHash(null)，这两个字段第 10 章才加。仓库里第 8 章先留注释，第 10 章补上。
18. ch08 register() 调 services.roles(ctx).defaultRole()（第 9 章才有）。
19. ch08 AuthTest 用了 TestFixtures（第 15 章）和 client.post(path, Map)。
    注意 javalin-testtools 的 post(path, body) 发的是 **JSON**，表单要用
    FormBody.Builder + client.request(path, req -> req.post(form))，否则 formParam 取不到值。
    仓库里第 8 章用 FormBody 自己写了 postForm/csrfTokenFrom 两个小工具。
20. ch09 之后，注册流程依赖“默认角色已存在”。测试里必须先灌角色
    （相当于原书 setUp 里的 Role.insert_roles()），否则 defaultRole() 抛
    IllegalStateException → 注册返回 500。文档可以在第 9 章就提一句。
21. ch09 的 MainRoutes 片段把第 10–13 章的处理器一次性列齐（userProfile/post/follow/
    moderate/admin…）。照抄会编译不过，建议注明“这是最终形态，按章逐步出现”。
22. ch09 V3__role_permissions.sql 实际是空操作（V1 已经建了 permissions、is_default
    和 ix_roles_is_default）。另外整个教程缺 V2__*.sql，版本号从 V1 跳到 V3 ——
    Flyway 允许跳号，但读者会困惑，建议补一句说明或重新编号。
23. **Validator.allowNullable() 在 Javalin 7 已经没有了**，改用 getOrNull()。
    文档里共 8 处：ch10:489、ch11:456、ch12:836/954/970、ch13:333/565、ch14:696。
24. ch10 edit_profile.jte 的角色下拉框又犯了 #4 的毛病：
    @if(...)selected@endif 在属性名位置，要改成 selected="${...}"。
25. **ch11 tag/pagination.jte 用了 Razor 风格注释 `@* … *@`，JTE 不支持**，
    结果 @param String baseUrl 这一行整个被当成参数名，渲染时报
    “No parameter with name baseUrl is defined in tag/pagination.jte”。注释要用 <%-- --%>。
26. ch11 MainRoutes 把单篇文章的处理器命名为 post(Context)，会盖住静态导入的
    ApiBuilder.post(...)，编译报 “method post ... cannot be applied to given types”。
    改名 showPost。
27. ch11 SeedService.fake 只用 new Faker(Locale.SIMPLIFIED_CHINESE)：
    internet().username() 给出“峻熙.梁丘”（不符合用户名规则），
    emailAddress() 退化成 “.@hotmail.com”，于是 30 个用户只能造出 3 个。
    要用两个 Faker（中文做展示字段、英文做标识字段）并用序号兜底唯一。
28. ch11 Post 实体引用了第 13 章的 Comment；PostListPage 里 post.getComments().size()
    也要等到第 13 章。文档可注明顺序。
29. ch12 FollowListPage.of 把 since 写成了 null（模板里 timeago 会拿到 null）。
    应该用 f.getTimestamp()。
30. ch13 的 PostListPage 评论数：ch11 用 post.getComments().size()（逐篇懒加载 = N+1），
    而 ch16 的 NPlusOneTest 又要求首页查询数 ≤6。两处对不上。
    仓库里统一用 CommentRepository.countCommentsByPost(ids) 一条聚合查询批量取。
31. ch13 CommentRepository 同时给了 findByPost(post,page,size) 和带 includeDisabled 的版本，
    countByPost 也是；仓库里保留带可见性参数的那套，避免“读者看到被隐藏的评论”。
32. ch15 测试客户端 API（javalin-testtools 7.x 是基于 java.net.http 的，不是 OkHttp）：
    - response.header("Location") 不存在 → response.headers().get("Location").get(0)
    - client.post(path, Map) 发的是 JSON；表单要用 FormBody.Builder + client.request(...)
    - 客户端自带 CookieManager，不需要手工转发 Set-Cookie（文档里那套 sessionOf() 可以删）
    - 默认不跟随重定向（文档说的是 OkHttp 的行为，结论一样但原因要改）
33. ch15 MainRoutesTest 断言首页有“陌生人”，但第 11 章之后首页问候语已经变成
    “欢迎来到 Javalinky”/“你好，xxx！”。
34. exec-maven-plugin 的插件级 <configuration> 里一旦写了 <arguments><classpath/>，
    exec:java 就会报 “Unable to parse configuration ... parameter arguments”。
    仓库改成插件级只配 <commandlineArgs>-classpath %classpath …App</commandlineArgs>，
    另给 Cli 一个命名执行：mvn exec:exec@cli -Dcli.args="seed-roles"。
    文档里所有 exec:java -Dexec.mainClass=…Cli -Dexec.args="…" 都要跟着改。
35. ch15 装 Playwright 浏览器的命令同样受 #34 影响，仓库里改成
    mvn dependency:build-classpath -Dmdep.includeScope=test … + java -cp … com.microsoft.playwright.CLI install chromium
36. ch16 QueryStats.record() 在文档里没人调用（注释说“由下面的计时包装调用”，但那个包装没出现），
    于是 Server-Timing 里的 db;dur 永远是 0。仓库补了一个
    QueryStats.TimingListener implements SessionEventListener
    （jdbcExecuteStatementStart/End），并在 persistence.xml 里用
    hibernate.session.events.auto 注册，这才真的量到 JDBC 耗时。
37. ch16 的 hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS 在 Hibernate 7 已经没有了，
    只保留 hibernate.log_slow_query。
38. ch16 NPlusOneTest 调了一个不存在的 lastQueryCount()。更好的办法是直接读
    Server-Timing 响应头里的 queries;desc="n" —— 不需要为测试开后门，
    而且断言方式更强：对比 1 篇和 20 篇时的查询条数是否相等。
39. **maven-shade-plugin 必须过滤签名文件**，否则 java -jar 直接报
    SecurityException: Invalid signature file digest for Manifest main attributes
    （本项目里是 at.favre.lib:bcrypt 带的 META-INF/PFOPENSO.SF）。
    要加 <filters> 排除 META-INF/*.SF、*.DSA、*.RSA 和重复的 module-info.class。
    这个坑第 7 章打包时还没触发（那时还没引入 bcrypt），到第 17 章部署才炸。
40. ch17 的 Dockerfile 健康检查和 deploy.sh 都在请求 /healthz，但全书从没定义过这个路由。
    仓库里补了 GET /healthz（顺便查一次数据库，避免“端口开着但后端全坏”）。
41. ch17 JavalinkyApp 用了 cfg.behindProxy()，但第 7 章的 AppConfig 没有这个字段。
42. ch17 把带 SMTPAppender 的 logback.xml 直接替换掉开发用的那份，于是开发和测试也会
    挂上邮件 appender（没有 ADMIN_EMAIL 时 logback 还会报错）。仓库拆成两份：
    logback.xml（开发/测试）与 logback-prod.xml，生产用
    -Dlogback.configurationFile=logback-prod.xml 指定。
43. **RequestId 必须装在 Timing 之后**：Javalin 按注册顺序跑 after 过滤器，
    RequestId 的 after 会 MDC.remove()，排在前面的话 Timing 打的慢请求日志里
    requestId 就是空的（实测 “WARN [] … 慢请求”）。
44. ch17 的 error/500.jte 片段用了 ${requestId}，但没说 ErrorPages 要把它传进模板
    （而且得给默认值，否则其它地方渲染 500 页会缺参数）。
45. 预编译模板要两个 jte-maven-plugin 执行：HTML 一份，纯文本一份
    （sourceDirectory=src/main/jte-text + contentType=Plain + packageName=gg.jte.generated.text），
    对应 Templates.textPrecompiled()。只配一个的话生产模式发邮件会找不到纯文本模板。
