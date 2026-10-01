package com.resumegen.service;

import com.resumegen.config.ResumeProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatermarkServiceTest {

    private WatermarkService service;

    @BeforeEach
    void setUp() {
        ResumeProperties props = new ResumeProperties();
        props.getWatermark().getVisible().setText("{user}_{date}");
        service = new WatermarkService(props);
    }

    @Test
    void buildVisibleTextReplacesPlaceholders() {
        String text = service.buildVisibleText(7L, "dev-1");
        assertThat(text).startsWith("7_");
        assertThat(text).hasSize(1 + 1 + 8); // "7_" + yyyyMMdd
    }

    @Test
    void buildVisibleTextAnonymousUsesGuest() {
        assertThat(service.buildVisibleText(null, null)).startsWith("guest_");
    }

    @Test
    void buildBlindPayloadCombinesUserAndDevice() {
        assertThat(service.buildBlindPayload(7L, "dev-1")).isEqualTo("7|dev-1");
        assertThat(service.buildBlindPayload(null, "")).isEqualTo("guest|anonymous");
    }

    @Test
    void encodeDecodeRoundTrip() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        String payload = "7|dev-1";
        service.encodeLsb(img, payload);
        assertThat(service.decodeLsb(img)).isEqualTo(payload);
    }

    @Test
    void decodeWithoutWatermarkReturnsNull() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        assertThat(service.decodeLsb(img)).isNull();
    }

    @Test
    void encodeTooSmallImageThrows() {
        BufferedImage img = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        assertThatThrownBy(() -> service.encodeLsb(img, "7|dev-1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void parsePayloadSplitsFields() {
        assertThat(service.parsePayload("7|dev-1"))
                .containsEntry("found", true)
                .containsEntry("userId", "7")
                .containsEntry("deviceId", "dev-1");
        assertThat(service.parsePayload(null)).containsEntry("found", false);
    }
}
