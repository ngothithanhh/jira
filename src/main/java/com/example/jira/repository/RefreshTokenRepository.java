package com.example.jira.repository;

import com.example.jira.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Integer> {
    Optional<RefreshToken> findByTokenValue(String tokenValue);
    List<RefreshToken> findByFamilyId(String familyId);
}
