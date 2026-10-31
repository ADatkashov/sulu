package com.sulu.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Fixed-window per-IP limiter for sensitive auth endpoints (in-memory; swap for a shared store when scaling out). */
@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private record Window(long minute, int[] count) {}
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final int limit;
    private final Clock clock;

    public RateLimitFilter(SuluProperties props, Clock clock) {
        this.limit = props.rateLimit().authPerMinute();
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest r) {
        String p = r.getRequestURI();
        return !(p.startsWith("/api/auth/") && "POST".equals(r.getMethod()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        long minute = clock.millis() / 60_000;
        String key = req.getRemoteAddr() + "|" + req.getRequestURI();
        Window w = windows.compute(key, (k, old) -> (old == null || old.minute != minute) ? new Window(minute, new int[]{1}) : bump(old));
        if (windows.size() > 10_000) windows.entrySet().removeIf(e -> e.getValue().minute < minute);
        if (w.count[0] > limit) {
            res.setStatus(429);
            res.setContentType("application/json");
            res.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}");
            return;
        }
        chain.doFilter(req, res);
    }

    private static Window bump(Window w) { w.count[0]++; return w; }
}
