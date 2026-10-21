package com.example.javalinky;

import com.example.javalinky.web.RateLimiter;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发代码的测试要回答一个问题：「很多线程同时来，结果还对不对？」
 * 办法是让所有线程在同一个 CountDownLatch 上等齐，然后一起放出去 ——
 * 这样它们真的在同一瞬间撞进同一段代码，而不是先后排队。
 */
class RateLimiterTest {

    /** 假时钟：时间只在测试显式推进时才走，测限流不用 sleep */
    private static final class FakeClock {
        private final AtomicLong nanos = new AtomicLong();
        long now()                 { return nanos.get(); }
        void advanceSeconds(long s) { nanos.addAndGet(s * 1_000_000_000L); }
    }

    @Test
    void 超出容量就拒绝() {
        var clock = new FakeClock();
        var limiter = new RateLimiter(5, 1.0, clock::now);

        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryAcquire("1.2.3.4")).as("第 %d 次应该放行", i + 1).isTrue();
        }
        assertThat(limiter.tryAcquire("1.2.3.4")).as("第 6 次应该被拒").isFalse();
    }

    @Test
    void 不同的key互不影响() {
        var clock = new FakeClock();
        var limiter = new RateLimiter(1, 1.0, clock::now);

        assertThat(limiter.tryAcquire("1.1.1.1")).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1")).isFalse();
        assertThat(limiter.tryAcquire("2.2.2.2")).as("另一个 IP 不该被牵连").isTrue();
    }

    @Test
    void 令牌会随时间补回来() {
        var clock = new FakeClock();
        var limiter = new RateLimiter(3, 1.0, clock::now);   // 每秒补 1 个

        for (int i = 0; i < 3; i++) assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).isFalse();

        clock.advanceSeconds(2);
        assertThat(limiter.tryAcquire("ip")).as("过了 2 秒，补回 2 个").isTrue();
        assertThat(limiter.tryAcquire("ip")).isTrue();
        assertThat(limiter.tryAcquire("ip")).as("只补了 2 个").isFalse();
    }

    @Test
    void 补充不会超过容量() {
        var clock = new FakeClock();
        var limiter = new RateLimiter(3, 1.0, clock::now);

        assertThat(limiter.tryAcquire("ip")).isTrue();
        clock.advanceSeconds(3600);      // 放一小时
        assertThat(limiter.remaining("ip")).as("桶不该溢出").isEqualTo(3);
    }

    /**
     * 本章的核心测试：500 个线程同时冲一个只有 50 个令牌的桶，
     * 放行的次数必须<b>恰好</b>是 50。写成「先读再判断再写」的话，
     * 这个数字会变成 51、54、58…… 每次还不一样。
     *
     * 两个参数是量出来的，不是拍的（量法见第 19 章）：
     *   - <b>平台线程，不是虚拟线程。</b>虚拟线程挤在少数几个载体线程上，
     *     纳秒级的临界区根本碰不到一起 —— 在一台 2 核机器上跑 20 轮，
     *     朴素实现一次都没被抓住。换成平台线程，立刻现形。
     *   - <b>500 个，不是 200 个。</b>同一台机器上 200 个的发现率是 18%，
     *     500 个是 84%。再叠加下面 3 轮，漏网概率降到约 0.4%。
     */
    @Test
    void 五百个线程同时抢五十个令牌只能放行五十次() throws Exception {
        for (int round = 1; round <= 3; round++) {
            int threads = 500;
            int capacity = 50;
            var clock = new FakeClock();                   // 时间不走，排除补充的干扰
            var limiter = new RateLimiter(capacity, 1.0, clock::now);

            var ready = new CountDownLatch(threads);
            var start = new CountDownLatch(1);
            var done = new CountDownLatch(threads);
            var granted = new AtomicInteger();

            for (int i = 0; i < threads; i++) {
                new Thread(() -> {
                    ready.countDown();
                    try {
                        start.await();                      // 在这里等齐
                        if (limiter.tryAcquire("同一个IP")) granted.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                }).start();
            }

            assertThat(ready.await(30, TimeUnit.SECONDS)).as("线程没起齐").isTrue();
            start.countDown();                              // 一起放出去
            assertThat(done.await(30, TimeUnit.SECONDS)).as("不该有线程卡住").isTrue();

            assertThat(granted.get())
                .as("第 %d 轮：多放行一次，就是多漏一次撞库尝试", round)
                .isEqualTo(capacity);
        }
    }

    @Test
    void 闲置的桶会被回收掉() {
        var clock = new FakeClock();
        var limiter = new RateLimiter(2, 1.0, clock::now);

        // 制造一万个不同的 key，模拟伪造来源地址的攻击
        for (int i = 0; i < 10_000; i++) limiter.tryAcquire("ip-" + i);
        assertThat(limiter.size()).as("清理之前当然是满的").isGreaterThan(1000);

        clock.advanceSeconds(3600);
        for (int i = 0; i < 1000; i++) limiter.tryAcquire("活跃的ip");

        assertThat(limiter.size())
            .as("过期的桶应该被清掉，否则这个 map 就是个内存泄漏")
            .isLessThan(100);
    }
}
