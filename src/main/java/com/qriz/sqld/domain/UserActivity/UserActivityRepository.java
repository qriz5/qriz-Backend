package com.qriz.sqld.domain.userActivity;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserActivityRepository extends JpaRepository<UserActivity, Long> {
    List<UserActivity> findByUserId(Long userId);
    List<UserActivity> findByUserIdAndTestInfo(Long userId, String testInfo);
    Optional<UserActivity> findByIdAndUserId(Long activityId, Long userId);
}
