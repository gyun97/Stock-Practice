package com.project.demo.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.graphql.GraphQlProperties;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 인증 관련 에러 코드
    EMAIL_DUPLICATE(HttpStatus.CONFLICT, "AUTH-001", "이미 존재하는 이메일입니다."),
    NAME_DUPLICATE(HttpStatus.CONFLICT, "AUTH-005", "이미 사용되고 있는 이름입니다."),
    PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "AUTH-007", "비밀번호가 일치하지 않습니다."),
    NEW_PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "AUTH-008", "새로운 두 비밀번호가 일치하지 않습니다"),
    NEW_PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "USER-002", "새로운 비밀번호는 기존 비밀번호와 동일하지 않아야 합니다"),

    // 토큰 관련 에러 코드
    TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH-002", "유효하지 않은 서명의 토큰입니다."),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "AUTH-003", "유효 기간이 지난 토큰입니다."),
    TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "AUTH-004", "데이터베이스에서 해당 토큰을 찾을 수 없습니다."),
    ADMIN_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "AUTH-006", "유효하지 않은 관리자 토큰입니다."),

    // 유저 관련 에러 코드
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER-001", "해당 계정의 사용자를 찾을 수 없습니다."),

    // 주문 관련 에러 코드
    MONEY_NOT_ENOUGH(HttpStatus.BAD_REQUEST, "ORDER-001", "보유 금액이 부족합니다"),
    STOCK_NOT_ENOUGH(HttpStatus.BAD_REQUEST, "ORDER-002", "보유 주식 수가 부족합니다"),
    ORDER_NOT_FOUND(HttpStatus.BAD_REQUEST, "ORDER--003", "해당 주문을 찾을 수 없습니다"),
    ORDER_EXECUTED(HttpStatus.BAD_REQUEST, "ORDER-004", "이미 체결된 주문입니다"),


    // 주식 관련 에러 코드
    STOCK_NOT_FOUND(HttpStatus.BAD_REQUEST, "STOCK-001", "해당 주식을 찾을 수 없습니다"),

    // 포토폴리오 관련 에러 코드
    PORTFOLIO_NOT_FOUND(HttpStatus.BAD_REQUEST, "PORT-001", "해당 유저의 포토폴리오를 찾을 수 없습니다");



    private final HttpStatus status;
    private final String code; // 프런트 분기용
    private final String message;
}
