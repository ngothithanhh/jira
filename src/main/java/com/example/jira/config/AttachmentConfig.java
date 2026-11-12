package com.example.jira.config;

/** Nạp giới hạn multipart cho lưu trữ PostgreSQL local, không phụ thuộc bean hoặc credential Cloudinary. */
@org.springframework.context.annotation.Configuration
@org.springframework.context.annotation.PropertySource("classpath:application-upload.properties")
public class AttachmentConfig { }
