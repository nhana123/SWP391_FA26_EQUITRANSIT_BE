package com.equitransit.backend.service.departure;

import com.equitransit.backend.dto.departure.DepartureModels.DepartureEvidence;
import com.equitransit.backend.exception.order.OrderException;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Private durable image storage; keys carry no client-selected path. */
@Component
public class DepartureEvidenceStorage {

    private static final int LIMIT = 10 * 1024 * 1024;
    private final Path root;

    /** @param directory private application-owned storage root */
    public DepartureEvidenceStorage(@Value("${equitransit.departure."
            + "storage-root:.local/departure-evidence}") String directory) {
        root = Path.of(directory).toAbsolutePath().normalize();
    }

    /** @param order parent @param plan plan @param leg pickup @param user actor
     * @param bytes real image @param now UTC @return scoped immutable key */
    public DepartureEvidence store(long order, long plan, long leg, long user,
            byte[] bytes, OffsetDateTime now) {
        String type = imageType(bytes);
        String key = UUID.randomUUID().toString();
        String hash = hash(bytes);
        try {
            Files.createDirectories(root);
            try (var stream = new DataOutputStream(Files.newOutputStream(
                    path(key), StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE))) {
                stream.writeInt(1);
                stream.writeLong(order);
                stream.writeLong(plan);
                stream.writeLong(leg);
                stream.writeLong(user);
                stream.writeUTF(now.toString());
                stream.writeUTF(hash);
                stream.writeInt(bytes.length);
                stream.write(bytes);
            }
        } catch (IOException failure) {
            throw new IllegalStateException("Evidence storage unavailable.",
                    failure);
        }
        return new DepartureEvidence(key, type, bytes.length, hash);
    }

    /** @param key opaque key @param order parent @param plan current plan
     * @param leg pickup @param user expected uploader or null for receipt read
     * @return defensive, hash-verified image bytes */
    public byte[] read(String key, long order, long plan, long leg, Long user) {
        Path file = path(key);
        try {
            if (Files.isSymbolicLink(file) || Files.size(file) > LIMIT + 1024) {
                throw invalid();
            }
            try (var stream = new DataInputStream(Files.newInputStream(file))) {
                if (stream.readInt() != 1 || stream.readLong() != order
                        || stream.readLong() != plan
                        || stream.readLong() != leg) {
                    throw invalid();
                }
                long uploader = stream.readLong();
                if (user != null && uploader != user) {
                    throw invalid();
                }
                OffsetDateTime.parse(stream.readUTF());
                String expected = stream.readUTF();
                int length = stream.readInt();
                if (length <= 0 || length > LIMIT) {
                    throw invalid();
                }
                byte[] bytes = stream.readNBytes(length);
                if (bytes.length != length || stream.read() != -1
                        || !hash(bytes).equals(expected)) {
                    throw invalid();
                }
                imageType(bytes);
                return bytes;
            }
        } catch (IOException | java.time.DateTimeException failure) {
            throw invalid();
        }
    }

    /** @param key receipt key @param order parent @param plan plan
     * @param leg pickup
     * @return false for unavailable or invalid evidence */
    public boolean valid(String key, long order, long plan, long leg) {
        try {
            read(key, order, plan, leg, null);
            return true;
        } catch (OrderException invalid) {
            return false;
        }
    }

    private Path path(String key) {
        if (key == null || !key.matches(
                "[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")) {
            throw invalid();
        }
        return root.resolve(key + ".evidence");
    }

    private String imageType(byte[] bytes) {
        if (bytes == null || bytes.length < 8 || bytes.length > LIMIT) {
            throw invalid();
        }
        boolean png = bytes[0] == (byte) 0x89 && bytes[1] == 0x50
                && bytes[2] == 0x4e && bytes[3] == 0x47
                && bytes[4] == 13 && bytes[5] == 10
                && bytes[6] == 26 && bytes[7] == 10;
        boolean jpeg = bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xd8
                && bytes[2] == (byte) 0xff;
        if (!png && !jpeg) {
            throw invalid();
        }
        try (var input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid();
            }
            var reader = readers.next();
            try {
                reader.setInput(input);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > 20000000
                        || reader.read(0) == null) {
                    throw invalid();
                }
            } finally {
                reader.dispose();
            }
        } catch (IOException failure) {
            throw invalid();
        }
        return png ? "image/png" : "image/jpeg";
    }

    private String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(
                    "SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private OrderException invalid() {
        return new OrderException(HttpStatus.CONFLICT,
                "RECEIPT_EVIDENCE_INVALID",
                "Upload a valid scoped PNG/JPEG image before receiving.");
    }
}
