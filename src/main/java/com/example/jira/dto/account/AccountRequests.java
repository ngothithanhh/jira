package com.example.jira.dto.account;
import jakarta.validation.constraints.*;
public final class AccountRequests {
    private AccountRequests() { }
    public record Profile(@Size(max=50) String userName,@Size(max=100) String fullName) { }
    public record Password(@NotBlank String oldPassword,@NotBlank String newPassword) { }
    public record Enabled(@NotNull Boolean enabled) { }
}
