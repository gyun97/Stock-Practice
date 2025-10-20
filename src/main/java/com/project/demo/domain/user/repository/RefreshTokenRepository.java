package com.project.demo.domain.user.repository;

import com.project.demo.domain.user.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository <RefreshToken, Long> {

}
