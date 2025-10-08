package com.project.demo.common.exception.auth;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;

public class DuplicateNameException extends BusinessException {

    public DuplicateNameException(){super(ErrorCode.NAME_DUPLICATE);}
}
