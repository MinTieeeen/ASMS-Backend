package com.asms.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.sksamuel.scrimage.ImmutableImage;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AvatarImageProcessorTest {

    private final AvatarImageProcessor processor = new AvatarImageProcessor();

    @Test
    @DisplayName("FR-USER-07: crops the square and writes 256 and 64 px WebP files")
    void process_shouldCropAndEncodeBothSizes() throws IOException {
        byte[] png = image("png", 400, 300);

        AvatarImageProcessor.ProcessedAvatar avatar = processor.process(png, 50, 0, 300);

        assertWebp(avatar.large(), 256);
        assertWebp(avatar.thumb(), 64);
    }

    @Test
    void process_shouldAcceptJpeg() throws IOException {
        AvatarImageProcessor.ProcessedAvatar avatar = processor.process(image("jpg", 200, 200), 0, 0, 200);

        assertWebp(avatar.large(), 256);
    }

    @Test
    @DisplayName("NFR-USER-05: the format comes from the content, not the name")
    void process_shouldRejectUnsupportedContent() throws IOException {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        byte[] gif = image("gif", 200, 200);

        assertCode(() -> processor.process(svg, 0, 0, 128), ErrorCode.AVATAR_UNSUPPORTED_TYPE);
        assertCode(() -> processor.process(gif, 0, 0, 128), ErrorCode.AVATAR_UNSUPPORTED_TYPE);
        assertCode(() -> processor.process(new byte[0], 0, 0, 128), ErrorCode.AVATAR_UNSUPPORTED_TYPE);
    }

    @Test
    @DisplayName("BR-USER-07: sides between 128 and 6000 px")
    void process_shouldRejectImagesOutOfBounds() throws IOException {
        byte[] tooSmall = image("png", 100, 400);
        byte[] tooWide = image("png", 6001, 200);

        assertCode(() -> processor.process(tooSmall, 0, 0, 100), ErrorCode.AVATAR_INVALID_IMAGE);
        assertCode(() -> processor.process(tooWide, 0, 0, 200), ErrorCode.AVATAR_INVALID_IMAGE);
    }

    @Test
    void process_shouldRejectCropOutsideTheImage() throws IOException {
        byte[] png = image("png", 300, 300);

        assertCode(() -> processor.process(png, 100, 0, 250), ErrorCode.AVATAR_INVALID_IMAGE);
        assertCode(() -> processor.process(png, -1, 0, 200), ErrorCode.AVATAR_INVALID_IMAGE);
        assertCode(() -> processor.process(png, 0, 0, 127), ErrorCode.AVATAR_INVALID_IMAGE);
    }

    @Test
    void process_shouldRejectBrokenImage() throws IOException {
        byte[] png = image("png", 300, 300);
        byte[] truncated = Arrays.copyOf(png, 40);

        assertCode(() -> processor.process(truncated, 0, 0, 200), ErrorCode.AVATAR_INVALID_IMAGE);
    }

    private static void assertWebp(byte[] webp, int size) throws IOException {
        assertThat(new String(webp, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("RIFF");
        assertThat(new String(webp, 8, 4, StandardCharsets.US_ASCII)).isEqualTo("WEBP");
        ImmutableImage decoded = ImmutableImage.loader().fromBytes(webp);
        assertThat(decoded.width).isEqualTo(size);
        assertThat(decoded.height).isEqualTo(size);
    }

    private static void assertCode(Runnable call, ErrorCode code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getErrorCode())
                .isEqualTo(code));
    }

    private static byte[] image(String format, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(11, 122, 92));
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.YELLOW);
        graphics.fillOval(width / 4, height / 4, width / 2, height / 2);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
