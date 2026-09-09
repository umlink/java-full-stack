package com.example.bootserver.security;

import com.example.bootserver.controller.dto.LoginResponse;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * 仅负责签发访问令牌，不负责从请求头提取或认证令牌。
 *
 * M1-07 会在过滤器中校验 Bearer token；本卡只建立“登录成功后签名、设置过期时间”的最小闭环。
 */
@Service
public class JwtTokenService {

    // SecretKey 是签名用的原始密钥对象；由配置中的 Base64 文本在构造时转换一次，避免每次登录重复解码。
    private final SecretKey signingKey;
    // 令牌有效期来自 application.yml，例如 PT30M 表示 30 分钟。
    private final Duration accessTokenTtl;
    // 统一从 UTC 时钟获取当前时间，避免服务器本地时区影响 JWT 的签发和过期时刻。
    private final Clock clock;

    public JwtTokenService(JwtProperties properties) {
        // 先校验配置，再创建服务；配置错误会在启动时暴露，而不是等到用户登录才失败。
        this.accessTokenTtl = requirePositiveTtl(properties.getAccessTokenTtl());
        this.signingKey = decodeSigningKey(properties.getSecretBase64());
        this.clock = Clock.systemUTC();
    }

    /**
     * 签发 HS256 访问令牌，payload 只放用户 ID（标准 sub）和 iat/exp，避免把角色或隐私数据固化进令牌。
     */
    public LoginResponse issueAccessToken(Long userId) {
        // iat（issued at）和 exp（expiration）均以 UTC 的绝对时间表示，不受时区显示格式影响。
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(accessTokenTtl);
        String token = Jwts.builder()
                // JWT 的 sub 是标准主体字段；这里保存用户 ID，M1-07 校验令牌后据此识别当前用户。
                .subject(String.valueOf(userId))
                // JJWT Builder 使用旧版 Date API；业务时间仍用 Instant 计算，再在边界转换。
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                // 默认使用 HMAC-SHA256（HS256）：服务端用同一把密钥签发和验签，因此密钥绝不能泄露给前端。
                .signWith(signingKey)
                // 将 Header、Payload、Signature 编码并拼成 xxx.yyy.zzz 形式的紧凑 JWT 字符串。
                .compact();
        // 响应中的 expiresAt 方便前端判断令牌何时需要重新登录；它与 JWT 内的 exp 是同一个时刻。
        return new LoginResponse(token, expiresAt);
    }

    private SecretKey decodeSigningKey(String secretBase64) {
        try {
            // 环境变量通常只能方便地保存文本；Base64 不是加密，而是把随机字节安全地表示成文本。
            // 解码后交给 JJWT 创建 HMAC 密钥，并校验 HS256 至少需要 256 bit（32 字节）。
            return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretBase64));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET_BASE64 必须是至少 32 字节密钥的 Base64 编码", exception);
        }
    }

    private Duration requirePositiveTtl(Duration ttl) {
        // 过期时间必须是正数；零或负数令牌一签发就失效，通常说明配置填写错误。
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalStateException("app.jwt.access-token-ttl 必须大于零");
        }
        return ttl;
    }
}
