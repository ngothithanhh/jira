package com.example.jira.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Loads local development variables from the project-root .env file so they
 * are available to Spring placeholders such as ${APP_JWT_SECRET}.
 */
@Configuration
@PropertySource(value = "file:.env", ignoreResourceNotFound = true)
public class EnvConfig {
}
