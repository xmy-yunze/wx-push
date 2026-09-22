package com.wxpush.support;

import com.wxpush.config.WxProperties;
import com.wxpush.gateway.WxApiClient;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 手写 stub —— 替掉 {@link WxApiClient} 的真实 HTTP 调用。
 *
 * <p>本项目的测试风格是<b>不用 Mockito，手写 stub</b>：被测类的行为完全真实，
 * 只有「会发网络请求的那一层」被替换。这样测出来的结论更可信 ——
 * 例如并发只拉一次 token，验证的是 {@code AccessTokenManager} 真实的锁行为，
 * 而不是「mock 被调了几次」。</p>
 *
 * <p>用法：把失败异常塞进队列，第 N 次调用就会抛第 N 个异常（队列空则成功）。
 * 这样可以精确验证「第一次 40001、第二次成功」这类重试路径。</p>
 */
public class StubWxApiClient extends WxApiClient {

    private final AtomicInteger fetchCount = new AtomicInteger();
    private final AtomicInteger createMenuCalls = new AtomicInteger();
    private final AtomicInteger getMenuCalls = new AtomicInteger();
    private final AtomicInteger deleteMenuCalls = new AtomicInteger();

    /** 按调用次序消费的失败队列；元素为 null 表示这次成功 */
    private final Deque<RuntimeException> createMenuFailures = new ArrayDeque<>();
    private final Deque<RuntimeException> getMenuFailures = new ArrayDeque<>();

    private volatile String lastMenuJson;
    private volatile String menuResponse = "{\"menu\":{\"button\":[]}}";
    private volatile long expiresInSeconds = 7200L;

    /** 让拉取 token 稍微耗时，使并发测试真的能撞在一起（否则测不出竞争） */
    private volatile long fetchDelayMillis = 50L;

    public StubWxApiClient() {
        // 父类构造需要这三样。stub 覆写了全部对外方法，所以它们不会被真正使用
        super(RestClient.builder(), new ObjectMapper(),
                new WxProperties("stub-token", "stub-app-id", "stub-app-secret"));
    }

    @Override
    public TokenResult fetchAccessToken() {
        fetchCount.incrementAndGet();
        sleep(fetchDelayMillis);
        return new TokenResult("stub-token-" + fetchCount.get(), expiresInSeconds);
    }

    @Override
    public void createMenu(String accessToken, String menuJson) {
        createMenuCalls.incrementAndGet();
        lastMenuJson = menuJson;
        throwIfQueued(createMenuFailures);
    }

    @Override
    public String getMenu(String accessToken) {
        getMenuCalls.incrementAndGet();
        throwIfQueued(getMenuFailures);
        return menuResponse;
    }

    @Override
    public void deleteMenu(String accessToken) {
        deleteMenuCalls.incrementAndGet();
    }

    // ---------------- 测试用配置与断言辅助 ----------------

    /** 让第 N 次 createMenu 抛指定异常 */
    public void queueCreateMenuFailure(RuntimeException failure) {
        createMenuFailures.add(failure);
    }

    /** 让第 N 次 getMenu 抛指定异常 */
    public void queueGetMenuFailure(RuntimeException failure) {
        getMenuFailures.add(failure);
    }

    public void setMenuResponse(String menuResponse) {
        this.menuResponse = menuResponse;
    }

    public void setExpiresInSeconds(long expiresInSeconds) {
        this.expiresInSeconds = expiresInSeconds;
    }

    public void setFetchDelayMillis(long fetchDelayMillis) {
        this.fetchDelayMillis = fetchDelayMillis;
    }

    public int fetchCount() {
        return fetchCount.get();
    }

    public int createMenuCalls() {
        return createMenuCalls.get();
    }

    public int getMenuCalls() {
        return getMenuCalls.get();
    }

    public int deleteMenuCalls() {
        return deleteMenuCalls.get();
    }

    public String lastMenuJson() {
        return lastMenuJson;
    }

    private static void throwIfQueued(Deque<RuntimeException> failures) {
        if (failures.isEmpty()) {
            return;
        }
        RuntimeException failure = failures.poll();
        if (failure != null) {
            throw failure;
        }
    }

    private static void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
