package com.project.demo.common.exception.order;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;

public class NotFoundOrderException extends BusinessException {
    public NotFoundOrderException(){super(ErrorCode.ORDER_NOT_FOUND);}
}
