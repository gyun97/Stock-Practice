package com.project.demo.domain.auth.repository;

import com.project.demo.domain.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository <RefreshToken, Long> {

}
