package com.project.demo.domain.execution.service;

import com.project.demo.common.exception.order.NotEnoughMoneyException;
import com.project.demo.common.exception.order.NotEnoughStockException;
import com.project.demo.common.exception.portfolio.NotFoundPortfolioException;
import com.project.demo.domain.execution.entity.Execution;
import com.project.demo.domain.execution.repository.ExecutionRepository;
import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.order.enums.OrderType;
import com.project.demo.domain.order.repository.OrderRepository;
import com.project.demo.domain.portfolio.entity.Portfolio;
import com.project.demo.domain.portfolio.repository.PortfolioRepository;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.user.entity.User;
import com.project.demo.domain.userstock.entity.UserStock;
import com.project.demo.domain.userstock.repository.UserStockRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import io.micrometer.observation.annotation.Observed;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExecutionProcessServiceImpl implements ExecutionProcessService {

    private final ExecutionRepository executionRepository;
    private final PortfolioRepository portfolioRepository;
    private final UserStockRepository userStockRepository;
    private final OrderRepository orderRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void executeBuy(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        processBuy(orderId, user, stock, price, quantity, totalPrice);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED)
    public void executeSell(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        processSell(orderId, user, stock, price, quantity, totalPrice);
    }



    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Observed(name = "executeReservedOrder", contextualName = "executeReservedOrder.span")
    public void executeReservedOrder(Long orderId, int currentPrice) {
        Order order = orderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다. ID: " + orderId));

        if (order.isExecuted()) {
            return;
        }

        // 공통 로직으로 속성 전달
        long totalPrice = (long) currentPrice * order.getQuantity();

        if (order.getType() == OrderType.BUY) {
            processBuy(orderId, order.getUser(), order.getStock(), currentPrice, order.getQuantity(), totalPrice);
        } else if (order.getType() == OrderType.SELL) {
            processSell(orderId, order.getUser(), order.getStock(), currentPrice, order.getQuantity(), totalPrice);
        }
    }

    private void processBuy(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        Order order = orderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다. ID: " + orderId));

        if (order.isExecuted()) {
            return;
        }

        Portfolio portfolio = portfolioRepository.findWithLockByUserId(user.getId())
                .orElseThrow(NotFoundPortfolioException::new);
        entityManager.refresh(portfolio, LockModeType.PESSIMISTIC_WRITE);

        if (portfolio.getBalance() < totalPrice) {
            throw new NotEnoughMoneyException();
        }

        Execution execution = Execution.builder()
                .order(order)
                .type(OrderType.BUY)
                .price(price)
                .quantity(quantity)
                .totalPrice(totalPrice)
                .build();
        executionRepository.save(execution);

        UserStock userStock = getOrCreateUserStock(user, stock, portfolio, price, quantity);
        userStock.increasePurchaseAmount((long) price * quantity);
        portfolio.addUserStock(userStock);
        updatePortfolioAfterBuy(portfolio, price, quantity);

        order.markExecuted();
        entityManager.flush();
    }

    private void processSell(Long orderId, User user, Stock stock, int price, int quantity, long totalPrice) {
        Order order = orderRepository.findWithLockById(orderId)
                .orElseThrow(() -> new RuntimeException("주문을 찾을 수 없습니다. ID: " + orderId));

        if (order.isExecuted()) {
            return;
        }

        Execution execution = Execution.builder()
                .order(order)
                .type(OrderType.SELL)
                .price(price)
                .quantity(quantity)
                .totalPrice(totalPrice)
                .build();
        executionRepository.save(execution);

        Portfolio portfolio = portfolioRepository.findWithLockByUserId(user.getId())
                .orElseThrow(NotFoundPortfolioException::new);
        entityManager.refresh(portfolio, LockModeType.PESSIMISTIC_WRITE);

        UserStock userStock = userStockRepository.findByUserAndStockWithLock(user.getId(), stock.getId())
                .orElseThrow(NotEnoughStockException::new);
        entityManager.refresh(userStock, LockModeType.PESSIMISTIC_WRITE);

        if (userStock.getTotalQuantity() < quantity) {
            throw new NotEnoughStockException();
        }

        updateUserStockAndPortfolioAfterSell(user, stock, portfolio, userStock, price, quantity);

        order.markExecuted();
        entityManager.flush();
    }

    private UserStock getOrCreateUserStock(User user, Stock stock, Portfolio portfolio, int price, int quantity) {
        try {
            Optional<UserStock> optionalUserStock = userStockRepository.findByUserAndStockWithLock(user.getId(),
                    stock.getId());
            if (optionalUserStock.isPresent()) {
                UserStock userStock = optionalUserStock.get();
                entityManager.refresh(userStock, LockModeType.PESSIMISTIC_WRITE);
                userStock.updateAfterBuy(price, quantity);
                return userStock;
            } else {
                UserStock userStock = UserStock.builder()
                        .user(user).stock(stock).avgPrice(price).totalQuantity(quantity)
                        .ticker(stock.getTicker()).portfolio(portfolio)
                        .userName(user.getName()).stockName(stock.getName())
                        .build();
                return userStockRepository.saveAndFlush(userStock);
            }
        } catch (DataIntegrityViolationException e) {
            UserStock userStock = userStockRepository.findByUserAndStockWithLock(user.getId(), stock.getId())
                    .orElseThrow(() -> new RuntimeException("UserStock 조회 실패"));
            entityManager.refresh(userStock, LockModeType.PESSIMISTIC_WRITE);
            userStock.updateAfterBuy(price, quantity);
            return userStock;
        }
    }

    private void updateUserStockAndPortfolioAfterSell(User user, Stock stock, Portfolio portfolio, UserStock userStock,
            int price, int quantity) {
        int remainingQuantity = userStock.getTotalQuantity() - quantity;
        long avgPrice = userStock.getAvgPrice();
        long profit = (long) (price - avgPrice) * quantity;

        log.info("[매도체결] {} 매도 수익: {}원", stock.getName(), profit);

        if (remainingQuantity > 0) {
            userStock.updateQuantity(remainingQuantity);
            userStock.decreasePurchaseAmount(quantity);
        } else {
            userStockRepository.delete(userStock);
            user.getUserStocks().remove(userStock);
            stock.getUserStocks().remove(userStock);
            portfolio.getUserStocks().remove(userStock);
        }
        updatePortfolioAfterSell(portfolio, price, quantity);
    }

    private void updatePortfolioAfterBuy(Portfolio portfolio, int buyPrice, int quantity) {
        long increaseStockValue = (long) buyPrice * quantity;
        portfolio.increaseStockAsset(increaseStockValue);
        portfolio.decreaseBalance(increaseStockValue);
        portfolio.increaseTotalQuantity(quantity);
        portfolio.recalculateTotalAsset();
        portfolio.updateHoldCount();
    }

    private void updatePortfolioAfterSell(Portfolio portfolio, int sellPrice, int quantity) {
        long decreasedStockValue = (long) sellPrice * quantity;
        portfolio.decreaseStockAsset(decreasedStockValue);
        portfolio.increaseBalance(decreasedStockValue);
        portfolio.decreaseTotalQuantity(quantity);
        portfolio.recalculateTotalAsset();
        portfolio.updateHoldCount();
    }
}
