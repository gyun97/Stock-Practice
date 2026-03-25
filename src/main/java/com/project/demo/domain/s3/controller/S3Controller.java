package com.project.demo.domain.s3.controller;

import com.project.demo.common.response.ApiResponse;
import com.project.demo.common.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/s3")
@RequiredArgsConstructor
public class S3Controller {

    private final S3Service s3Service;

    /**
     * 클라이언트가 S3에 직접 파일을 업로드하기 위한 Presigned URL 발급
     * @param directory 업로드할 디렉토리 (예: profiles, logos)
     * @param originalFilename 원본 파일명
     * @return [S3에 저장될 Key, Presigned URL]
     */
    @GetMapping("/presigned-url")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
            @RequestParam(defaultValue = "profiles") String directory,
            @RequestParam String originalFilename) {
        
        // 확장자 추출
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        
        // UUID를 사용하여 고유한 파일명 생성 (한글 파일명 문제 등 방지)
        String s3Key = directory + "/" + UUID.randomUUID().toString() + extension;
        
        String presignedUrl = s3Service.getPresignedUploadUrl(s3Key);
        
        return ResponseEntity.ok(ApiResponse.requestSuccess(new PresignedUrlResponse(s3Key, presignedUrl)));
    }

    public record PresignedUrlResponse(String s3Key, String presignedUrl) {}
}
