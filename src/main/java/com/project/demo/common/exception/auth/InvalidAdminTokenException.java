package com.project.demo.common.exception.auth;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;
import io.micrometer.core.instrument.config.validate.Validated;

public class InvalidAdminTokenException extends BusinessException {
    public InvalidAdminTokenException(){super(ErrorCode.ADMIN_TOKEN_INVALID);}
}
