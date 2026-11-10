package com.wgzhao.addax.admin.alert.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.wgzhao.addax.admin.alert.AlertChannelProvider;
import com.wgzhao.addax.admin.alert.AlertChannelType;
import com.wgzhao.addax.admin.alert.AlertDeliveryResult;
import com.wgzhao.addax.admin.alert.AlertMessage;
import com.wgzhao.addax.admin.alert.AlertMessageFactory;
import com.wgzhao.addax.admin.alert.AlertSeverity;
import com.wgzhao.addax.admin.alert.AlertUrlGuard;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * SMTP provider. The mail sender is constructed for every delivery from the
 * channel's decrypted configuration, so one channel's credentials never leak
 * into another and a configuration change takes effect without a restart.
 */
@Component
@RequiredArgsConstructor
public class EmailAlertProvider
    implements AlertChannelProvider
{
    private static final int DEFAULT_TIMEOUT_MS = 10000;
    private static final int MAX_RECIPIENTS = 50;
    private static final Set<String> SECURITY_MODES = Set.of("NONE", "STARTTLS", "SSL");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());

    private final AlertUrlGuard urlGuard;
    private final AlertMessageFactory messageFactory;

    @Override
    public AlertChannelType type()
    {
        return AlertChannelType.EMAIL;
    }

    @Override
    public void validate(JsonNode config)
    {
        parse(config);
    }

    @Override
    public AlertDeliveryResult send(JsonNode config, AlertMessage message)
    {
        long started = System.currentTimeMillis();
        try {
            SmtpSettings settings = parse(config);
            JavaMailSenderImpl sender = buildSender(settings);
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(settings.from());
            helper.setTo(settings.to().toArray(new InternetAddress[0]));
            if (!settings.cc().isEmpty()) {
                helper.setCc(settings.cc().toArray(new InternetAddress[0]));
            }
            helper.setSubject(subject(settings, message));
            helper.setText(plainBody(message), htmlBody(message));
            Instant occurredAt = message.occurredAt() == null ? Instant.now() : message.occurredAt();
            helper.setSentDate(Date.from(occurredAt));
            setHeader(mime, "X-Addax-Event-Key", message.eventKey());
            setHeader(mime, "X-Addax-Event-Type", message.eventType());
            setHeader(mime, "X-Addax-Severity", message.severity() == null ? "" : message.severity().name());
            sender.send(mime);
            return AlertDeliveryResult.ok(250,
                "SMTP accepted by " + settings.host() + ":" + settings.port(),
                System.currentTimeMillis() - started);
        }
        catch (Exception ex) {
            return AlertDeliveryResult.failed(null, null, System.currentTimeMillis() - started, rootMessage(ex));
        }
    }

    private JavaMailSenderImpl buildSender(SmtpSettings settings)
    {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(settings.host());
        sender.setPort(settings.port());
        sender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        if (!settings.username().isEmpty()) {
            sender.setUsername(settings.username());
            sender.setPassword(settings.password());
        }
        Properties properties = sender.getJavaMailProperties();
        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", String.valueOf(!settings.username().isEmpty()));
        properties.put("mail.smtp.connectiontimeout", String.valueOf(settings.timeoutMs()));
        properties.put("mail.smtp.timeout", String.valueOf(settings.timeoutMs()));
        properties.put("mail.smtp.writetimeout", String.valueOf(settings.timeoutMs()));
        // JavaMail defaults this to false; without it a valid certificate for
        // any host would be accepted, defeating TLS's man-in-the-middle defense.
        properties.put("mail.smtp.ssl.checkserveridentity", "true");
        switch (settings.security()) {
            case "SSL" -> properties.put("mail.smtp.ssl.enable", "true");
            case "STARTTLS" -> {
                properties.put("mail.smtp.starttls.enable", "true");
                properties.put("mail.smtp.starttls.required", "true");
            }
            default -> {
                // Plain SMTP is allowed for isolated relays; the UI marks it explicitly.
            }
        }
        return sender;
    }

    private SmtpSettings parse(JsonNode config)
    {
        String host = config.path("host").asText("").trim();
        if (host.isEmpty()) {
            throw new IllegalArgumentException("邮件通道缺少 SMTP 服务器地址");
        }
        String security = config.path("security").asText("STARTTLS").trim().toUpperCase(Locale.ROOT);
        if (!SECURITY_MODES.contains(security)) {
            throw new IllegalArgumentException("SMTP 加密方式只支持 NONE/STARTTLS/SSL");
        }
        int defaultPort = switch (security) {
            case "SSL" -> 465;
            case "NONE" -> 25;
            default -> 587;
        };
        int port = config.path("port").asInt(defaultPort);
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("SMTP 端口必须在 1-65535 之间");
        }

        String username = config.path("username").asText("").trim();
        String password = config.path("password").asText("");
        if (username.isEmpty() && !password.isEmpty()) {
            throw new IllegalArgumentException("填写 SMTP 密码时必须同时填写用户名");
        }
        if (!username.isEmpty() && password.isEmpty()) {
            throw new IllegalArgumentException("填写 SMTP 用户名时必须同时填写密码");
        }

        InternetAddress from = parseFrom(config.path("from").asText(""), config.path("fromName").asText(""));
        List<InternetAddress> to = parseAddresses(config.path("to"), "收件人", true);
        List<InternetAddress> cc = parseAddresses(config.path("cc"), "抄送", false);

        int timeoutMs = config.path("timeoutMs").asInt(DEFAULT_TIMEOUT_MS);
        if (timeoutMs < 1000 || timeoutMs > 30000) {
            throw new IllegalArgumentException("SMTP 超时必须在 1000-30000 毫秒之间");
        }
        String subjectPrefix = config.path("subjectPrefix").asText("").trim();
        if (subjectPrefix.contains("\r") || subjectPrefix.contains("\n")) {
            throw new IllegalArgumentException("邮件主题前缀不能包含换行符");
        }
        if (subjectPrefix.length() > 50) {
            throw new IllegalArgumentException("邮件主题前缀不能超过 50 个字符");
        }
        boolean allowPrivateNetwork = config.path("allowPrivateNetwork").asBoolean(false);
        // Resolve at save and again at send time; the latter closes the window
        // between configuration and connection when DNS changes.
        urlGuard.validateSmtpHost(host, allowPrivateNetwork);
        return new SmtpSettings(host, port, security, username, password, from, to, cc,
            subjectPrefix, timeoutMs);
    }

    private InternetAddress parseFrom(String raw, String rawName)
    {
        String value = raw == null ? "" : raw.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("邮件通道缺少发件人邮箱");
        }
        ensureNoLineBreak(value, "发件人邮箱");
        InternetAddress[] addresses = strictParse(value, "发件人邮箱");
        if (addresses.length != 1 || addresses[0].isGroup()) {
            throw new IllegalArgumentException("发件人邮箱只能填写一个地址");
        }
        String name = rawName == null ? "" : rawName.trim();
        ensureNoLineBreak(name, "发件人名称");
        if (name.length() > 100) {
            throw new IllegalArgumentException("发件人名称不能超过 100 个字符");
        }
        try {
            InternetAddress from = new InternetAddress(addresses[0].getAddress(),
                name.isEmpty() ? addresses[0].getPersonal() : name,
                StandardCharsets.UTF_8.name());
            from.validate();
            return from;
        }
        catch (Exception ex) {
            throw new IllegalArgumentException("发件人邮箱格式不正确");
        }
    }

    private List<InternetAddress> parseAddresses(JsonNode node, String label, boolean required)
    {
        List<String> values = new ArrayList<>();
        if (node != null && !node.isMissingNode() && !node.isNull()) {
            if (node.isArray()) {
                node.forEach(item -> {
                    if (!item.isTextual()) {
                        throw new IllegalArgumentException(label + "必须是邮箱地址字符串或数组");
                    }
                    values.add(item.asText());
                });
            }
            else if (node.isTextual()) {
                values.add(node.asText());
            }
            else {
                throw new IllegalArgumentException(label + "必须是邮箱地址字符串或数组");
            }
        }
        String raw = String.join(",", values).trim();
        if (raw.isEmpty()) {
            if (required) {
                throw new IllegalArgumentException("邮件通道缺少" + label);
            }
            return List.of();
        }
        ensureNoLineBreak(raw, label);
        InternetAddress[] addresses = strictParse(raw.replace(';', ','), label);
        Map<String, InternetAddress> unique = new LinkedHashMap<>();
        for (InternetAddress address : addresses) {
            if (address.isGroup() || address.getAddress() == null || address.getAddress().isBlank()) {
                throw new IllegalArgumentException(label + "包含非法邮箱地址");
            }
            unique.putIfAbsent(address.getAddress().toLowerCase(Locale.ROOT), address);
        }
        if (unique.size() > MAX_RECIPIENTS) {
            throw new IllegalArgumentException(label + "不能超过 " + MAX_RECIPIENTS + " 个");
        }
        return new ArrayList<>(unique.values());
    }

    private InternetAddress[] strictParse(String value, String label)
    {
        try {
            return InternetAddress.parse(value, true);
        }
        catch (AddressException ex) {
            throw new IllegalArgumentException(label + "包含非法邮箱地址");
        }
    }

    private static void ensureNoLineBreak(String value, String label)
    {
        if (value != null && (value.contains("\r") || value.contains("\n"))) {
            throw new IllegalArgumentException(label + "不能包含换行符");
        }
    }

    private String subject(SmtpSettings settings, AlertMessage message)
    {
        String severity = message.severity() == null ? "" : "[" + message.severity().name() + "] ";
        String title = message.title() == null || message.title().isBlank()
            ? "Addax Admin 告警" : message.title();
        String prefix = settings.subjectPrefix().isEmpty() ? "" : settings.subjectPrefix() + " ";
        String subject = prefix + severity + title;
        return subject.length() > 200 ? subject.substring(0, 200) : subject;
    }

    private String plainBody(AlertMessage message)
    {
        return (message.title() == null ? "Addax Admin 告警" : message.title()) + "\n"
            + "级别: " + severityLabel(message.severity()) + "\n"
            + "时间: " + formatTime(message.occurredAt()) + "\n"
            + "类型: " + nullToEmpty(message.eventType()) + "\n"
            + "---------------------------------\n"
            + nullToEmpty(message.text());
    }

    private String htmlBody(AlertMessage message)
    {
        String severity = severityLabel(message.severity());
        String color = switch (severity) {
            case "P1" -> "#dc2626";
            case "P2" -> "#d97706";
            default -> "#2563eb";
        };
        String content = message.html();
        if (content == null || content.isBlank()) {
            content = "<p>" + escapeHtml(nullToEmpty(message.text())).replace("\n", "<br/>") + "</p>";
        }
        String meta = "级别 " + escapeHtml(severity)
            + " · " + escapeHtml(formatTime(message.occurredAt()))
            + " · " + escapeHtml(nullToEmpty(message.eventType()))
            + " · " + escapeHtml(messageFactory.hostname());
        return """
            <div style="font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;color:#1f2937;line-height:1.6;max-width:720px;">
              <div style="border-left:4px solid %s;background:#f8fafc;padding:10px 14px;margin-bottom:18px;">
                <div style="font-size:13px;color:#475569;">%s</div>
                <div style="font-size:12px;color:#94a3b8;margin-top:4px;">事件：%s</div>
              </div>
              <div>%s</div>
              <hr style="border:none;border-top:1px solid #e2e8f0;margin:24px 0 12px;"/>
              <p style="font-size:12px;color:#94a3b8;">此邮件由 Addax Admin 告警系统自动发送，请勿直接回复。</p>
            </div>
            """.formatted(color, meta, escapeHtml(nullToEmpty(message.eventKey())), content);
    }

    private static String severityLabel(AlertSeverity severity)
    {
        return severity == null ? "P3" : severity.name();
    }

    private static String formatTime(Instant instant)
    {
        return instant == null ? "—" : TIME_FORMAT.format(instant);
    }

    private static void setHeader(MimeMessage mime, String name, String value)
        throws MessagingException
    {
        if (value == null) {
            return;
        }
        mime.setHeader(name, value.replace("\r", "").replace("\n", ""));
    }

    private static String rootMessage(Throwable ex)
    {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String message = root.getMessage();
        if (message == null || message.isBlank()) {
            message = root.getClass().getSimpleName();
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private static String nullToEmpty(String value)
    {
        return value == null ? "" : value;
    }

    private static String escapeHtml(String value)
    {
        return AlertMessageFactory.escapeHtml(value);
    }

    private record SmtpSettings(
        String host,
        int port,
        String security,
        String username,
        String password,
        InternetAddress from,
        List<InternetAddress> to,
        List<InternetAddress> cc,
        String subjectPrefix,
        int timeoutMs
    )
    {
    }
}
