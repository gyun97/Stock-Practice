package com.project.demo.common.response;

import com.project.demo.common.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ApiResponse<T> {
    private int statusCode;
    private String message;
    private String code; // 프론트 분기용
    private T data;

    // 상태 코드, 메시지, 데이터를 관리(성공시)
    private ApiResponse(int statusCode, String message, T data) {
        this.statusCode = statusCode;
        this.message = message;
        this.data = data;
    }

    // 200 응답 생성
    public static <T> ApiResponse<T> requestSuccess(T data) {
        return new ApiResponse<>(HttpStatus.OK.value(), "요청이 정상처리되었습니다.", data);
    }

    // 201 응답 생성
    public static <T> ApiResponse<T> createdSuccess(T data) {
        return new ApiResponse<>(HttpStatus.CREATED.value(), "리소스가 정상적으로 등록되었습니다.", data);
    }

}
