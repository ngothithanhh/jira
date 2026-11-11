package com.example.jira.repository;

import com.example.jira.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {
    // Caller giữ khóa user trước bulk update để refresh không tạo con sau khi thu hồi phiên.
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically=true)
    @Query("update RefreshToken t set t.revoked = true, t.revokedAt = :now where t.user.userId = :userId and t.revoked = false")
    int revokeAllForUser(@Param("userId") int userId,@Param("now") java.time.LocalDateTime now);
    // Khóa token theo giá trị client gửi; lượt refresh sau phải đọc lại cờ revoked sau lượt trước commit.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenValue = :value")
    Optional<RefreshToken> findByTokenValueForUpdate(@Param("value") String value);
    Optional<RefreshToken> findByTokenValue(String tokenValue);
    List<RefreshToken> findByFamilyId(String familyId);
}
