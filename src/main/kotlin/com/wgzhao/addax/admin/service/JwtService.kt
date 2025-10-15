package com.wgzhao.addax.admin.service

import io.github.oshai.kotlinlogging.KotlinLogging
import io.jsonwebtoken.*
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.security.SignatureException
import jakarta.servlet.http.HttpServletRequest
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Component
import org.springframework.util.StringUtils
import java.util.*
import java.util.function.Function
import javax.crypto.SecretKey

/**
 * JWT服务类，负责生成、解析和校验JWT令牌。
 * 提供令牌生成、提取声明、校验有效性等功能。
 */
@Component
class JwtService {
    private val log = KotlinLogging.logger {  }
    /** 令牌过期时间（毫秒）  */
    @Value("\${jwt.expiration}")
    private val accessTokenExpiration = 0

    /**
     * 提取JWT中的指定声明（如用户名、过期时间等）
     * @param token JWT令牌
     * @param claimsResolver 声明解析函数
     * @param <T> 返回类型
     * @return 声明值
    </T> */
    fun <T> extractClaim(token: String?, claimsResolver: Function<Claims?, T?>): T? {
        val claims = extractAllClaims(token)
        if (claims == null) {
            return null
        }
        return claimsResolver.apply(claims)
    }

    /**
     * 生成JWT令牌（仅包含用户名）
     * @param username 用户名
     * @return JWT令牌字符串
     */
    fun generateToken(username: String?): String? {
        val claims: MutableMap<String?, Any?> = HashMap<String?, Any?>()
        return createToken(claims, username)
    }

    /**
     * 生成JWT令牌（可包含自定义声明）
     * @param claims 自定义声明
     * @param username 用户名
     * @return JWT令牌字符串
     */
    fun createToken(claims: MutableMap<String?, Any?>?, username: String?): String? {
        val now = Date()
        val expiryDate = Date(now.getTime() + accessTokenExpiration)
        // 构建JWT令牌
        return Jwts.builder()
            .claims(claims)
            .subject(username)
            .issuedAt(Date())
            .expiration(expiryDate)
            .signWith(KEY)
            .compact()
    }

    /**
     * 从请求中解析出JWT令牌（支持 Authorization/Bearer 格式）
     * @param request HTTP请求对象
     * @return JWT令牌字符串或null
     */
    fun resolveToken(request: HttpServletRequest): String? {
        var bearerToken: String?
        bearerToken = request.getHeader("Authorization")
        if (!StringUtils.hasText(bearerToken)) {
            bearerToken = request.getHeader("authorization")
        }
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7)
        }
        return null
    }

    /**
     * 校验令牌是否有效（用户名匹配且未过期）
     * @param token JWT令牌
     * @param username 用户名
     * @return 是否有效
     */
    fun validateToken(token: String?, username: String?): Boolean {
        val tokenUsername = extractUsername(token)
        return (tokenUsername != null && tokenUsername == username && !isTokenExpired(token))
    }

    /**
     * 校验令牌是否有效（用户名匹配且未过期）
     * @param token JWT令牌
     * @param userDetails 用户信息
     * @return 是否有效
     */
    fun validateToken(token: String?, userDetails: UserDetails): Boolean {
        val username = extractUsername(token)
        return (username != null && username == userDetails.getUsername() && !isTokenExpired(token))
    }

    /**
     * 提取JWT令牌中的用户名
     * @param token JWT令牌
     * @return 用户名
     */
    fun extractUsername(token: String?): String? {
        return extractClaim<String?>(token, Function { obj: Claims? -> obj!!.getSubject() })
    }

    /**
     * 提取JWT令牌中的过期时间
     * @param token JWT令牌
     * @return 过期时间
     */
    private fun extractExpiration(token: String?): Date? {
        return extractClaim<Date?>(token, Function { obj: Claims? -> obj!!.getExpiration() })
    }

    /**
     * 判断令牌是否已过期
     * @param token JWT令牌
     * @return 是否过期
     */
    fun isTokenExpired(token: String?): Boolean {
        return extractExpiration(token)!!.before(Date())
    }

    /**
     * 解析JWT令牌，获取所有声明
     * @param jwtToken JWT令牌
     * @return Claims对象或null
     */
    private fun extractAllClaims(jwtToken: String?): Claims? {
        try {
            // 解析并校验JWT令牌
            return Jwts.parser()
                .verifyWith(KEY)
                .build()
                .parseSignedClaims(jwtToken)
                .getPayload()
        } catch (ex: ExpiredJwtException) {
            log.error(ex) { "Expired JWT token" }
        } catch (ex: UnsupportedJwtException) {
            log.error(ex) { "Unsupported JWT token" }
        } catch (ex: MalformedJwtException) {
            log.error(ex) { "Invalid JWT token" }
        } catch (ex: SignatureException) {
            log.error(ex) { "JWT signature does not match locally computed signature" }
        } catch (ex: IllegalArgumentException) {
            log.error(ex) { "JWT claims string is empty" }
        }
        return null
    }

    companion object {
        /** JWT密钥（建议生产环境使用更安全的方式管理）  */
        const val SECRET: String = "4017CCCC60E17DE5C84CF03C6CBE559413EA1606"

        /** JWT签名密钥对象  */
        private val KEY: SecretKey = Keys.hmacShaKeyFor(SECRET.toByteArray())
    }
}
