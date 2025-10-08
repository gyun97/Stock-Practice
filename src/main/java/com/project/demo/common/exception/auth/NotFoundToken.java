package com.project.demo.common.exception.auth;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;

public class NotFoundToken extends BusinessException {

    public NotFoundToken(){super(ErrorCode.TOKEN_NOT_FOUND);}

}
