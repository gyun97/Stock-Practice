package com.project.demo.common.exception.order;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;

public class NotEnoughMoneyException extends BusinessException {
    public NotEnoughMoneyException() {super(ErrorCode.MONEY_NOT_ENOUGH);}
}
