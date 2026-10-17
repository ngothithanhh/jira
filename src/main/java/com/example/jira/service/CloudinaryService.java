package com.example.jira.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface CloudinaryService {
    String uploadFile(MultipartFile file) throws IOException;
    byte[] downloadFile(String fileUrl) throws IOException;
    void deleteFile(String fileUrl) throws IOException;
}
