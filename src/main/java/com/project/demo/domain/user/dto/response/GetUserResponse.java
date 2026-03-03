package com.project.demo.domain.user.dto.response;

import com.project.demo.domain.user.entity.User;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AllArgsConstructor
@NoArgsConstructor
public class GetUserResponse {

    private Long userId;
    private String name;
    private String email;
    private double balance;
    private String profileImage;

    public GetUserResponse(Long userId, String name, String email, double balance) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.balance = balance;
    }

    public static GetUserResponse of(User user, double balance) {
        return new GetUserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                balance,
                user.getProfileImage());
    }

}
