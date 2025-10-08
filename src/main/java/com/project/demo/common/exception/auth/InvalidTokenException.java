package com.project.demo.common.exception.auth;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;

public class InvalidTokenException extends BusinessException {

    public InvalidTokenException(){super(ErrorCode.TOKEN_INVALID);}
}
