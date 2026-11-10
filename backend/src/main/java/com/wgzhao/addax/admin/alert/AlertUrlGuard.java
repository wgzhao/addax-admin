package com.wgzhao.addax.admin.alert;

import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * SSRF guard for outbound alert traffic.
 *
 * <p>IM channels are pinned to the vendor's documented host, so their URLs cannot
 * be pointed at internal services at all. Generic webhooks may target arbitrary
 * hosts, but loopback/private/link-local ranges are refused unless the operator
 * explicitly opts in on that channel (an audited decision).</p>
 */
@Component
public class AlertUrlGuard
{
    private static final Set<String> WECOM_HOSTS = Set.of("qyapi.weixin.qq.com");
    private static final Set<String> DINGTALK_HOSTS = Set.of("oapi.dingtalk.com");
    private static final Set<String> FEISHU_HOSTS = Set.of("open.feishu.cn");

    private static final Map<AlertChannelType, Set<String>> ALLOWED_HOSTS = Map.of(
        AlertChannelType.WECOM, WECOM_HOSTS,
        AlertChannelType.DINGTALK, DINGTALK_HOSTS,
        AlertChannelType.FEISHU, FEISHU_HOSTS
    );

    public void validate(String rawUrl, AlertChannelType type, boolean allowPrivateNetwork)
    {
        URI uri = parse(rawUrl);
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException(type + " 地址必须使用 https");
        }
        validateHostForType(uri, type, allowPrivateNetwork);
    }

    public void validateWebhook(String rawUrl, boolean allowPrivateNetwork)
    {
        URI uri = parse(rawUrl);
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("Webhook 地址只支持 http/https");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Webhook 地址不能包含用户名或密码");
        }
        if (!allowPrivateNetwork) {
            rejectPrivateAddress(uri.getHost());
        }
    }

    /**
     * SMTP is a host/port pair rather than a URL. Validate the shape first so a
     * pasted {@code smtp.example.com:587} fails with a useful message instead of
     * being treated as a hostname. Corporate mail relays commonly live on a
     * private address, so that case is available behind the same explicit
     * opt-in used by generic webhooks.
     */
    public void validateSmtpHost(String rawHost, boolean allowPrivateNetwork)
    {
        if (rawHost == null || rawHost.isBlank()) {
            throw new IllegalArgumentException("SMTP 服务器地址不能为空");
        }
        String host = rawHost.trim();
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        if (host.isBlank()) {
            throw new IllegalArgumentException("SMTP 服务器地址不能为空");
        }
        if (host.contains("://") || host.contains("/") || host.contains("@")
            || host.contains("?") || host.contains("#") || host.contains(";")
            || host.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException("SMTP 服务器地址格式不正确，只填写主机名或 IP");
        }
        if (host.matches("^[A-Za-z0-9._-]+:\\d+$")) {
            throw new IllegalArgumentException("SMTP 端口请填写在单独的端口中");
        }
        if (!allowPrivateNetwork) {
            rejectPrivateAddress(host);
        }
    }

    private URI parse(String rawUrl)
    {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("告警地址不能为空");
        }
        URI uri;
        try {
            uri = URI.create(rawUrl.trim());
        }
        catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("告警地址格式不正确");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("告警地址缺少主机名");
        }
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("告警地址不能包含用户名或密码");
        }
        return uri;
    }

    private void validateHostForType(URI uri, AlertChannelType type, boolean allowPrivateNetwork)
    {
        Set<String> allowed = ALLOWED_HOSTS.get(type);
        if (allowed == null) {
            return;
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!allowed.contains(host)) {
            throw new IllegalArgumentException(
                type.label() + " 地址必须指向 " + String.join("/", allowed));
        }
        if (!allowPrivateNetwork) {
            rejectPrivateAddress(host);
        }
    }

    /**
     * Resolve immediately before use so a name cannot be re-pointed between the
     * save-time check and the send-time connection (DNS rebinding).
     */
    public void rejectPrivateAddress(String host)
    {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        }
        catch (UnknownHostException ex) {
            throw new IllegalArgumentException("告警地址无法解析: " + host);
        }
        for (InetAddress address : addresses) {
            if (isBlocked(address)) {
                throw new IllegalArgumentException(
                    "告警地址指向内网或保留地址 " + address.getHostAddress()
                        + "，如确需访问请显式开启“允许内网地址”");
            }
        }
    }

    static boolean isBlocked(InetAddress address)
    {
        if (address.isAnyLocalAddress()
            || address.isLoopbackAddress()
            || address.isLinkLocalAddress()
            || address.isSiteLocalAddress()
            || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet4Address) {
            byte[] raw = address.getAddress();
            int first = raw[0] & 0xff;
            int second = raw[1] & 0xff;
            // 0.0.0.0/8, 100.64/10 (CGNAT), 192.0.0/24, 198.18/15, 240/4, broadcast
            if (first == 0 || first >= 240) {
                return true;
            }
            if (first == 100 && second >= 64 && second <= 127) {
                return true;
            }
            if (first == 192 && second == 0) {
                return true;
            }
            if (first == 198 && (second == 18 || second == 19)) {
                return true;
            }
            return false;
        }
        if (address instanceof Inet6Address) {
            byte[] raw = address.getAddress();
            if ((raw[0] & 0xfe) == 0xfc) {
                // fc00::/7 unique local
                return true;
            }
            // IPv4-mapped ::ffff:a.b.c.d is reported as Inet4Address by the JDK in
            // most cases, but check the raw form as well.
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                if (raw[i] != 0) {
                    mapped = false;
                    break;
                }
            }
            if (mapped && (raw[10] & 0xff) == 0xff && (raw[11] & 0xff) == 0xff) {
                try {
                    return isBlocked(InetAddress.getByAddress(new byte[] {raw[12], raw[13], raw[14], raw[15]}));
                }
                catch (UnknownHostException ignored) {
                    return true;
                }
            }
        }
        return false;
    }
}
