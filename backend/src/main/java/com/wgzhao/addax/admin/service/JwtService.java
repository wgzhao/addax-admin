package com.wgzhao.addax.admin.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * JWT服务类，负责生成、解析和校验JWT令牌。
 * 提供令牌生成、提取声明、校验有效性等功能。
 */
@Slf4j
@Component
public class JwtService
{
    /**
     * JWT密钥（建议生产环境使用更安全的方式管理）
     */
    public final static String SECRET = "4017CCCC60E17DE5C84CF03C6CBE559413EA1606";
    /**
     * JWT签名密钥对象
     */
    private final static SecretKey KEY = Keys.hmacShaKeyFor(SECRET.getBytes());
    /**
     * 令牌过期时间（毫秒）
     */
    @Value("${jwt.expiration}")
    private int accessTokenExpiration;

    /**
     * 提取JWT中的指定声明（如用户名、过期时间等）
     *
     * @param token JWT令牌
     * @param claimsResolver 声明解析函数
     * @param <T> 返回类型
     * @return 声明值
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver)
    {
        final Claims claims = extractAllClaims(token);
        if (claims == null) {
            return null;
        }
        return claimsResolver.apply(claims);
    }

    /**
     * 生成JWT令牌（仅包含用户名）
     *
     * @param username 用户名
     * @return JWT令牌字符串
     */
    public String generateToken(String username)
    {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, username);
    }

    /**
     * 生成JWT令牌（可包含自定义声明）
     *
     * @param claims 自定义声明
     * @param username 用户名
     * @return JWT令牌字符串
     */
    public String createToken(Map<String, Object> claims, String username)
    {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + accessTokenExpiration);
        // 构建JWT令牌
        return Jwts.builder()
            .claims(claims)
            .subject(username)
            .issuedAt(new Date())
            .expiration(expiryDate)
            .signWith(KEY)
            .compact();
    }

    /**
     * 从请求中解析出JWT令牌（支持 Authorization/Bearer 格式）
     *
     * @param request HTTP请求对象
     * @return JWT令牌字符串或null
     */
    public String resolveToken(HttpServletRequest request)
    {
        String bearerToken;
        bearerToken = request.getHeader("Authorization");
        if (!StringUtils.hasText(bearerToken)) {
            bearerToken = request.getHeader("authorization");
        }
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 提取JWT令牌中的用户名
     *
     * @param token JWT令牌
     * @return 用户名
     */
    public String extractUsername(String token)
    {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * 提取JWT令牌中的过期时间
     *
     * @param token JWT令牌
     * @return 过期时间
     */
    private Date extractExpiration(String token)
    {
        return extractClaim(token, Claims::getExpiration);
    }

    /**
     * 判断令牌是否已过期
     *
     * @param token JWT令牌
     * @return 是否过期
     */
    public boolean isTokenExpired(String token)
    {
        return extractExpiration(token).before(new Date());
    }

    /**
     * 解析JWT令牌，获取所有声明
     *
     * @param jwtToken JWT令牌
     * @return Claims对象或null
     */
    private Claims extractAllClaims(String jwtToken)
    {
        try {
            // 解析并校验JWT令牌
            return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(jwtToken)
                .getPayload();
        }
        catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        }
        catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        }
        catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        }
        catch (SignatureException ex) {
            log.error("JWT signature does not match locally computed signature");
        }
        catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty");
        }
        return null;
    }
}
