package com.example.jira.repository;

import com.example.jira.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.userId = :id")
    Optional<User> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") int id);
    Optional<User> findByUserName(String username);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
