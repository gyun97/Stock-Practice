package com.project.demo.common.exception.user;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;

public class InValidNewPasswordException extends BusinessException {
    public InValidNewPasswordException(){super(ErrorCode.NEW_PASSWORD_INVALID);}
}
