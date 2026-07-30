package com.blaie.blaie_be.capture.infrastructure.image;

import com.blaie.blaie_be.capture.application.port.ImageInput;
import com.blaie.blaie_be.capture.domain.CaptureAnalysisException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultImageSanitizerTest {
    @Test
    void installsAWebpReaderBecauseWebpIsAnAdvertisedInputType() {
        assertThat(ImageIO.getImageReadersByMIMEType("image/webp").hasNext()).isTrue();
    }

    @Test
    void decodesAndSanitizesWebpInput() {
        byte[] webp = Base64.getDecoder().decode(
                "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89WAAAAA=="
        );

        var sanitized = new DefaultImageSanitizer(properties()).sanitize(new ImageInput(
                "pixel.webp",
                "image/webp",
                webp
        ));

        assertThat(sanitized.contentType()).isIn("image/png", "image/jpeg");
        assertThat(sanitized.width()).isEqualTo(1);
        assertThat(sanitized.height()).isEqualTo(1);
    }

    @Test
    void rejectsAReencodedRasterThatWouldExceedTheStorageAndProviderLimit() {
        byte[] compactWebp = Base64.getDecoder().decode(
                "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEAAUAmJaQAA3AA/v89WAAAAA=="
        );
        ImageCaptureProperties properties = properties();
        properties.setMaxFileSize(org.springframework.util.unit.DataSize.ofBytes(50));

        assertThat(compactWebp.length).isLessThanOrEqualTo(50);
        assertThatThrownBy(() -> new DefaultImageSanitizer(properties).sanitize(new ImageInput(
                "pixel.webp",
                "image/webp",
                compactWebp
        )))
                .isInstanceOf(CaptureAnalysisException.class)
                .satisfies(error -> assertThat(((CaptureAnalysisException) error).failureCode())
                        .isEqualTo("image_too_large"));
    }

    @Test
    void appliesJpegExifOrientationBeforeStrippingMetadata() throws Exception {
        byte[] jpeg = imageBytes(2, 1, "jpg");
        byte[] orientedJpeg = withExifOrientation(jpeg, 6);

        var sanitized = new DefaultImageSanitizer(properties()).sanitize(new ImageInput(
                "phone-photo.jpg",
                "image/jpeg",
                orientedJpeg
        ));

        assertThat(sanitized.width()).isEqualTo(1);
        assertThat(sanitized.height()).isEqualTo(2);
    }

    @Test
    void decodesAndReencodesAllowedRasterWithoutTrustingFilename() throws Exception {
        ImageCaptureProperties properties = properties();
        DefaultImageSanitizer sanitizer = new DefaultImageSanitizer(properties);
        byte[] png = imageBytes(20, 10, "png");

        var sanitized = sanitizer.sanitize(new ImageInput(
                "../../unsafe.svg",
                "application/octet-stream",
                png
        ));

        assertThat(sanitized.contentType()).isEqualTo("image/png");
        assertThat(sanitized.width()).isEqualTo(20);
        assertThat(sanitized.height()).isEqualTo(10);
        assertThat(sanitized.sha256()).matches("[a-f0-9]{64}");
        assertThat(ImageIO.read(new java.io.ByteArrayInputStream(sanitized.bytes()))).isNotNull();
    }

    @Test
    void rejectsContentThatClaimsToBeAnImageButCannotBeDecoded() {
        DefaultImageSanitizer sanitizer = new DefaultImageSanitizer(properties());

        assertThatThrownBy(() -> sanitizer.sanitize(new ImageInput(
                "fake.png",
                "image/png",
                "not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        )))
                .isInstanceOf(CaptureAnalysisException.class)
                .satisfies(error -> assertThat(((CaptureAnalysisException) error).failureCode())
                        .isEqualTo("image_type_unsupported"));
    }

    @Test
    void rejectsSvgEvenWhenItUsesAnImageContentType() {
        DefaultImageSanitizer sanitizer = new DefaultImageSanitizer(properties());

        assertThatThrownBy(() -> sanitizer.sanitize(new ImageInput(
                "payload.svg",
                "image/svg+xml",
                "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8)
        )))
                .isInstanceOf(CaptureAnalysisException.class)
                .satisfies(error -> assertThat(((CaptureAnalysisException) error).failureCode())
                        .isEqualTo("image_type_unsupported"));
    }

    @Test
    void rejectsDecodedImagesBeyondPixelLimit() throws Exception {
        ImageCaptureProperties properties = properties();
        properties.setMaxPixels(99);
        DefaultImageSanitizer sanitizer = new DefaultImageSanitizer(properties);

        assertThatThrownBy(() -> sanitizer.sanitize(new ImageInput(
                "large.png",
                "image/png",
                imageBytes(10, 10, "png")
        )))
                .isInstanceOf(CaptureAnalysisException.class)
                .satisfies(error -> assertThat(((CaptureAnalysisException) error).failureCode())
                        .isEqualTo("image_dimensions_unsupported"));
    }

    private ImageCaptureProperties properties() {
        ImageCaptureProperties properties = new ImageCaptureProperties();
        properties.setMaxFileSize(org.springframework.util.unit.DataSize.ofMegabytes(10));
        properties.setMaxPixels(40_000_000);
        properties.setMaxWidth(12_000);
        properties.setMaxHeight(12_000);
        return properties;
    }

    private byte[] imageBytes(int width, int height, String format) throws Exception {
        int imageType = "jpg".equals(format)
                ? BufferedImage.TYPE_INT_RGB
                : BufferedImage.TYPE_INT_ARGB;
        BufferedImage image = new BufferedImage(width, height, imageType);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, format, output);
        return output.toByteArray();
    }

    private byte[] withExifOrientation(byte[] jpeg, int orientation) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(jpeg, 0, 2);
        output.write(new byte[] {
                (byte) 0xff, (byte) 0xe1, 0x00, 0x22,
                'E', 'x', 'i', 'f', 0x00, 0x00,
                'M', 'M', 0x00, 0x2a, 0x00, 0x00, 0x00, 0x08,
                0x00, 0x01,
                0x01, 0x12,
                0x00, 0x03,
                0x00, 0x00, 0x00, 0x01,
                0x00, (byte) orientation, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00
        });
        output.write(jpeg, 2, jpeg.length - 2);
        return output.toByteArray();
    }
}
