package com.wgzhao.addax.admin.alert;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wgzhao.addax.admin.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM envelope for channel credentials.
 *
 * <p>The key comes from {@code ALERT_SECRET_KEY}. When it is absent the key is
 * derived from {@code JWT_SECRET} so an upgrade does not silently disable
 * alerting; operators are expected to set the dedicated variable in production.
 * The {@code v1} prefix leaves room for rotation.</p>
 */
@Component
@Slf4j
public class AlertSecretCipher
{
    private static final String PREFIX = "v1";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final ObjectMapper objectMapper;
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public AlertSecretCipher(ObjectMapper objectMapper,
        @Value("${ALERT_SECRET_KEY:}") String alertSecretKey,
        @Value("${JWT_SECRET:}") String jwtSecret)
    {
        this.objectMapper = objectMapper;
        String source = alertSecretKey == null ? "" : alertSecretKey.trim();
        if (source.isEmpty()) {
            log.warn("ALERT_SECRET_KEY is not set; deriving the alert config key from JWT_SECRET. "
                + "Set ALERT_SECRET_KEY in production before storing alert channels.");
            source = jwtSecret == null ? "" : jwtSecret.trim();
        }
        this.key = source.isEmpty() ? null : sha256(("addax-alert-config:" + source).getBytes(StandardCharsets.UTF_8));
    }

    public boolean isConfigured()
    {
        return key != null;
    }

    public String encrypt(JsonNode config)
    {
        requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(objectMapper.writeValueAsBytes(config));
            return PREFIX + ":" + Base64.getEncoder().encodeToString(iv)
                + ":" + Base64.getEncoder().encodeToString(encrypted);
        }
        catch (Exception ex) {
            log.error("Failed to encrypt alert channel config", ex);
            throw new ApiException(500, "告警通道配置加密失败");
        }
    }

    public JsonNode decrypt(String ciphertext)
    {
        requireKey();
        try {
            String[] parts = ciphertext == null ? new String[0] : ciphertext.split(":", 3);
            if (parts.length != 3 || !PREFIX.equals(parts[0])) {
                throw new IllegalStateException("unsupported ciphertext");
            }
            byte[] iv = Base64.getDecoder().decode(parts[1]);
            byte[] encrypted = Base64.getDecoder().decode(parts[2]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(TAG_BITS, iv));
            return objectMapper.readTree(cipher.doFinal(encrypted));
        }
        catch (Exception ex) {
            log.error("Failed to decrypt alert channel config; ALERT_SECRET_KEY may have changed", ex);
            throw new ApiException(500, "告警通道配置解密失败，请检查 ALERT_SECRET_KEY 是否与创建通道时一致");
        }
    }

    private void requireKey()
    {
        if (key == null) {
            throw new ApiException(500, "未配置 ALERT_SECRET_KEY，无法安全保存告警通道配置");
        }
    }

    private static byte[] sha256(byte[] input)
    {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        }
        catch (Exception ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
