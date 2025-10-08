package com.project.demo.common.exception;

import com.project.demo.common.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 비즈니스 예외(BaseException) 공통 처리
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(BusinessException e, HttpServletRequest req) {
        var ec = e.getErrorCode();

        if (ec.getStatus().is4xxClientError()) { // 클라이언트의 4xx 예외는 warn
            log.warn("상태: {}, 코드: {}, 메서드: {}, URI: {}", ec.getStatus(), ec.getCode(), req.getMethod(), req.getRequestURI());
        } else  { // 서버 버그인 5xx 등의 예외는 error
            log.error("상태: {}, 코드: {}, 메서드: {}, URI: {}", ec.getStatus(), ec.getCode(), req.getMethod(), req.getRequestURI(), e);
        }
        return ErrorResponse.error(e);
    }

    /*
    Bean Validation(@Valid를 통한 요청값 검증) 예외 처리
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {
        String errorMessages = e.getBindingResult().getFieldErrors()
                .stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));

        log.warn("Validation Failed: {}, Method: {}, URI: {}", errorMessages, req.getMethod(), req.getRequestURI());

        return ErrorResponse.error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", errorMessages);
    }
}


