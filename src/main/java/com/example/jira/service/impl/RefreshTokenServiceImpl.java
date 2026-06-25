package com.example.jira.service.impl;

import com.example.jira.entity.RefreshToken;
import com.example.jira.entity.User;
import com.example.jira.repository.RefreshTokenRepository;
import com.example.jira.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private static final int REFRESH_TOKEN_EXP_DAYS = 7;
    @Override
    public RefreshToken createRefreshToken(User user){
        RefreshToken token = new RefreshToken();

        token.setUser(user);
        token.setTokenValue(UUID.randomUUID().toString());
        token.setJti(UUID.randomUUID().toString());
        token.setFamilyId(UUID.randomUUID().toString());

        token.setCreatedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusDays(REFRESH_TOKEN_EXP_DAYS));

        token.setRevoked(false);

        return refreshTokenRepository.save(token);
    }

    @Override
    public RefreshToken verifyToken(String tokenValue){
        RefreshToken token = refreshTokenRepository.findByTokenValue(tokenValue).orElseThrow(()->new RuntimeException("Không tìm thấy refresh token"));

        if(token.isRevoked()){
            revokeFamily(token.getFamilyId());
            throw new RuntimeException("Refresh token reuse detected");
        }
        if(token.getExpiresAt().isBefore(LocalDateTime.now())){
            throw new RuntimeException("Refresh token expired");
        }

        token.setLastUsedAt(LocalDateTime.now());

        return refreshTokenRepository.save(token);
    }

    @Override
    public RefreshToken rotateRefreshToken(RefreshToken oldToken){
        oldToken.setRevoked(true);
        oldToken.setRevokedAt(LocalDateTime.now());

        refreshTokenRepository.save(oldToken);

        RefreshToken newToken = new RefreshToken();

        newToken.setUser(oldToken.getUser());
        newToken.setTokenValue(UUID.randomUUID().toString());
        newToken.setJti(UUID.randomUUID().toString());

        newToken.setFamilyId(oldToken.getFamilyId());

        newToken.setCreatedAt(LocalDateTime.now());
        newToken.setExpiresAt(LocalDateTime.now().plusDays(7));

        newToken.setRevoked(false);

        return refreshTokenRepository.save(newToken);

    }

    @Override
    public void revokeToken(RefreshToken token){
        token.setRevoked(true);
        token.setRevokedAt(LocalDateTime.now());

        refreshTokenRepository.save(token);

    }

    @Override
    public void revokeFamily(String familyId){

        List<RefreshToken> tokens = refreshTokenRepository.findByFamilyId(familyId);
        for(RefreshToken token : tokens){
            token.setRevoked(true);
            token.setRevokedAt(LocalDateTime.now());

        }
        refreshTokenRepository.saveAll(tokens);

    }
}
