package com.example.javalinky.web;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * 按 key 限流的令牌桶（第 19 章）。
 *
 * 这是整个应用里少数几个“被多个请求线程同时读写”的对象之一：
 * 一个实例装在应用上，所有请求共用。所以每一次状态变更都必须是原子的 ——
 * 先读后写的那种写法在这里一定会漏。
 */
public final class RateLimiter {

    /** 桶的状态。做成不可变记录，这样 compute() 里只能整体替换，不会改了一半 */
    private record Bucket(double tokens, long updatedAt) {}

    private final int capacity;           // 桶最多装多少个令牌 = 允许的突发量
    private final double tokensPerNano;   // 补充速度
    private final long idleNanos;         // 多久没动过的桶可以回收
    private final LongSupplier clock;     // 默认 System.nanoTime，测试里换成假时钟

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong callsSincePrune = new AtomicLong();

    /** 每 PRUNE_EVERY 次调用顺手清理一遍，避免 map 无限增长 */
    private static final long PRUNE_EVERY = 1000;

    public RateLimiter(int capacity, double perSecond) {
        this(capacity, perSecond, System::nanoTime);
    }

    /** 时钟可注入：测试里换成假时钟，就不用 sleep 了 */
    public RateLimiter(int capacity, double perSecond, LongSupplier clock) {
        if (capacity < 1) throw new IllegalArgumentException("capacity 至少是 1");
        if (perSecond <= 0) throw new IllegalArgumentException("perSecond 必须大于 0");
        this.capacity = capacity;
        this.tokensPerNano = perSecond / 1_000_000_000.0;
        // 桶从空装满需要的时间，再宽限一倍 —— 之后这个 key 的记录就没有意义了
        this.idleNanos = (long) (capacity / tokensPerNano) * 2;
        this.clock = clock;
    }

    /**
     * 取一个令牌。拿到返回 true，没拿到返回 false（调用方该拒绝这次请求）。
     */
    public boolean tryAcquire(String key) {
        long now = clock.getAsLong();

        // compute() 在这个 key 所在的桶上持锁，映射函数「最多执行一次」，
        // 所以在里面写副作用是安全的。换成 AtomicReference.updateAndGet 就不行了 ——
        // 那个在 CAS 失败时会重试，granted 会被多算。
        boolean[] granted = new boolean[1];
        buckets.compute(key, (k, bucket) -> {
            double tokens = bucket == null
                ? capacity
                : refill(bucket.tokens(), now - bucket.updatedAt());

            if (tokens >= 1.0) {
                granted[0] = true;
                return new Bucket(tokens - 1.0, now);
            }
            return new Bucket(tokens, now);
        });

        if (callsSincePrune.incrementAndGet() % PRUNE_EVERY == 0) prune(now);
        return granted[0];
    }

    /** 这个 key 现在还剩几个令牌（只用于展示和测试，不要拿它做判断——会有竞态） */
    public int remaining(String key) {
        Bucket bucket = buckets.get(key);
        if (bucket == null) return capacity;
        return (int) refill(bucket.tokens(), clock.getAsLong() - bucket.updatedAt());
    }

    /** 登录成功之后把这个 key 的记录清掉，别让之前的失败次数拖累正常用户 */
    public void reset(String key) {
        buckets.remove(key);
    }

    /** 当前记着多少个 key。用于观测和测试清理逻辑 */
    public int size() {
        return buckets.size();
    }

    private double refill(double tokens, long elapsedNanos) {
        if (elapsedNanos <= 0) return tokens;
        return Math.min(capacity, tokens + elapsedNanos * tokensPerNano);
    }

    /**
     * 清掉长时间没动过的桶。
     * 不清的话这个 map 会按「攻击者给的 key 数量」无限增长 —— 本身就是一个 DoS 入口。
     */
    private void prune(long now) {
        for (Iterator<Map.Entry<String, Bucket>> it = buckets.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Bucket> e = it.next();
            if (now - e.getValue().updatedAt() > idleNanos) it.remove();
        }
    }
}
