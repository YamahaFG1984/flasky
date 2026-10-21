package com.example.javalinky.web;

import io.javalin.http.sse.SseClient;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 当前连着的 SSE 客户端（第 21 章）。
 *
 * 这是继第 19 章的 RateLimiter 之后第二个「应用级共享且可变」的对象，
 * 而且比那个更麻烦：连接会在任意时刻断开，而且
 * <b>服务端不会被立刻告知</b> —— 往一个对端已关闭的 TCP 连接里写数据
 * 通常是成功的（数据进了内核缓冲区）。
 *
 * 所以必须有心跳：定期往每个连接写一点东西，写不动的才会暴露出来。
 * 没有心跳的话这个 Map 只增不减，就是一个内存泄漏。
 */
public final class LiveHub implements AutoCloseable {

    /** 心跳间隔。生产环境 15~30 秒比较常见，测试里会调到很小 */
    public static final Duration DEFAULT_HEARTBEAT = Duration.ofSeconds(20);

    /**
     * newKeySet() 给出的是一个并发安全的 Set（底层就是 ConcurrentHashMap）。
     * 普通的 HashSet 在这里会直接出问题：一边遍历推送，一边有人断开连接。
     */
    private final Map<Long, Set<SseClient>> clients = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat;

    public LiveHub() {
        this(DEFAULT_HEARTBEAT);
    }

    public LiveHub(Duration interval) {
        this.heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);      // 别拦着 JVM 退出
            return t;
        });
        long millis = Math.max(1, interval.toMillis());
        heartbeat.scheduleWithFixedDelay(this::ping, millis, millis, TimeUnit.MILLISECONDS);
    }

    /** 新连接进来。调用方要保证 client 已经 keepAlive() 过了 */
    public void register(long userId, SseClient client) {
        clients.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet()).add(client);

        // 容器真的察觉到断开时会回调这里。但它<b>不一定会被调用</b>，
        // 所以不能只靠它 —— 心跳才是兜底。
        client.onClose(() -> unregister(userId, client));
    }

    public void unregister(long userId, SseClient client) {
        // compute 保证「删成员」和「删空集合」是一个原子动作。
        // 分成 get + remove 两步的话，正好有人在这中间往空集合里 add，
        // 那个连接就永远收不到消息了。
        clients.compute(userId, (id, set) -> {
            if (set == null) return null;
            set.remove(client);
            return set.isEmpty() ? null : set;    // 返回 null = 把这个 key 删掉
        });
    }

    /** 推一条消息给某个用户的所有连接 */
    public void send(long userId, String event, Object data) {
        Set<SseClient> set = clients.get(userId);
        if (set == null) return;

        for (SseClient client : set) {
            if (client.terminated()) {
                unregister(userId, client);
                continue;
            }
            try {
                client.sendEvent(event, data);
            } catch (Exception e) {
                // 推送失败就当这个连接废了。绝不能让它影响请求本身 ——
                // 「通知没发出去」比「发评论失败」轻得多。
                unregister(userId, client);
            }
        }
    }

    /**
     * 心跳：往每个连接写一行 SSE 注释（": ping"）。
     * 浏览器会忽略注释行，但这次写会让已经断掉的连接暴露出来。
     * 顺带还能穿过某些代理的空闲超时。
     */
    void ping() {
        clients.forEach((userId, set) -> {
            for (SseClient client : set) {
                if (client.terminated()) {
                    unregister(userId, client);
                    continue;
                }
                try {
                    client.sendComment("ping");
                } catch (Exception e) {
                    unregister(userId, client);
                }
            }
        });
    }

    /** 这个用户现在有几个标签页连着 */
    public int connectionCount(long userId) {
        Set<SseClient> set = clients.get(userId);
        return set == null ? 0 : set.size();
    }

    /** 一共有多少个连接 —— 适合丢到监控里 */
    public int totalConnections() {
        return clients.values().stream().mapToInt(Set::size).sum();
    }

    @Override
    public void close() {
        heartbeat.shutdownNow();
        clients.values().forEach(set -> set.forEach(SseClient::close));
        clients.clear();
    }
}
