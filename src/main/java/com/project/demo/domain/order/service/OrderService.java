package com.project.demo.domain.order.service;

import com.project.demo.domain.order.dto.response.OrderResponse;
import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.user.entity.User;

import java.util.List;

public interface OrderService {

    public String buyingStock(Long userId, String ticker, int quantity);

    public void executeBuy(Order order, User user, Stock stock, int price, int quantity, long totalPrice);

    public String sellingStock(Long userId, String ticker, int quantity);

    public void executeSell(Order order, User user, Stock stock, int price, int quantity, long totalPrice);

    public String reserveBuy(Long userId, String ticker, int quantity, int targetPrice);

    public String reserveSell(Long userId, String ticker, int quantity, int targetPrice);

    public void executeReservedOrders();

    /**
     * 특정 종목의 예약 주문 체결 (이벤트 기반)
     * 
     * @param ticker       종목 코드
     * @param currentPrice 현재가
     */
    public void executeReservedOrdersForTicker(String ticker, int currentPrice);

    public OrderResponse cancelReservation(Long orderId);

    public List<OrderResponse> getMyAllOrders(Long userId);

    public List<OrderResponse> getNormalOrders(Long userId);

    public List<OrderResponse> getReservationOrders(Long userId);

}
