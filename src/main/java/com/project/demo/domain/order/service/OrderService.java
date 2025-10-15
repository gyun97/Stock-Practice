package com.project.demo.domain.order.service;

import com.project.demo.domain.order.entity.Order;
import com.project.demo.domain.stock.entity.Stock;
import com.project.demo.domain.user.entity.User;

public interface OrderService {

    public String buyingStock(Long userId, String ticker, int quantity);

    public void executeBuy(Order order, User user, Stock stock, int price, int quantity, int totalPrice);


}
