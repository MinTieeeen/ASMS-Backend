package com.asms.service.user;

import com.asms.exception.BusinessException;
import com.asms.exception.ErrorCode;
import com.sksamuel.scrimage.ImmutableImage;
import com.sksamuel.scrimage.ScaleMethod;
import com.sksamuel.scrimage.webp.WebpWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

/**
 * Turns an uploaded photo into the two avatar files (BR-USER-07, FR-USER-07, NFR-USER-05):
 *
 * <ol>
 *   <li>the format is told from the first bytes, never from the file name or Content-Type;
 *   <li>width and height are read from the header before decoding, so an image that would expand to gigabytes of
 *       pixels (decompression bomb) is rejected cheaply;
 *   <li>the image is decoded, turned upright from its EXIF orientation, cropped to the square chosen in the browser,
 *       scaled to 256 and 64 px and encoded again as WebP quality 85, which drops every metadata (EXIF, GPS...).
 * </ol>
 *
 * <p>The original file is never stored or served.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
@Component
public class AvatarImageProcessor {

    static final int MIN_SIDE = 128;
    static final int MAX_SIDE = 6000;
    private static final int WEBP_QUALITY = 85;
    private static final WebpWriter WEBP = WebpWriter.DEFAULT.withQ(WEBP_QUALITY);

    /**
     * @param cropX left edge of the square, in pixels of the upright original
     * @param cropY top edge of the square
     * @param cropSize side of the square, at least 128 px
     * @throws BusinessException {@code AVATAR_UNSUPPORTED_TYPE} or {@code AVATAR_INVALID_IMAGE}
     */
    public ProcessedAvatar process(byte[] content, int cropX, int cropY, int cropSize) {
        if (!isSupportedFormat(content)) {
            throw new BusinessException(ErrorCode.AVATAR_UNSUPPORTED_TYPE);
        }
        checkDeclaredSize(content);

        ImmutableImage upright = decode(content);
        if (cropX < 0
                || cropY < 0
                || cropSize < MIN_SIDE
                || (long) cropX + cropSize > upright.width
                || (long) cropY + cropSize > upright.height) {
            throw invalid();
        }
        ImmutableImage square = upright.subimage(cropX, cropY, cropSize, cropSize);
        return new ProcessedAvatar(
                encode(square, AvatarUrlResolver.LARGE_SIZE), encode(square, AvatarUrlResolver.THUMB_SIZE));
    }

    /** JPEG (FF D8 FF), PNG (89 'PNG' 0D 0A 1A 0A) or WebP ('RIFF' size 'WEBP') */
    static boolean isSupportedFormat(byte[] content) {
        return startsWith(content, 0, 0xFF, 0xD8, 0xFF)
                || startsWith(content, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)
                || (startsWith(content, 0, 'R', 'I', 'F', 'F') && startsWith(content, 8, 'W', 'E', 'B', 'P'));
    }

    /** Reads the dimensions from the header only (no pixel decoding) */
    private static void checkDeclaredSize(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (Math.min(width, height) < MIN_SIDE || Math.max(width, height) > MAX_SIDE) {
                    throw invalid();
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            throw e instanceof BusinessException business ? business : invalid();
        }
    }

    private static ImmutableImage decode(byte[] content) {
        try {
            return ImmutableImage.loader().detectOrientation(true).fromBytes(content);
        } catch (IOException | RuntimeException e) {
            throw invalid();
        }
    }

    private static byte[] encode(ImmutableImage square, int size) {
        try {
            return square.scaleTo(size, size, ScaleMethod.Bicubic).bytes(WEBP);
        } catch (IOException e) {
            throw new IllegalStateException("WebP encoding failed", e);
        }
    }

    private static boolean startsWith(byte[] content, int offset, int... expected) {
        if (content.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((content[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static BusinessException invalid() {
        return new BusinessException(ErrorCode.AVATAR_INVALID_IMAGE);
    }

    /** Both avatar sizes, WebP encoded */
    public record ProcessedAvatar(byte[] large, byte[] thumb) {}
}
