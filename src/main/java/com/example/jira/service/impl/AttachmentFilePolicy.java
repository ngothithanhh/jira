package com.example.jira.service.impl;

import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;

public final class AttachmentFilePolicy {
    public static final long MAX_BYTES = 10 * 1024 * 1024;
    private static final Map<String, String> TYPES = Map.of(
            "pdf", "application/pdf", "png", "image/png", "jpg", "image/jpeg", "jpeg", "image/jpeg",
            "txt", "text/plain", "csv", "text/csv",
            "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private AttachmentFilePolicy() {}

    public static void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) reject(HttpStatus.BAD_REQUEST, "Empty file");
        if (file.getSize() > MAX_BYTES) reject(HttpStatus.PAYLOAD_TOO_LARGE, "Maximum attachment size is 10 MiB");
        String name = file.getOriginalFilename();
        if (name == null || name.contains("/") || name.contains("\\") || name.contains("\u0000") || name.length() > 255)
            reject(HttpStatus.BAD_REQUEST, "Invalid filename");
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!TYPES.containsKey(extension) || !TYPES.get(extension).equalsIgnoreCase(file.getContentType()))
            reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported attachment type");
        try (var input = file.getInputStream()) {
            byte[] header = input.readNBytes(512);
            boolean valid = switch (extension) {
                case "pdf" -> starts(header, new byte[]{37, 80, 68, 70, 45});
                case "png" -> starts(header, new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10});
                case "jpg", "jpeg" -> starts(header, new byte[]{(byte)255, (byte)216, (byte)255});
                case "docx", "xlsx" -> validOffice(file, extension);
                default -> validText(file);
            };
            if (!valid) reject(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "File content does not match its type");
        } catch (IOException ex) {
            reject(HttpStatus.BAD_REQUEST, "Cannot read attachment");
        }
    }

    private static boolean validOffice(MultipartFile file, String extension) throws IOException {
        boolean contentTypes = false, document = false;
        long expanded = 0;
        int count = 0;
        try (var zip = new java.util.zip.ZipInputStream(file.getInputStream())) {
            java.util.zip.ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++count > 2000) return false;
                String name = entry.getName();
                if (name.toLowerCase(Locale.ROOT).contains("vbaproject")) return false;
                contentTypes |= name.equals("[Content_Types].xml");
                document |= name.equals(extension.equals("docx") ? "word/document.xml" : "xl/workbook.xml");
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    expanded += read;
                    if (expanded > 50 * 1024 * 1024) return false;
                }
            }
        }
        return contentTypes && document;
    }

    private static boolean validText(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        for (byte b : bytes) if (b == 0) return false;
        try {
            java.nio.charset.StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
            return true;
        } catch (java.nio.charset.CharacterCodingException ex) { return false; }
    }

    private static boolean starts(byte[] bytes, byte[] prefix) {
        if (bytes.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) if (bytes[i] != prefix[i]) return false;
        return true;
    }
    private static void reject(HttpStatus status, String message) { throw new ResponseStatusException(status, message); }
}
