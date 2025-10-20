package com.project.demo.common.exception.auth;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;

public class NotFoundTokenException extends BusinessException {

    public NotFoundTokenException(){super(ErrorCode.TOKEN_NOT_FOUND);}

}
