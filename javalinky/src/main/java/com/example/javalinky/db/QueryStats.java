package com.example.javalinky.db;

import org.hibernate.SessionEventListener;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * 统计“当前请求发了多少条 SQL、一共花了多久”。
 *
 * 两个钩子配合：
 *   - StatementInspector.inspect()：Hibernate 每次要执行 SQL 都先调它，用来计数、记下 SQL 文本
 *   - SessionEventListener：jdbcExecuteStatementStart/End 给出真正的执行耗时
 *
 * 两个类都由 Hibernate 自己实例化（在 persistence.xml 里配类名），
 * 所以状态只能放在静态的 ThreadLocal 上。
 */
public class QueryStats implements StatementInspector {

    /** 用 ThreadLocal 是因为每个请求在自己的线程里（虚拟线程同样适用） */
    private static final ThreadLocal<Counter> CURRENT = new ThreadLocal<>();

    public static class Counter {
        public int count;
        public long nanos;
        public String slowest;
        public long slowestNanos;
        String lastSql;
        long startedAt;
    }

    public static void start() {
        CURRENT.set(new Counter());
    }

    public static Counter stop() {
        Counter counter = CURRENT.get();
        CURRENT.remove();
        return counter == null ? new Counter() : counter;
    }

    public static Counter current() {
        return CURRENT.get();
    }

    @Override
    public String inspect(String sql) {
        Counter counter = CURRENT.get();
        if (counter != null) {
            counter.count++;
            counter.lastSql = sql;
        }
        return sql;         // 必须原样返回，否则就改了 SQL
    }

    /** 记录单条查询的耗时 */
    public static void record(String sql, long nanos) {
        Counter counter = CURRENT.get();
        if (counter == null) return;
        counter.nanos += nanos;
        if (nanos > counter.slowestNanos) {
            counter.slowestNanos = nanos;
            counter.slowest = sql;
        }
    }

    /**
     * 给 Hibernate 的 hibernate.session.events.auto 用：
     * 它会为每个 Session 新建一个实例，所以这里不能存跨请求的状态。
     */
    public static class TimingListener implements SessionEventListener {

        @Override
        public void jdbcExecuteStatementStart() {
            Counter counter = CURRENT.get();
            if (counter != null) counter.startedAt = System.nanoTime();
        }

        @Override
        public void jdbcExecuteStatementEnd() {
            Counter counter = CURRENT.get();
            if (counter == null || counter.startedAt == 0) return;
            record(counter.lastSql, System.nanoTime() - counter.startedAt);
            counter.startedAt = 0;
        }
    }
}
