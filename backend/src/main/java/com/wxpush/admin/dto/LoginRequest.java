package com.wxpush.admin.dto;

/**
 * 登录请求体，对应 {@code POST /api/auth/login} 的 JSON 入参。
 *
 * <p>契约示例：</p>
 * <pre>
 *   { "username": "admin", "password": "******" }
 * </pre>
 *
 * <p>用 {@code record} 而不是带 setter 的类：登录入参是「一次性只读输入」，
 * 出了 Controller 就不该再被改动。反序列化由 Jackson 按参数名匹配（项目编译时开了
 * {@code -parameters}，所以不需要额外写 {@code @JsonProperty}）。</p>
 *
 * <p>这里<b>刻意不做</b>「长度 / 非空」校验：一旦密码规则写在入参上，
 * 将来改规则就要动这个类。非空与格式判断统一交给
 * {@link com.wxpush.admin.service.AuthService}，错误信息也好集中管理。</p>
 */
public record LoginRequest(String username, String password) {
}
