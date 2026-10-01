package com.resumegen.service;

import com.resumegen.config.ResumeProperties;
import com.resumegen.dto.WatermarkConfigVO;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 简历水印（R8-E）：可视水印文案生成 + 盲水印（LSB 像素级）编解码溯源。
 * <p>
 * 盲水印将「user_id|device_id」按字节写入像素红通道最低位，肉眼不可见，
 * 遭截图/转码后仍有较高存活率（压缩/缩放可能破坏，仅供溯源威慑，不替代法律手段）。
 */
@Service
public class WatermarkService {

    private static final byte[] MAGIC = {'R', 'W', 'M', 'W'};
    private static final int MAX_PAYLOAD_BYTES = 512;

    private final ResumeProperties props;

    public WatermarkService(ResumeProperties props) {
        this.props = props;
    }

    /** 可视水印文案（{user}/{date}/{device} 占位替换）。 */
    public String buildVisibleText(Long userId, String deviceId) {
        String template = props.getWatermark().getVisible().getText();
        if (template == null || template.isBlank()) {
            template = "{user}_{date}";
        }
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return template
                .replace("{user}", userId == null ? "guest" : String.valueOf(userId))
                .replace("{date}", date)
                .replace("{device}", deviceId == null || deviceId.isBlank() ? "" : deviceId);
    }

    /** 盲水印负载（如 user_id|device_id）。 */
    public String buildBlindPayload(Long userId, String deviceId) {
        return (userId == null ? "guest" : String.valueOf(userId))
                + "|" + (deviceId == null || deviceId.isBlank() ? "anonymous" : deviceId);
    }

    /** 组装前端所需的水印配置（含已替换的可视文案与盲水印负载）。 */
    public WatermarkConfigVO config(Long userId, String deviceId) {
        ResumeProperties.Watermark wm = props.getWatermark();
        WatermarkConfigVO vo = new WatermarkConfigVO();
        vo.setEnabled(wm.isEnabled());
        vo.setVisibleText(buildVisibleText(userId, deviceId));
        vo.setVisibleOpacity(wm.getVisible().getOpacity());
        vo.setVisibleDensity(wm.getVisible().getDensity());
        vo.setBlindEnabled(wm.getBlind().isEnabled());
        if (userId != null) {
            vo.setBlindPayload(buildBlindPayload(userId, deviceId));
        }
        return vo;
    }

    /** 将负载编码进图片红通道 LSB（原地修改并返回）。 */
    public BufferedImage encodeLsb(BufferedImage img, String payload) {
        byte[] data = payload.getBytes(StandardCharsets.UTF_8);
        if (data.length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("水印负载过长");
        }
        int totalBytes = 4 + 2 + data.length;
        int capacity = img.getWidth() * img.getHeight();
        if (capacity < totalBytes * 8) {
            throw new IllegalArgumentException("图片像素不足以承载水印");
        }
        byte[] full = new byte[totalBytes];
        System.arraycopy(MAGIC, 0, full, 0, 4);
        full[4] = (byte) ((data.length >> 8) & 0xFF);
        full[5] = (byte) (data.length & 0xFF);
        System.arraycopy(data, 0, full, 6, data.length);

        int bitIndex = 0;
        int w = img.getWidth();
        int h = img.getHeight();
        outer:
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if (bitIndex >= totalBytes * 8) {
                    break outer;
                }
                int bit = (full[bitIndex / 8] >> (7 - (bitIndex % 8))) & 1;
                int rgb = img.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                r = (r & 0xFE) | bit;
                img.setRGB(x, y, (rgb & 0xFF00FFFF) | (r << 16));
                bitIndex++;
            }
        }
        return img;
    }

    /** 从图片红通道 LSB 解码负载；未检测到水印返回 null。 */
    public String decodeLsb(BufferedImage img) {
        byte[] magic = readBytes(img, 0, 4);
        if (!Arrays.equals(magic, MAGIC)) {
            return null;
        }
        byte[] lenBytes = readBytes(img, 4, 2);
        int len = ((lenBytes[0] & 0xFF) << 8) | (lenBytes[1] & 0xFF);
        if (len <= 0 || len > MAX_PAYLOAD_BYTES) {
            return null;
        }
        byte[] data = readBytes(img, 6, len);
        return new String(data, StandardCharsets.UTF_8);
    }

    /** 解析负载字符串，返回结构化溯源信息。 */
    public Map<String, Object> parsePayload(String payload) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("payload", payload);
        if (payload == null || payload.isBlank()) {
            result.put("found", false);
            return result;
        }
        String[] parts = payload.split("\\|");
        result.put("found", true);
        result.put("userId", parts.length > 0 ? parts[0] : "");
        result.put("deviceId", parts.length > 1 ? parts[1] : "");
        return result;
    }

    private byte[] readBytes(BufferedImage img, int startByte, int count) {
        int w = img.getWidth();
        int h = img.getHeight();
        byte[] out = new byte[count];
        for (int b = 0; b < count; b++) {
            int value = 0;
            for (int i = 0; i < 8; i++) {
                int bitIndex = (startByte + b) * 8 + i;
                int x = bitIndex % w;
                int y = bitIndex / w;
                if (y >= h) {
                    return out;
                }
                int rgb = img.getRGB(x, y);
                value = (value << 1) | ((rgb >> 16) & 1);
            }
            out[b] = (byte) value;
        }
        return out;
    }
}
