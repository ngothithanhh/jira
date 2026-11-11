package com.example.jira.service.impl;

import com.example.jira.entity.RefreshToken;
import com.example.jira.entity.User;
import com.example.jira.repository.RefreshTokenRepository;
import com.example.jira.service.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final com.example.jira.repository.UserRepository userRepository;
    private final jakarta.persistence.EntityManager entityManager;
    private static final int REFRESH_TOKEN_EXP_DAYS = 7;
    /**
     * Dùng khi AuthService nhận refresh token từ client. tokenValue xác định phiên của users qua user_id.
     * Khóa refresh_tokens trước kiểm tra để hai request không cùng dùng một token chưa revoked.
     * Token còn hạn: thu hồi bản cũ và lưu bản mới cùng family trong một transaction.
     * Token bị dùng lại: thu hồi family và trả mã lỗi sau commit, không ném lỗi làm rollback việc thu hồi.
     */
    @Override
    @Transactional
    public RotationResult consumeAndRotate(String tokenValue) {
        // Giá trị rỗng không được dùng để tra cứu hoặc cấp một phiên mới.
        if (tokenValue == null || tokenValue.isBlank()) return new RotationResult(null, "Refresh token missing");
        // Lần đọc đầu chỉ lấy user_id; khóa user trước token để không deadlock với đổi mật khẩu/disable.
        RefreshToken candidate = refreshTokenRepository.findByTokenValue(tokenValue).orElse(null);
        if (candidate == null) return new RotationResult(null,"Refresh token not found");
        User owner = lockOwner(candidate);
        if (owner == null) return new RotationResult(null,"Refresh token owner missing");
        RefreshToken old = refreshTokenRepository.findByTokenValueForUpdate(tokenValue).orElse(null);
        if (old == null) return new RotationResult(null, "Refresh token not found");
        // Bỏ dữ liệu managed đã đọc trước lúc chờ user lock, rồi mới xét revoked và hạn dùng.
        entityManager.refresh(old); old.setUser(owner);
        if (!owner.isEnabled()) { revokeFamily(old.getFamilyId()); return new RotationResult(null,"Account disabled"); }
        // Lượt sau thấy revoked chỉ được thu hồi family, không được tạo thêm token con.
        if (old.isRevoked()) {
            revokeFamily(old.getFamilyId());
            return new RotationResult(null, "Refresh token reuse detected");
        }
        if (old.getExpiresAt() == null || !old.getExpiresAt().isAfter(LocalDateTime.now())) {
            return new RotationResult(null, "Refresh token expired");
        }
        old.setLastUsedAt(LocalDateTime.now());
        return new RotationResult(rotateRefreshToken(old), null);
    }

    /** Logout theo token trong body, khóa user rồi token và thu hồi cả family; token không còn tồn tại là no-op. */
    @Override @Transactional
    public void revokeSession(String tokenValue) {
        if (tokenValue == null || tokenValue.isBlank()) return;
        RefreshToken candidate = refreshTokenRepository.findByTokenValue(tokenValue).orElse(null);
        if (candidate == null || lockOwner(candidate) == null) return;
        RefreshToken token = refreshTokenRepository.findByTokenValueForUpdate(tokenValue).orElse(null);
        if (token != null) { entityManager.refresh(token); revokeFamily(token.getFamilyId()); }
    }

    /** user_id đến từ refresh_tokens vừa tra, không từ client; refresh sau khóa để dùng enabled/auth_version mới nhất. */
    private User lockOwner(RefreshToken candidate) {
        if (candidate.getUser() == null) return null;
        User user = userRepository.findByIdForUpdate(candidate.getUser().getUserId()).orElse(null);
        if (user != null) entityManager.refresh(user);
        return user;
    }
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
