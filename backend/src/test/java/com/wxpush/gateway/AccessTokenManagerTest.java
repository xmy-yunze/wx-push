package com.wxpush.gateway;

import com.wxpush.support.StubWxApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * access_token 管理器测试。
 *
 * <p>这里验证的是<b>并发正确性</b>，而不是「方法被调了几次」——
 * 因为这里最关键的风险不是逻辑分支，而是并发下重复拉取：
 * 一旦重复拉取，后拉的会把先拉的挤掉，导致调用方拿到一个已失效的 token。</p>
 */
@DisplayName("access_token 管理器（单例 + 双检锁）")
class AccessTokenManagerTest {

    @Test
    @DisplayName("缓存有效时反复调用只拉取一次")
    void cachesToken() {
        StubWxApiClient api = new StubWxApiClient();
        AccessTokenManager manager = new AccessTokenManager(api);

        String first = manager.accessToken();
        String second = manager.accessToken();
        String third = manager.accessToken();

        assertEquals(first, second);
        assertEquals(first, third);
        assertEquals(1, api.fetchCount(), "缓存有效时不该重复拉取 —— 每拉一次都会挤掉上一个 token");
    }

    @Test
    @DisplayName("invalidate 之后会重新拉取")
    void refetchesAfterInvalidate() {
        StubWxApiClient api = new StubWxApiClient();
        AccessTokenManager manager = new AccessTokenManager(api);

        String before = manager.accessToken();
        manager.invalidate();
        String after = manager.accessToken();

        assertEquals(2, api.fetchCount());
        assertEquals("stub-token-1", before);
        assertEquals("stub-token-2", after, "丢弃缓存后应拿到新 token");
    }

    @Test
    @DisplayName("32 个线程同时首次调用 —— 只拉取一次")
    void concurrentCallFetchesOnlyOnce() throws Exception {
        StubWxApiClient api = new StubWxApiClient();
        // 让拉取耗时 50ms，确保线程真的撞在一起（否则可能碰巧串行通过，测了个寂寞）
        api.setFetchDelayMillis(50L);
        AccessTokenManager manager = new AccessTokenManager(api);

        int threadCount = 32;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                futures.add(pool.submit(() -> {
                    // 所有线程在这里等着，直到一声令下同时冲进去 —— 制造最大竞争
                    startGate.await();
                    return manager.accessToken();
                }));
            }
            startGate.countDown();

            String expected = futures.get(0).get(10, TimeUnit.SECONDS);
            for (Future<String> future : futures) {
                assertEquals(expected, future.get(10, TimeUnit.SECONDS),
                        "所有线程必须拿到同一个 token");
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, api.fetchCount(),
                "并发时只允许拉取一次 —— 这是双检锁第二次检查存在的意义");
    }

    @Test
    @DisplayName("并发持有同一个 token，不会出现两个不同值")
    void concurrentCallsShareSameToken() throws Exception {
        StubWxApiClient api = new StubWxApiClient();
        api.setFetchDelayMillis(30L);
        AccessTokenManager manager = new AccessTokenManager(api);

        // 先预热一次，让缓存有值；后续并发读都必须命中同一个值
        String warmed = manager.accessToken();

        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 16; i++) {
                futures.add(pool.submit(manager::accessToken));
            }
            for (Future<String> future : futures) {
                assertEquals(warmed, future.get(10, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, api.fetchCount(), "预热之后并发读不应再拉取");
    }
}
