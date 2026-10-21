package com.example.javalinky.web;

import io.javalin.websocket.WsContext;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「谁在看这篇文章、谁在打字」（第 21 章的 WebSocket 示例）。
 *
 * 和 LiveHub 的区别就是本章想说明的那件事：
 * 这里的消息是<b>双向</b>的 —— 浏览器要往上报「我在输入」，
 * 服务器再广播给同一篇文章的其他人。SSE 做不到这一点（它只能服务器往下发）。
 *
 * WebSocket 的连接管理比 SSE 省心：协议自带 ping/pong 心跳，
 * 断开时 onClose 一定会被调用，不需要自己做 LiveHub 那套心跳兜底。
 */
public final class PostPresence {

    private final Map<Long, Set<WsContext>> viewers = new ConcurrentHashMap<>();

    public void join(long postId, WsContext ctx) {
        viewers.computeIfAbsent(postId, id -> ConcurrentHashMap.newKeySet()).add(ctx);
        broadcastCount(postId);
    }

    public void leave(long postId, WsContext ctx) {
        viewers.compute(postId, (id, set) -> {
            if (set == null) return null;
            set.remove(ctx);
            return set.isEmpty() ? null : set;
        });
        broadcastCount(postId);
    }

    /**
     * 转发「某人正在输入」给同一篇文章的其他人（不回给自己）。
     *
     * 注意是按 sessionId 比，不是按对象引用。
     * <b>Javalin 每个事件都会新建一个 WsContext 对象</b> ——
     * onMessage 收到的那个和 onConnect 时存进来的那个不是同一个实例，
     * 写成 ctx != sender 的话发起者也会收到自己的消息。
     */
    public void typing(long postId, WsContext sender, String username) {
        String senderId = sender.sessionId();
        each(postId, ctx -> {
            if (!ctx.sessionId().equals(senderId)) {
                ctx.send(Map.of("type", "typing", "username", username));
            }
        });
    }

    public int viewerCount(long postId) {
        Set<WsContext> set = viewers.get(postId);
        return set == null ? 0 : set.size();
    }

    private void broadcastCount(long postId) {
        int count = viewerCount(postId);
        each(postId, ctx -> ctx.send(Map.of("type", "viewers", "count", count)));
    }

    private void each(long postId, java.util.function.Consumer<WsContext> action) {
        Set<WsContext> set = viewers.get(postId);
        if (set == null) return;
        for (WsContext ctx : set) {
            try {
                action.accept(ctx);
            } catch (Exception e) {
                // 一个连接发不出去，不能影响其他人
                set.remove(ctx);
            }
        }
    }
}
