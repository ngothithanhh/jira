package com.example.jira.service.impl;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.example.jira.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryServiceImpl implements CloudinaryService {

    private final Cloudinary cloudinary;

    @Override
    public String uploadFile(MultipartFile file) throws IOException {
        // resource_type auto allows raw files (docs, xlsx, pdf) as well as images
        Map uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                "resource_type", "auto", "type", "authenticated"
        ));
        return uploadResult.get("secure_url").toString();
    }

    @Override
    public byte[] downloadFile(String fileUrl) throws IOException {
        java.net.URI uri = java.net.URI.create(fileUrl);
        String[] parts = uri.getPath().split("/", 5);
        if (!"https".equals(uri.getScheme()) || !"res.cloudinary.com".equals(uri.getHost())
                || parts.length < 5 || !java.util.Set.of("upload", "authenticated").contains(parts[3]))
            throw new IOException("Đường dẫn lưu trữ không hợp lệ");
        String url = fileUrl;
        if ("authenticated".equals(parts[3])) {
            String path = parts[4].replaceFirst("^v[0-9]+/", "");
            url = cloudinary.url().secure(true).signed(true).resourceType(parts[2]).type("authenticated").generate(path);
        }
        var connection = (java.net.HttpURLConnection) java.net.URI.create(url).toURL().openConnection();
        connection.setInstanceFollowRedirects(false);
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(30000);
        try {
            if (connection.getResponseCode() != 200) throw new IOException("Không thể tải tệp từ kho lưu trữ");
            try (var input = connection.getInputStream()) {
                byte[] bytes = input.readNBytes((int) AttachmentFilePolicy.MAX_BYTES + 1);
                if (bytes.length > AttachmentFilePolicy.MAX_BYTES) throw new IOException("Tệp đính kèm vượt quá dung lượng cho phép");
                return bytes;
            }
        } finally { connection.disconnect(); }
    }

    @Override
    public void deleteFile(String fileUrl) throws IOException {
        java.net.URI uri = java.net.URI.create(fileUrl);
        String[] parts = uri.getPath().split("/", 5);
        if (parts.length < 5 || !java.util.Set.of("upload", "authenticated").contains(parts[3])
                || !java.util.Set.of("image", "raw", "video").contains(parts[2]))
            throw new IOException("Đường dẫn tệp lưu trữ không hợp lệ");
        String publicId = parts[4].replaceFirst("^v[0-9]+/", "");
        if (!"raw".equals(parts[2])) {
            int dot = publicId.lastIndexOf('.');
            if (dot > publicId.lastIndexOf('/')) publicId = publicId.substring(0, dot);
        }
        Map result = cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", parts[2], "type", parts[3], "invalidate", true));
        if (!java.util.Set.of("ok", "not found").contains(String.valueOf(result.get("result"))))
            throw new IOException("Không thể xóa tệp trong kho lưu trữ");
    }
}
