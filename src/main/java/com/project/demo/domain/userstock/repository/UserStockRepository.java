package com.project.demo.domain.userstock.repository;

import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.userstock.entity.UserStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserStockRepository extends JpaRepository<UserStock, Long> {

    Optional<UserStock> findByUserAndStock(User user, Stock stock);

    List<UserStock> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT us FROM UserStock us WHERE us.user.id = :userId AND us.stock.id = :stockId")
    Optional<UserStock> findByUserAndStockWithLock(Long userId, Long stockId);
}
