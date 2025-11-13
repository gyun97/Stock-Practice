package com.project.demo.domain.order.service;

import com.project.demo.domain.order.dto.response.OrderResponse;
import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.user.entity.User;

import java.util.List;

public interface OrderService {

    public String buyingStock(Long userId, String ticker, int quantity);

    public void executeBuy(Order order, User user, Stock stock, int price, int quantity, int totalPrice);

    public String sellingStock(Long userId, String ticker, int quantity);

    public void executeSell(Order order, User user, Stock stock, int price, int quantity, int totalPrice);

    public String reserveBuy(Long userId, String ticker, int quantity, int targetPrice);

    public String reserveSell(Long userId, String ticker, int quantity, int targetPrice);

    public void executeReservedOrders();

    public OrderResponse cancelReservation(Long orderId);

    public List<OrderResponse> getMyAllOrders(Long userId);

    public List<OrderResponse> getNormalOrders(Long userId);

    public List<OrderResponse> getReservationOrders(Long userId);




}
