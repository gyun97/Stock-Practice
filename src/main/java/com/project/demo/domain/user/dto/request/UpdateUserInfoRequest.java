package com.project.demo.domain.user.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUserInfoRequest {

    private String newEmail;
    private String newName;
    private String newProfileImage;

    public UpdateUserInfoRequest(String newEmail, String newName) {
        this.newEmail = newEmail;
        this.newName = newName;
    }
}
