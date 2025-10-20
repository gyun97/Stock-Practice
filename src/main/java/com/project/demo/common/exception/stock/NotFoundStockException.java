package com.project.demo.common.exception.stock;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;

public class NotFoundStockException extends BusinessException {
    public NotFoundStockException(){super(ErrorCode.STOCK_NOT_FOUND);}
}
