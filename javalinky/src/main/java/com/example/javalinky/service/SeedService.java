package com.example.javalinky.service;

import com.example.javalinky.db.FollowRepository;
import com.example.javalinky.db.RoleRepository;
import com.example.javalinky.model.Permission;
import com.example.javalinky.model.Post;
import com.example.javalinky.model.Role;
import com.example.javalinky.model.User;
import jakarta.persistence.EntityManager;
import net.datafaker.Faker;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class SeedService {

    /** 角色的唯一定义处。要改权限，改这里然后重新跑 seed-roles。 */
    private static final Map<String, Set<Permission>> ROLES = new LinkedHashMap<>();
    private static final String DEFAULT_ROLE = "普通用户";

    static {
        ROLES.put("普通用户", EnumSet.of(
            Permission.FOLLOW, Permission.COMMENT, Permission.WRITE));
        ROLES.put("协管员", EnumSet.of(
            Permission.FOLLOW, Permission.COMMENT, Permission.WRITE,
            Permission.MODERATE));
        ROLES.put("管理员", EnumSet.allOf(Permission.class));
    }

    private static EntityManager em;

    public static void use(EntityManager entityManager) {
        em = entityManager;
    }

    /**
     * 创建或更新全部角色。幂等：跑一次和跑一百次结果一样。
     * 相当于原书的 Role.insert_roles()。
     */
    public static void insertRoles() {
        ROLES.forEach((name, permissions) -> {
            Role role = em.createQuery("select r from Role r where r.name = :n", Role.class)
                .setParameter("n", name)
                .getResultStream().findFirst()
                .orElseGet(() -> {
                    Role fresh = new Role(name, Set.of());
                    em.persist(fresh);
                    return fresh;
                });

            // 每次都按代码里的定义重设，这样调整权限后重跑即可生效
            role.setPermissions(permissions);
            role.setDefaultRole(name.equals(DEFAULT_ROLE));
        });
    }

    // ---------- 假数据（只在开发环境用） ----------

    public static void fake(int userCount, int postCount) {
        // 两个 Faker：展示字段用中文，标识字段必须是 ASCII。
        // 只用中文 Faker 的话，internet().username() 会给出“峻熙.梁丘”这样的用户名
        // （不符合注册时的 ^[A-Za-z][A-Za-z0-9_.]*$ 规则），
        // emailAddress() 更会退化成 “.@hotmail.com” —— 几条之后就全撞车了。
        var zh = new Faker(Locale.SIMPLIFIED_CHINESE);
        var en = new Faker(Locale.ENGLISH);
        var faker = zh;
        var random = new Random();
        var defaultRole = new RoleRepository(em).defaultRole();

        // --- 用户 ---
        int created = 0;
        int attempts = 0;
        while (created < userCount && attempts < userCount * 20) {
            attempts++;
            // 用序号兜底保证唯一，省得靠运气
            String username = asciiUsername(en.internet().username()) + (created + 1);
            String email = username + "@example.com";

            // 假数据还是可能撞车（比如重复跑 fake），撞了就跳过
            if (exists(email, username)) continue;

            var user = new User(email, username);
            user.setPassword("password");          // 开发用，统一密码方便登录测试
            user.setConfirmed(true);
            user.setName(faker.name().fullName());
            user.setLocation(faker.address().city());
            user.setAboutMe(faker.lorem().sentence(12));
            user.setRole(defaultRole);
            em.persist(user);
            em.persist(new com.example.javalinky.model.Follow(user, user));   // 自关注
            created++;

            // 每 50 条 flush 一次，别让一级缓存无限膨胀
            if (created % 50 == 0) {
                em.flush();
                em.clear();
                defaultRole = new RoleRepository(em).defaultRole();  // clear 之后要重新取
            }
        }
        em.flush();

        // --- 文章 ---
        var userIds = em.createQuery("select u.id from User u", Long.class).getResultList();
        for (int i = 0; i < postCount && !userIds.isEmpty(); i++) {
            var author = em.getReference(User.class, userIds.get(random.nextInt(userIds.size())));
            var post = new Post(fakeMarkdown(faker), author);
            // 随机散布在过去一年里，这样时间排序才有意义
            post.setTimestamp(Instant.now().minus(random.nextInt(365 * 24), ChronoUnit.HOURS));
            em.persist(post);
            if (i % 50 == 0) em.flush();
        }

        System.out.printf("已生成 %d 个用户、%d 篇文章%n", created, postCount);
    }

    /** 把 faker 给的用户名收拾成合法的：只留 ASCII，首字符必须是字母 */
    private static String asciiUsername(String raw) {
        String cleaned = raw.replaceAll("[^A-Za-z0-9_.]", "");
        if (cleaned.isEmpty() || !Character.isLetter(cleaned.charAt(0))) {
            cleaned = "u" + cleaned;
        }
        return cleaned.length() > 40 ? cleaned.substring(0, 40) : cleaned;
    }

    private static boolean exists(String email, String username) {
        return em.createQuery("""
                select count(u) from User u
                 where lower(u.email) = lower(:email)
                    or lower(u.username) = lower(:username)
                """, Long.class)
            .setParameter("email", email)
            .setParameter("username", username)
            .getSingleResult() > 0;
    }

    /** 带点 Markdown 语法的假正文，方便验证渲染 */
    private static String fakeMarkdown(Faker faker) {
        return """
            ## %s

            %s

            - %s
            - %s

            用 `代码` 和 **粗体** 以及 [链接](https://example.com) 测试一下渲染。
            """.formatted(
                faker.lorem().sentence(5),
                faker.lorem().paragraph(4),
                faker.lorem().sentence(6),
                faker.lorem().sentence(6));
    }

    /**
     * 确保每个用户都关注了自己。幂等，可以反复执行。
     * 相当于原书的 User.add_self_follows()。
     */
    public static void addSelfFollows() {
        var follows = new FollowRepository(em);
        var users = em.createQuery("select u from User u", User.class).getResultList();

        int added = 0;
        for (User user : users) {
            if (!follows.isFollowing(user, user)) {
                follows.follow(user, user);
                added++;
            }
        }
        System.out.printf("为 %d 个用户补上了自关注%n", added);
    }
}
