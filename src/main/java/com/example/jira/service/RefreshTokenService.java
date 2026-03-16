package com.example.jira.service;

import com.example.jira.entity.RefreshToken;
import com.example.jira.entity.User;

public interface RefreshTokenService {
    RefreshToken createRefreshToken(User user);

    RefreshToken verifyToken(String tokenValue);

    RefreshToken rotateRefreshToken(RefreshToken oldToken);

    void revokeToken(RefreshToken token);

    void revokeFamily(String familyId);
}
