package com.resumegen.service;

import com.resumegen.dto.CaptchaVO;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图片验证码服务：JDK 原生生成，内存缓存（单实例），TTL 后过期，一次性消费。
 */
@Service
public class CaptchaService {

    private static final long TTL_MILLIS = 60_000L;
    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";
    private static final int LEN = 4;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    private static final class Entry {
        final String code;
        final long expireAt;

        Entry(String code, long expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }
    }

    /** 生成验证码图片并返回 base64 + 一次性 captchaId。 */
    public CaptchaVO generate() {
        cleanExpired();
        String code = randomCode();
        String id = UUID.randomUUID().toString().replace("-", "");
        store.put(id, new Entry(code, System.currentTimeMillis() + TTL_MILLIS));

        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaId(id);
        vo.setImageBase64("data:image/png;base64," + encodePng(render(code)));
        return vo;
    }

    /** 校验验证码：一次性消费，大小写不敏感，过期或不存在返回 false。 */
    public boolean verify(String captchaId, String captchaCode) {
        if (captchaId == null || captchaCode == null) {
            return false;
        }
        Entry e = store.remove(captchaId);
        if (e == null || System.currentTimeMillis() > e.expireAt) {
            return false;
        }
        return e.code.equalsIgnoreCase(captchaCode.trim());
    }

    private String randomCode() {
        StringBuilder sb = new StringBuilder(LEN);
        for (int i = 0; i < LEN; i++) {
            sb.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }
        return sb.toString();
    }

    private BufferedImage render(String code) {
        int w = 110;
        int h = 40;
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(245, 247, 250));
        g.fillRect(0, 0, w, h);
        g.setColor(new Color(200, 212, 228));
        for (int i = 0; i < 5; i++) {
            g.drawLine(RANDOM.nextInt(w), RANDOM.nextInt(h), RANDOM.nextInt(w), RANDOM.nextInt(h));
        }
        g.setFont(new Font("SansSerif", Font.BOLD, 26));
        for (int i = 0; i < code.length(); i++) {
            g.setColor(new Color(30 + RANDOM.nextInt(90), 60 + RANDOM.nextInt(90), 140 + RANDOM.nextInt(90)));
            g.drawString(String.valueOf(code.charAt(i)), 12 + i * 24, 28 + RANDOM.nextInt(6));
        }
        g.dispose();
        return img;
    }

    private String encodePng(BufferedImage img) {
        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            ImageIO.write(img, "png", os);
            return Base64.getEncoder().encodeToString(os.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("生成验证码失败", e);
        }
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expireAt < now);
    }
}