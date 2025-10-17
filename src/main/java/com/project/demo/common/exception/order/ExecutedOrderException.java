package com.project.demo.common.exception.order;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;

public class ExecutedOrderException extends BusinessException {
    public ExecutedOrderException(){super(ErrorCode.ORDER_EXECUTED);}
}
