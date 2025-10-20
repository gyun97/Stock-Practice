package com.project.demo.common.exception.auth;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;

public class NewPasswordMismatch extends BusinessException {
    public NewPasswordMismatch(){super(ErrorCode.NEW_PASSWORD_MISMATCH);}
}
