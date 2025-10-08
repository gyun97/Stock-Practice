package com.project.demo.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {


    // 인증 관련 에러 코드
    EMAIL_DUPLICATE(HttpStatus.CONFLICT, "AUTH-001", "이미 존재하는 이메일입니다."),
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH-002", "유효하지 않은 서명의 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH-003", "유효 기간이 지난 토큰입니다."),
    TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH-004", "데이터베이스에서 해당 토큰을 찾을 수 없습니다."),
    NAME_DUPLICATE(HttpStatus.CONFLICT, "AUTH-005", "이미 사용되고 있는 이름입니다."),
    ADMIN_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH-006", "유효하지 않은 관리자 토큰입니다.");

    // 토큰 관련 에러 코드


    // 유저 관련 에러 코드


    // 주식 관련 에러 코드



    private final HttpStatus status;
    private final String code; // 프런트 분기용
    private final String message;
}
