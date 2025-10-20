package com.project.demo.domain.userstock.repository;

import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.userstock.entity.UserStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import javax.swing.text.html.Option;
import java.util.Optional;

public interface UserStockRepository extends JpaRepository<UserStock, Long> {

    Optional<UserStock> findByUserAndStock(User user, Stock stock);
}
