package com.project.demo.domain.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordUpdateRequest {

    @NotBlank(message = "기존 비밀번호를 입력하세요.")
    private String currentPassword; // 현재 비밀번호 확인용

    @NotBlank(message = "새로 변경할 비밀번호를 입력하세요.")
    private String newPassword;  // 새 비밀번호

    @NotBlank(message = "변경한 비밀번호를 한 번 더 입력해주세요.")
    private String checkNewPassword;  // 새 비밀번호 확인
}
