package com.example.javalinky;

import org.eclipse.jetty.ee10.servlet.SessionHandler;
import org.eclipse.jetty.http.HttpCookie;

public final class Sessions {

    public static SessionHandler handler(boolean httpsOnly) {
        SessionHandler h = new SessionHandler();
        h.setSessionCookie("JAVALINKY_SESSION");
        h.setHttpOnly(true);                                 // JS 读不到
        h.setSameSite(HttpCookie.SameSite.LAX);              // 挡掉跨站请求携带
        h.setSecureRequestOnly(httpsOnly);                   // 生产环境 true
        h.setMaxInactiveInterval(60 * 60 * 24 * 7);          // 7 天不活动就过期
        return h;
    }

    private Sessions() {}
}
