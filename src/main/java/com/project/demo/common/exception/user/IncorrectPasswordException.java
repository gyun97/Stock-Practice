package com.project.demo.common.exception.user;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;

public class IncorrectPasswordException extends BusinessException {

    public IncorrectPasswordException(){super(ErrorCode.PASSWORD_INCORRECT);}
}
