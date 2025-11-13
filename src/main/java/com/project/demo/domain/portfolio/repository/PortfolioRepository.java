package com.project.demo.domain.portfolio.repository;

import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    Optional<Portfolio> findByUser(User user);
    Optional<Portfolio> findByUserId(Long userId);

}
