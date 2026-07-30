package com.blaie.blaie_be.capture.infrastructure.image;

import com.blaie.blaie_be.capture.application.port.ImageInput;
import com.blaie.blaie_be.capture.application.port.ImageSanitizerPort;
import com.blaie.blaie_be.capture.application.port.SanitizedImage;
import com.blaie.blaie_be.capture.domain.CaptureAnalysisException;
import com.blaie.blaie_be.capture.domain.CaptureFailureClass;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.stereotype.Component;

@Component
public class DefaultImageSanitizer implements ImageSanitizerPort {
    private final ImageCaptureProperties properties;

    public DefaultImageSanitizer(ImageCaptureProperties properties) {
        this.properties = properties;
    }

    @Override
    public SanitizedImage sanitize(ImageInput input) {
        byte[] source = input.bytes();
        if (source.length == 0) {
            throw rejected("image_empty", "Image file is empty");
        }
        if (source.length > properties.maxFileSize().toBytes()) {
            throw rejected("image_too_large", "Image file exceeds the configured limit");
        }
        ImageFormat format = detectFormat(source);
        if (format == null) {
            throw rejected("image_type_unsupported", "Image type is not supported");
        }

        BufferedImage decoded = decodeAfterDimensionPreflight(source);
        BufferedImage oriented = applyOrientation(decoded, readExifOrientation(source, format));

        boolean alpha = oriented.getColorModel().hasAlpha();
        String outputFormat = alpha ? "png" : "jpg";
        String contentType = alpha ? "image/png" : "image/jpeg";
        BufferedImage normalized = new BufferedImage(
                oriented.getWidth(),
                oriented.getHeight(),
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = normalized.createGraphics();
        try {
            graphics.drawImage(oriented, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        byte[] sanitized;
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(normalized, outputFormat, output)) {
                throw rejected("image_invalid", "Image could not be re-encoded");
            }
            sanitized = output.toByteArray();
        } catch (java.io.IOException exception) {
            throw rejected("image_invalid", "Image could not be re-encoded", exception);
        }
        if (sanitized.length > properties.maxFileSize().toBytes()) {
            throw rejected("image_too_large", "Sanitized image exceeds the configured limit");
        }
        return new SanitizedImage(
                sanitized,
                contentType,
                outputFormat,
                normalized.getWidth(),
                normalized.getHeight(),
                sha256(sanitized)
        );
    }

    private BufferedImage decodeAfterDimensionPreflight(byte[] source) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (stream == null) {
                throw rejected("image_invalid", "Image could not be decoded");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                throw rejected("image_invalid", "Image could not be decoded");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                long pixels = Math.multiplyExact((long) width, (long) height);
                if (width > properties.maxWidth()
                        || height > properties.maxHeight()
                        || pixels > properties.maxPixels()) {
                    throw rejected(
                            "image_dimensions_unsupported",
                            "Image dimensions exceed the configured limit"
                    );
                }
                BufferedImage decoded = reader.read(0);
                if (decoded == null) {
                    throw rejected("image_invalid", "Image could not be decoded");
                }
                return decoded;
            } finally {
                reader.dispose();
            }
        } catch (CaptureAnalysisException exception) {
            throw exception;
        } catch (RuntimeException | IOException exception) {
            throw rejected("image_invalid", "Image could not be decoded", exception);
        }
    }

    private BufferedImage applyOrientation(BufferedImage source, int orientation) {
        if (orientation == 1) {
            return source;
        }
        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();
        boolean swapsDimensions = orientation >= 5;
        BufferedImage target = new BufferedImage(
                swapsDimensions ? sourceHeight : sourceWidth,
                swapsDimensions ? sourceWidth : sourceHeight,
                source.getColorModel().hasAlpha()
                        ? BufferedImage.TYPE_INT_ARGB
                        : BufferedImage.TYPE_INT_RGB
        );
        for (int y = 0; y < sourceHeight; y++) {
            for (int x = 0; x < sourceWidth; x++) {
                int targetX;
                int targetY;
                switch (orientation) {
                    case 2 -> {
                        targetX = sourceWidth - 1 - x;
                        targetY = y;
                    }
                    case 3 -> {
                        targetX = sourceWidth - 1 - x;
                        targetY = sourceHeight - 1 - y;
                    }
                    case 4 -> {
                        targetX = x;
                        targetY = sourceHeight - 1 - y;
                    }
                    case 5 -> {
                        targetX = y;
                        targetY = x;
                    }
                    case 6 -> {
                        targetX = sourceHeight - 1 - y;
                        targetY = x;
                    }
                    case 7 -> {
                        targetX = sourceHeight - 1 - y;
                        targetY = sourceWidth - 1 - x;
                    }
                    case 8 -> {
                        targetX = y;
                        targetY = sourceWidth - 1 - x;
                    }
                    default -> {
                        targetX = x;
                        targetY = y;
                    }
                }
                target.setRGB(targetX, targetY, source.getRGB(x, y));
            }
        }
        return target;
    }

    private int readExifOrientation(byte[] bytes, ImageFormat format) {
        if (format != ImageFormat.JPEG) {
            return 1;
        }
        int segment = 2;
        while (segment + 4 <= bytes.length && unsigned(bytes[segment]) == 0xff) {
            int marker = unsigned(bytes[segment + 1]);
            if (marker == 0xda || marker == 0xd9) {
                break;
            }
            int segmentLength = unsignedShort(bytes, segment + 2, false);
            int payloadStart = segment + 4;
            int payloadLength = segmentLength - 2;
            if (segmentLength < 2 || payloadStart + payloadLength > bytes.length) {
                return 1;
            }
            if (marker == 0xe1
                    && payloadLength >= 14
                    && hasExifSignature(bytes, payloadStart)) {
                return parseTiffOrientation(bytes, payloadStart + 6, payloadLength - 6);
            }
            segment += segmentLength + 2;
        }
        return 1;
    }

    private boolean hasExifSignature(byte[] bytes, int offset) {
        return bytes[offset] == 'E'
                && bytes[offset + 1] == 'x'
                && bytes[offset + 2] == 'i'
                && bytes[offset + 3] == 'f'
                && bytes[offset + 4] == 0
                && bytes[offset + 5] == 0;
    }

    private int parseTiffOrientation(byte[] bytes, int tiffStart, int tiffLength) {
        if (tiffLength < 14 || tiffStart + tiffLength > bytes.length) {
            return 1;
        }
        boolean littleEndian;
        if (bytes[tiffStart] == 'I' && bytes[tiffStart + 1] == 'I') {
            littleEndian = true;
        } else if (bytes[tiffStart] == 'M' && bytes[tiffStart + 1] == 'M') {
            littleEndian = false;
        } else {
            return 1;
        }
        if (unsignedShort(bytes, tiffStart + 2, littleEndian) != 42) {
            return 1;
        }
        long ifdOffset = unsignedInt(bytes, tiffStart + 4, littleEndian);
        if (ifdOffset > tiffLength - 2L) {
            return 1;
        }
        int ifdStart = tiffStart + (int) ifdOffset;
        int entryCount = unsignedShort(bytes, ifdStart, littleEndian);
        for (int index = 0; index < entryCount; index++) {
            int entry = ifdStart + 2 + index * 12;
            if (entry + 12 > tiffStart + tiffLength) {
                return 1;
            }
            int tag = unsignedShort(bytes, entry, littleEndian);
            int type = unsignedShort(bytes, entry + 2, littleEndian);
            long count = unsignedInt(bytes, entry + 4, littleEndian);
            if (tag == 0x0112 && type == 3 && count >= 1) {
                int orientation = unsignedShort(bytes, entry + 8, littleEndian);
                return orientation >= 1 && orientation <= 8 ? orientation : 1;
            }
        }
        return 1;
    }

    private int unsignedShort(byte[] bytes, int offset, boolean littleEndian) {
        int first = unsigned(bytes[offset]);
        int second = unsigned(bytes[offset + 1]);
        return littleEndian ? first | second << 8 : first << 8 | second;
    }

    private long unsignedInt(byte[] bytes, int offset, boolean littleEndian) {
        long first = unsigned(bytes[offset]);
        long second = unsigned(bytes[offset + 1]);
        long third = unsigned(bytes[offset + 2]);
        long fourth = unsigned(bytes[offset + 3]);
        return littleEndian
                ? first | second << 8 | third << 16 | fourth << 24
                : first << 24 | second << 16 | third << 8 | fourth;
    }

    private int unsigned(byte value) {
        return value & 0xff;
    }

    private ImageFormat detectFormat(byte[] bytes) {
        if (bytes.length >= 8
                && (bytes[0] & 0xff) == 0x89
                && bytes[1] == 0x50
                && bytes[2] == 0x4e
                && bytes[3] == 0x47
                && bytes[4] == 0x0d
                && bytes[5] == 0x0a
                && bytes[6] == 0x1a
                && bytes[7] == 0x0a) {
            return ImageFormat.PNG;
        }
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xd8
                && (bytes[2] & 0xff) == 0xff) {
            return ImageFormat.JPEG;
        }
        if (bytes.length >= 12
                && bytes[0] == 'R'
                && bytes[1] == 'I'
                && bytes[2] == 'F'
                && bytes[3] == 'F'
                && bytes[8] == 'W'
                && bytes[9] == 'E'
                && bytes[10] == 'B'
                && bytes[11] == 'P') {
            return ImageFormat.WEBP;
        }
        return null;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private CaptureAnalysisException rejected(String code, String message) {
        return new CaptureAnalysisException(
                code,
                message,
                CaptureFailureClass.CONTENT_TERMINAL
        );
    }

    private CaptureAnalysisException rejected(String code, String message, Throwable cause) {
        return new CaptureAnalysisException(
                code,
                message,
                CaptureFailureClass.CONTENT_TERMINAL,
                cause
        );
    }

    private enum ImageFormat {
        JPEG,
        PNG,
        WEBP
    }
}
