//package com.project.demo.common.exception;
//
//import com.project.demo.common.response.ApiResponse;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.http.HttpStatus;
//import org.springframework.http.ResponseEntity;
//import org.springframework.web.bind.annotation.ExceptionHandler;
//import org.springframework.web.bind.annotation.RestControllerAdvice;
//
//@RestControllerAdvice
//@Slf4j
//public class GlobalExceptionHandler {
//
//    // 비즈니스 커스텀 예외
//    @ExceptionHandler(UserNotFoundException.class)
//    public ResponseEntity<ApiResponse<Void>> handleUserNotFound(UserNotFoundException ex) {
//        log.warn("UserNotFoundException: {}", ex.getMessage());
//        return ResponseEntity
//                .status(HttpStatus.NOT_FOUND)
//                .body(ApiResponse.createError(HttpStatus.NOT_FOUND.value(), ex.getMessage()));
//    }
//
//    // JWT 등 인증 관련 예외
//    @ExceptionHandler(TokenExpiredException.class)
//    public ResponseEntity<ApiResponse<Void>> handleTokenExpired(TokenExpiredException ex) {
//        log.warn("TokenExpiredException: {}", ex.getMessage());
//        return ResponseEntity
//                .status(HttpStatus.UNAUTHORIZED)
//                .body(ApiResponse.createError(HttpStatus.UNAUTHORIZED.value(), "토큰이 만료되었습니다."));
//    }
//
//    // 표준 예외 (잘못된 요청)
//    @ExceptionHandler(IllegalArgumentException.class)
//    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
//        log.warn("잘못된 요청 인자: {}", ex.getMessage());
//        return ResponseEntity
//                .status(HttpStatus.BAD_REQUEST)
//                .body(ApiResponse.createError(HttpStatus.BAD_REQUEST.value(), ex.getMessage()));
//    }
//
//    // 표준 예외 (잘못된 상태)
//    @ExceptionHandler(IllegalStateException.class)
//    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
//        log.warn("잘못된 상태 요청: {}", ex.getMessage());
//        return ResponseEntity
//                .status(HttpStatus.UNAUTHORIZED)
//                .body(ApiResponse.createError(HttpStatus.UNAUTHORIZED.value(), ex.getMessage()));
//    }
//
//    // 예상 못한 모든 예외 처리
//    @ExceptionHandler(Exception.class)
//    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex) {
//        log.error("서버 내부 오류: ", ex);
//        return ResponseEntity
//                .status(HttpStatus.INTERNAL_SERVER_ERROR)
//                .body(ApiResponse.createError(HttpStatus.INTERNAL_SERVER_ERROR.value(), "서버 내부 오류가 발생했습니다."));
//    }
//}