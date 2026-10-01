package com.vaadin.demo.nordicsupply.util;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * Images as bytes and back: the pages the example documents are painted as, encoded for the model and the screen,
 * and the size of an image the customer sent, which the assistant is told so that it can place its source
 * rectangles on it.
 */
public final class Images {

    private Images() {}

    /**
     * An image as PNG bytes, for screenshots and rendered pages.
     *
     * @throws UncheckedIOException when the image writer fails, which an in-memory stream never does
     */
    public static byte[] png(BufferedImage image) {
        var out = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", out);
        } catch (IOException e) {
            throw new UncheckedIOException("could not encode the image as PNG", e);
        }
        return out.toByteArray();
    }

    /**
     * An image as JPEG bytes at the given quality, for photographs.
     *
     * @throws UncheckedIOException when the image writer fails, which an in-memory stream never does
     */
    public static byte[] jpeg(BufferedImage image, float quality) {
        var writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        var parameters = writer.getDefaultWriteParam();
        parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        parameters.setCompressionQuality(quality);
        var out = new ByteArrayOutputStream();
        try (var stream = new MemoryCacheImageOutputStream(out)) {
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } catch (IOException e) {
            throw new UncheckedIOException("could not encode the image as JPEG", e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    /** The width and height of an image, or empty when the bytes are not an image the JDK can read. */
    public static Optional<Dimension> size(byte[] image) {
        try (var in = ImageIO.createImageInputStream(new ByteArrayInputStream(image))) {
            var readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return Optional.empty();
            }
            var reader = readers.next();
            try {
                reader.setInput(in);
                return Optional.of(new Dimension(reader.getWidth(0), reader.getHeight(0)));
            } finally {
                reader.dispose();
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
