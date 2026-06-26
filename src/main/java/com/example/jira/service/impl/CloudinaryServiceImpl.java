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
                "resource_type", "auto"
        ));
        return uploadResult.get("secure_url").toString();
    }

    @Override
    public void deleteFile(String fileUrl) throws IOException {
        // Extract publicId from URL
        // A typical Cloudinary URL: https://res.cloudinary.com/<cloud_name>/image/upload/v1234567890/<public_id>.<ext>
        // Extracting it perfectly depends on folder structures, but for basic usage:
        String publicId = extractPublicId(fileUrl);
        if (publicId != null && !publicId.isEmpty()) {
            // we use resource_type auto but delete needs specific or we can try raw/image. 
            // In Cloudinary, deleting raw files requires resource_type "raw" or "image".
            // Since we don't know easily, we can try deleting as "image" then "raw".
            try {
                cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "image"));
                cloudinary.uploader().destroy(publicId, ObjectUtils.asMap("resource_type", "raw"));
            } catch (Exception e) {
                System.out.println("Could not delete file from Cloudinary: " + e.getMessage());
            }
        }
    }

    private String extractPublicId(String fileUrl) {
        try {
            String[] parts = fileUrl.split("/");
            String lastPart = parts[parts.length - 1];
            int dotIndex = lastPart.lastIndexOf('.');
            if (dotIndex > 0) {
                return lastPart.substring(0, dotIndex);
            }
            return lastPart;
        } catch (Exception e) {
            return null;
        }
    }
}
