package com.example.jira.repository;

import com.example.jira.dto.project.MemberCandidate;
import com.example.jira.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    long countByEnabledTrue();
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.userId = :id")
    Optional<User> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") int id);
    Optional<User> findByUserName(String username);
    Optional<User> findByEmail(String email);
    /** Email duy nhất của users giới hạn kết quả tối đa một hàng. */
    @Query("""
            select new com.example.jira.dto.project.MemberCandidate(u.userId, u.userName, u.fullName)
            from User u
            where u.email = :email and u.enabled = true
              and not exists (
                select m from ProjectMember m
                where m.project.projectId = :projectId and m.user.userId = u.userId
              )
            """)
    Optional<MemberCandidate> findProjectCandidate(@Param("projectId") int projectId, @Param("email") String email);
    boolean existsByEmail(String email);
}
