package com.example.jira.service;

import com.example.jira.entity.RefreshToken;
import com.example.jira.entity.User;

public interface RefreshTokenService {
    void revokeSession(String tokenValue);
    // Kết quả trả sau khi transaction hoàn tất để lỗi reuse không rollback việc thu hồi family.
    record RotationResult(RefreshToken token, String error) { }

    RotationResult consumeAndRotate(String tokenValue);
    RefreshToken createRefreshToken(User user);

    RefreshToken verifyToken(String tokenValue);

    RefreshToken rotateRefreshToken(RefreshToken oldToken);

    void revokeToken(RefreshToken token);

    void revokeFamily(String familyId);
}
