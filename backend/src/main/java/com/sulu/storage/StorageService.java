package com.sulu.storage;

import com.sulu.common.ApiException;
import com.sulu.common.SuluProperties;
import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/** File storage abstraction. This implementation writes to local disk (dev / single node); swap for S3/R2 by re-implementing store/load. */
@Service
public class StorageService {
    private final Path root;
    private final long maxBytes;

    public StorageService(SuluProperties props) throws IOException {
        this.root = Paths.get(props.storage().dir()).toAbsolutePath().normalize();
        this.maxBytes = props.storage().maxBytes();
        Files.createDirectories(root);
    }

    /** Validates real content (magic bytes, not the client's filename/content-type) and stores under a generated key. */
    public String storeImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw ApiException.bad("EMPTY_FILE", "File is empty");
        if (file.getSize() > maxBytes) throw new ApiException(org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "File too large");
        try {
            byte[] data = file.getBytes();
            String ext = sniff(data);
            if (ext == null) throw ApiException.bad("UNSUPPORTED_IMAGE", "Only JPEG, PNG or WebP images are allowed");
            String key = UUID.randomUUID() + "." + ext;
            Files.write(root.resolve(key), data, StandardOpenOption.CREATE_NEW);
            return key;
        } catch (IOException e) { throw new IllegalStateException("storage failure", e); }
    }

    static String sniff(byte[] d) {
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "jpg";
        if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "png";
        if (d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F' && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "webp";
        return null;
    }

    public Path resolve(String key) {
        if (!key.matches("[0-9a-f\\-]{36}\\.(jpg|png|webp)")) throw ApiException.notFound("File"); // blocks path traversal
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root) || !Files.isRegularFile(p)) throw ApiException.notFound("File");
        return p;
    }
}
