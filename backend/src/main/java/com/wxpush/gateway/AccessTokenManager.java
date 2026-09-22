package com.wxpush.gateway;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * access_token 管理器 —— 全局唯一的 token 提供者。
 *
 * <h3>为什么必须收口到一个组件？</h3>
 * <p>微信的 access_token 是<b>全局唯一</b>的：<b>每重新获取一次，上一个立刻失效</b>。
 * 如果有两处代码各自去拉 token，它们就会互相把对方挤掉，表现为随机的
 * {@code 40001 invalid credential} —— 而且往往是「单机测试没问题、一上量就报错」，
 * 现场极难排查。所以规则是：<b>全项目只有这一个地方能调 {@code /token}</b>。</p>
 *
 * <h3>并发控制：为什么要双检锁</h3>
 * <p>最朴素的写法是给整个方法加 {@code synchronized}，但那样<b>每次</b>读取 token
 * 都要抢锁 —— 而读 token 是高频操作（每个管理端请求都要）。双检锁把开销降到最低：</p>
 * <ol>
 *   <li><b>第一次检查（无锁）</b>：绝大多数请求在这里就拿到缓存返回，完全不进锁</li>
 *   <li><b>加锁</b>：只有「缓存为空或即将过期」时才进锁</li>
 *   <li><b>第二次检查（有锁）</b>：进锁后再看一次 —— 因为抢锁期间可能已有别的线程刷新完了，
 *       此时直接用新的，不再重复拉取。这一步是双检锁<b>存在的全部意义</b></li>
 * </ol>
 *
 * <p>⚠️ {@code holder} 必须声明为 {@code volatile}。否则第一次检查（在锁外）可能读到
 * 一个「引用已赋值、但对象内部尚未完全初始化」的中间状态 —— 这是双检锁最著名的坑，
 * 也是它在 Java 5 之前不可用的原因。</p>
 *
 * <h3>为什么网络请求写在锁里面</h3>
 * <p>把网络请求挪到锁外、只锁赋值，看起来性能更好，但会破坏「全局唯一」这个前提：
 * 两个线程同时发现缓存过期，就会各拉一次 token，后拉的把先拉的挤掉，
 * 而先拉的那次刚好返回给了调用方 —— 于是拿到一个已经失效的 token。
 * 宁可让少数几个线程多等一次网络往返（每 2 小时才发生一次），也不能让 token 互相挤掉。</p>
 */
@Slf4j
@Component
public class AccessTokenManager {

    /**
     * 提前刷新窗口。
     *
     * <p>为什么要提前？如果卡在到期那一刻才刷新，那么在「发现过期 → 新 token 到手」
     * 这段时间里发出的请求会带着一个刚失效的 token 打过去，白挨一次 40001。
     * 留 5 分钟余量足以覆盖网络抖动与时钟偏差。</p>
     */
    private static final Duration REFRESH_AHEAD = Duration.ofMinutes(5);

    /** 兜底下限：万一微信返回一个极小的 expires_in，也不至于每次调用都去刷新 */
    private static final Duration MIN_TTL = Duration.ofMinutes(1);

    /** 专用锁对象。不用 {@code this}，避免与外部可能对该 bean 实例加的锁互相干扰 */
    private final Object refreshLock = new Object();

    private final WxApiClient wxApiClient;

    /** 缓存的 token。⚠️ volatile 是双检锁正确性的前提，详见类注释 */
    private volatile TokenHolder holder;

    public AccessTokenManager(WxApiClient wxApiClient) {
        this.wxApiClient = wxApiClient;
    }

    /**
     * 取一个当前有效的 access_token。
     *
     * <p>缓存有效时直接返回（无锁快路径）；否则在锁内刷新一次。</p>
     */
    public String accessToken() {
        // 第一次检查：无锁。正常运行时所有请求都走这条路
        TokenHolder cached = holder;
        if (cached != null && !cached.needsRefresh()) {
            return cached.value();
        }

        synchronized (refreshLock) {
            // 第二次检查：有锁。抢锁期间可能已被别的线程刷新，那就直接用，不重复拉
            cached = holder;
            if (cached != null && !cached.needsRefresh()) {
                return cached.value();
            }
            TokenHolder fresh = fetch();
            holder = fresh;
            return fresh.value();
        }
    }

    /**
     * 丢弃缓存，让下次调用重新获取。
     *
     * <p>什么时候需要？收到 40001 / 42001 时 —— 说明我们的 token 被别的途径挤掉了
     * （比如运营者在公众平台手动刷新过）。此时缓存里那个已经废了，必须丢弃重取。</p>
     *
     * <p>这里不用加锁：{@code holder} 是 volatile 的，单次赋值本身就是原子的。
     * 最坏情况是两个线程同时丢弃，代价只是多拉一次 token，不影响正确性。</p>
     */
    public void invalidate() {
        holder = null;
        log.info("已丢弃缓存的 access_token，下次调用将重新获取");
    }

    /** 真正去微信拉一次 token，并算出下次该刷新的时刻 */
    private TokenHolder fetch() {
        WxApiClient.TokenResult result = wxApiClient.fetchAccessToken();

        Duration ttl = Duration.ofSeconds(Math.max(result.expiresInSeconds(), MIN_TTL.getSeconds()));
        Instant refreshAt = Instant.now().plus(ttl).minus(REFRESH_AHEAD);

        // 小概率情形：微信给的有效期短于提前量，会导致 refreshAt 落在过去（等于每次都刷新）。
        // 这里兜一下，至少保证缓存能存活 MIN_TTL。
        Instant floor = Instant.now().plus(MIN_TTL);
        if (refreshAt.isBefore(floor)) {
            refreshAt = floor;
        }

        // ⚠️ 只记录「还有多久」，绝不打印 token 本身（日志文件可能被导出、被采集）
        log.info("已获取新的 access_token（有效期 {} 秒），将在 {} 秒后提前刷新",
                ttl.getSeconds(), Duration.between(Instant.now(), refreshAt).getSeconds());

        return new TokenHolder(result.accessToken(), refreshAt);
    }

    /** token 值 + 下次刷新时刻 */
    private record TokenHolder(String value, Instant refreshAt) {

        /** 是否已到该刷新的时刻（含提前量） */
        boolean needsRefresh() {
            return Instant.now().isAfter(refreshAt);
        }
    }
}
