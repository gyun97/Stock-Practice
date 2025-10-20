package com.project.demo.common.exception.user;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;

public class NotFoundUserException extends BusinessException {

    public NotFoundUserException(){super(ErrorCode.USER_NOT_FOUND);}
}
