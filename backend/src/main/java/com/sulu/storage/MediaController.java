package com.sulu.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class MediaController {
    private final StorageService storage;
    public MediaController(StorageService storage) { this.storage = storage; }

    @GetMapping("/media/{key}")
    public ResponseEntity<Resource> get(@PathVariable String key) {
        Path p = storage.resolve(key);
        MediaType type = key.endsWith(".png") ? MediaType.IMAGE_PNG : key.endsWith(".webp") ? MediaType.parseMediaType("image/webp") : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok().contentType(type).header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic()).body(new FileSystemResource(p));
    }
}
