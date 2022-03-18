package com.shang.poi.service;

import com.shang.poi.common.FileNameComparator;
import com.shang.poi.config.FileProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
public class StorageService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Comparator<String> COMPARATOR = new FileNameComparator();

    private final Path root;
    private final Integer maxHistory;

    public StorageService(FileProperties fileProperties) {
        this.root = Paths.get(fileProperties.getXlsxPath());
        this.maxHistory = fileProperties.getMaxHistory();
    }

    @PostConstruct
    public void init() {
        if (Files.notExists(root)) {
            try {
                Files.createDirectories(root);
                log.info("Create directories: {}", root.toAbsolutePath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public Path generate() {
        return root.resolve(String.format("%s %03d.%s", LocalDateTime.now().format(FileNameComparator.FORMATTER), RANDOM.nextInt(1000), "xlsx"));
    }

    public Stream<Path> loadAll() {
        try {
            final List<Path> paths = Files.walk(root, 1)
                    .filter(path -> !path.equals(root))
                    .sorted((left, right) -> COMPARATOR.reversed().compare(left.getFileName().toString(), right.getFileName().toString()))
                    .map(root::relativize).collect(Collectors.toList());
            CompletableFuture.runAsync(() -> paths.stream().skip(maxHistory).forEach(this::delete));
            return paths.stream().limit(maxHistory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read stored files", e);
        }
    }

    public Path load(String filename) {
        return root.resolve(filename);
    }

    public Resource loadAsResource(String filename) {
        try {
            Path file = load(filename);
            Resource resource = new UrlResource(file.toUri());
            if (resource.exists() || resource.isReadable()) {
                return resource;
            } else {
                throw new RuntimeException("Could not read file: " + filename);
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException("Could not read file: " + filename, e);
        }
    }

    public void deleteAll() {
        FileSystemUtils.deleteRecursively(root.toFile());
    }

    public void delete(Path path) {
        try {
            Files.deleteIfExists(root.resolve(path.getFileName()));
        } catch (IOException ignore) {
        }
    }

}
