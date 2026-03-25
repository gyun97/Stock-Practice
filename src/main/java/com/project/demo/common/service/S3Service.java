package com.project.demo.common.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Presigner s3Presigner;

    @Value("${cloud.aws.s3.bucket}")
    private String bucket;

    /**
     * 업로드를 위한 Presigned URL 생성
     * @param key S3에 저장될 파일 경로/이름
     * @return Presigned URL 문자열
     */
    public String getPresignedUploadUrl(String key) {
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .putObjectRequest(putObjectRequest)
                .build();

        return s3Presigner.presignPutObject(presignRequest).url().toString();
    }

    /**
     * S3 객체의 공개 URL 반환 (CloudFront 사용하지 않을 경우)
     * @param key S3 객체 키
     * @return S3 URL
     */
    public String getS3Url(String key) {
        if (key == null || key.isBlank() || key.startsWith("http")) {
            return key;
        }
        return String.format("https://%s.s3.amazonaws.com/%s", bucket, key);
    }
}
