package com.project.demo.common.exception;
import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage()); // getMessage()가 ErrorCode 메시지를 기본으로 내보내도록
        this.errorCode = errorCode;
    }

}
