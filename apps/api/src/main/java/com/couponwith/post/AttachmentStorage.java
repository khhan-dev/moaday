package com.couponwith.post;

import com.couponwith.common.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.BufferedInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;
import java.util.Locale;

@Component
public class AttachmentStorage {
    private static final long MAX_SIZE = 20L * 1024 * 1024;
    private static final Set<String> BLOCKED_TYPES = Set.of("text/html", "image/svg+xml", "application/x-msdownload",
            "application/x-sh", "application/x-bat", "application/javascript", "text/javascript");
    private static final Set<String> BLOCKED_EXTENSIONS = Set.of("exe", "dll", "com", "scr", "msi", "bat", "cmd",
            "ps1", "sh", "js", "mjs", "cjs", "html", "htm", "svg", "php", "jar", "war");
    private final Path root;
    public AttachmentStorage(@Value("${moaday.storage.path:./data/uploads}") String path) {
        this.root = Path.of(path).toAbsolutePath().normalize();
        try { Files.createDirectories(root); } catch (IOException exception) { throw new IllegalStateException("첨부파일 저장소를 만들 수 없습니다.", exception); }
    }
    public StoredFile store(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > MAX_SIZE) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"INVALID_ATTACHMENT_SIZE","첨부파일은 20MB 이하만 올릴 수 있습니다.");
        var type = file.getContentType() == null ? "application/octet-stream"
                : file.getContentType().split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        if (BLOCKED_TYPES.contains(type)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,"BLOCKED_ATTACHMENT_TYPE","실행 가능한 파일 형식은 올릴 수 없습니다.");
        var original = safeFilename(file.getOriginalFilename());
        var extensionIndex = original.lastIndexOf('.');
        var extension = extensionIndex < 0 ? "" : original.substring(extensionIndex + 1).toLowerCase(Locale.ROOT);
        if (BLOCKED_EXTENSIONS.contains(extension)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                "BLOCKED_ATTACHMENT_EXTENSION", "실행되거나 웹 페이지로 열릴 수 있는 파일은 올릴 수 없습니다.");
        var key = UUID.randomUUID().toString();
        try (var input = new BufferedInputStream(file.getInputStream())) {
            input.mark(1024);
            var header = input.readNBytes(512);
            input.reset();
            if (looksExecutable(header)) throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "BLOCKED_ATTACHMENT_CONTENT", "파일 내용이 허용되지 않는 형식입니다.");
            Files.copy(input, resolve(key), StandardCopyOption.REPLACE_EXISTING);
        }
        catch (IOException exception) { throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,"ATTACHMENT_STORE_FAILED","첨부파일을 저장하지 못했습니다."); }
        return new StoredFile(original, type, file.getSize(), key);
    }
    public Resource load(String key) {
        try { var resource = new UrlResource(resolve(key).toUri()); if (!resource.exists()) throw new IOException(); return resource; }
        catch (IOException exception) { throw new ApiException(HttpStatus.NOT_FOUND,"ATTACHMENT_FILE_NOT_FOUND","첨부파일을 찾을 수 없습니다."); }
    }
    public void delete(String key) { try { Files.deleteIfExists(resolve(key)); } catch (IOException ignored) {} }
    private Path resolve(String key) { var path = root.resolve(key).normalize(); if (!path.startsWith(root)) throw new IllegalArgumentException("invalid storage key"); return path; }
    private String safeFilename(String value) {
        var raw = value == null ? "attachment" : value.replace('\\', '/');
        var name = raw.substring(raw.lastIndexOf('/') + 1).trim();
        if (name.isBlank() || name.length() > 255 || name.chars().anyMatch(character -> Character.isISOControl(character))) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_ATTACHMENT_NAME", "올바른 파일 이름을 사용해 주세요.");
        }
        return name;
    }
    private boolean looksExecutable(byte[] header) {
        if (header.length >= 2 && header[0] == 'M' && header[1] == 'Z') return true;
        var text = new String(header, StandardCharsets.UTF_8).stripLeading().toLowerCase(Locale.ROOT);
        return text.startsWith("<!doctype html") || text.startsWith("<html") || text.startsWith("<script")
                || text.startsWith("<?php") || text.startsWith("#!");
    }
    public record StoredFile(String originalName,String contentType,long sizeBytes,String storageKey) {}
}
