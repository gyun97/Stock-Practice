package com.project.demo.common.exception.order;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;

public class NotEnoughStockException extends BusinessException {
    public NotEnoughStockException() {super(ErrorCode.STOCK_NOT_ENOUGH);}
}
