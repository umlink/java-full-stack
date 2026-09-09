package com.example.bootserver.security;

import com.example.bootserver.controller.dto.LoginResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * 负责 JWT 的签发与声明解析，不负责从 HTTP 请求头提取令牌或制定接口权限。
 *
 * M1-07 的过滤器调用本类完成验签，再把身份写入 SecurityContext；角色加载和授权留给后续卡片。
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

    /**
     * 验签并读取 JWT 的主体用户 ID。
     *
     * {@code parseSignedClaims} 会先检查签名和 exp；签名被改动、令牌过期等情况会抛出 JwtException，
     * 由 M1-07 的过滤器转换为统一 401。这里只解析身份，不查询用户或判断角色，避免越过认证职责。
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        try {
            // sub 在签发时以用户 ID 的字符串形式存入；读取后还原 Long，供 SecurityContext 保存当前用户。
            Long userId = Long.valueOf(claims.getSubject());
            if (userId <= 0) {
                // 签名正确不代表声明语义正确；数据库自增主键只接受正数，不能把 0 或负数作为当前身份。
                throw new MalformedJwtException("JWT 的 sub 必须是正数用户 ID");
            }
            return userId;
        } catch (NumberFormatException exception) {
            throw new MalformedJwtException("JWT 的 sub 必须是用户 ID", exception);
        }
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
