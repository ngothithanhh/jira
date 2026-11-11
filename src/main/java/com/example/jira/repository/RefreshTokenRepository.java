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
    // Khóa token theo giá trị client gửi; lượt refresh sau phải đọc lại cờ revoked sau lượt trước commit.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenValue = :value")
    Optional<RefreshToken> findByTokenValueForUpdate(@Param("value") String value);
    Optional<RefreshToken> findByTokenValue(String tokenValue);
    List<RefreshToken> findByFamilyId(String familyId);
}
