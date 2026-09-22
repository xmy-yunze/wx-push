package com.wxpush.gateway;

/**
 * 调用微信服务端 HTTP API 失败。
 *
 * <p>⚠️ 与「微信推送报文解析失败」是两回事，别混在一起：</p>
 * <ul>
 *   <li><b>收</b>（微信 → 我们）：XML 报文，解析失败只需记日志 —— 重试也救不回来</li>
 *   <li><b>发</b>（我们 → 微信）：JSON 调用，失败<b>必须让运营者看见</b>，
 *       因为多半是配置问题（IP 白名单、密钥错、菜单不合法），不看提示无法自行修复</li>
 * </ul>
 *
 * <p>所以这个异常的 message 一定是<b>可直接展示给运营者的中文</b>，
 * 并且尽量给出「怎么修」而不是只报错误码。</p>
 */
public class WxApiException extends RuntimeException {

    /** 网络层面失败（连不上、超时、响应非 JSON）时用它，因为微信没返回 errcode */
    public static final int ERR_NETWORK = -1;

    /** 微信返回的 errcode；网络层失败时为 {@link #ERR_NETWORK} */
    private final int errCode;

    /** 微信返回的 errmsg 原文（可能为空） */
    private final String errMsg;

    public WxApiException(int errCode, String errMsg, String hint) {
        super(buildMessage(errCode, errMsg, hint));
        this.errCode = errCode;
        this.errMsg = errMsg == null ? "" : errMsg;
    }

    /**
     * 拼装最终提示。
     *
     * <p>格式：{@code 人话提示（微信返回 40164：invalid ip xxx）} ——
     * 前半句给运营者看，括号里给排查问题的人看。两者都保留，
     * 因为只给错误码运营者看不懂，只给人话提示工程师拿不到线索。</p>
     */
    private static String buildMessage(int errCode, String errMsg, String hint) {
        String raw = (errMsg == null || errMsg.isBlank()) ? "" : "：" + errMsg;
        String detail = "（微信返回 " + errCode + raw + "）";
        return (hint == null || hint.isBlank())
                ? "调用微信接口失败" + detail
                : hint + detail;
    }

    public int getErrCode() {
        return errCode;
    }

    public String getErrMsg() {
        return errMsg;
    }

    /**
     * 该错误是否属于「access_token 失效」——调用方据此决定要不要丢弃缓存再重试一次。
     *
     * <p>40001（token 无效）与 42001（token 过期）都归到这一类。
     * 出现它们通常说明别的调用方刷新过 token，把我们的挤掉了。</p>
     */
    public boolean isTokenInvalid() {
        return errCode == 40001 || errCode == 42001;
    }
}
