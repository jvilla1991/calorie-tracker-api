package com.calorietracker.photo;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.UUID;

/**
 * Test data for photo uploads: byte arrays that pass (or fail) the JPEG check, and
 * multipart/form-data bodies for tests that use a real HTTP client.
 */
public final class TestPhotos {

    private TestPhotos() {}

    /**
     * {@code size} bytes (at least 6) that start like a JPEG (FF D8 FF E0) and end with the JPEG
     * end-of-image marker. Not a decodable image: the API only checks the leading bytes.
     */
    public static byte[] jpeg(int size) {
        byte[] b = new byte[size];
        Arrays.fill(b, (byte) 0x42);
        b[0] = (byte) 0xFF;
        b[1] = (byte) 0xD8;
        b[2] = (byte) 0xFF;
        b[3] = (byte) 0xE0;
        b[size - 2] = (byte) 0xFF;
        b[size - 1] = (byte) 0xD9;
        return b;
    }

    /** The first bytes of a PNG file, which must be refused. */
    public static byte[] png() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};
    }

    /** A multipart/form-data request body, built by hand for {@code java.net.http.HttpClient}. */
    public static final class Multipart {
        private final String boundary = "----photo-test-" + UUID.randomUUID();
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();

        public Multipart file(String name, byte[] bytes) {
            write("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + name + ".jpg\"\r\n"
                    + "Content-Type: image/jpeg\r\n\r\n");
            body.writeBytes(bytes);
            write("\r\n");
            return this;
        }

        public Multipart field(String name, String value) {
            write("--" + boundary + "\r\n"
                    + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                    + value + "\r\n");
            return this;
        }

        public String contentType() {
            return "multipart/form-data; boundary=" + boundary;
        }

        public byte[] build() {
            ByteArrayOutputStream done = new ByteArrayOutputStream();
            done.writeBytes(body.toByteArray());
            done.writeBytes(("--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
            return done.toByteArray();
        }

        private void write(String s) {
            body.writeBytes(s.getBytes(StandardCharsets.UTF_8));
        }
    }
}
