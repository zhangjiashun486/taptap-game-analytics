package org.example.base_server.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具：登录成功后签发 token，后续请求用 token 证明身份。
 * token 里保存：用户名（subject）、userId、角色（role）。
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;                 // 签名密钥（application.properties / 环境变量）

    @Value("${jwt.expire-hours:24}")
    private long expireHours;              // 有效期（小时）

    /** 生成 HMAC 密钥 */
    private SecretKey key() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 生成 token；userId 为空时（管理员）不写 userId 声明 */
    public String generate(Long userId, String username, String role) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expireHours * 3600_000L);
        JwtBuilder builder = Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(expiration);
        if (userId != null) {
            builder.claim("userId", userId);
        }
        return builder.signWith(key()).compact();
    }

    /** 解析并校验 token；无效/过期会抛异常 */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
