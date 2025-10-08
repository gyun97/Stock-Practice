package com.project.demo.common.exception.auth;

import com.project.demo.common.exception.BusinessException;
import com.project.demo.common.exception.ErrorCode;


public class DuplicateEmailException extends BusinessException {

    public DuplicateEmailException() {super(ErrorCode.EMAIL_DUPLICATE);}
}
