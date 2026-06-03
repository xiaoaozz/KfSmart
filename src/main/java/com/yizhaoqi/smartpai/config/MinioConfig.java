package com.yizhaoqi.smartpai.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfig {

    private static final Logger logger = LoggerFactory.getLogger(MinioConfig.class);

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.accessKey}")
    private String accessKey;

    @Value("${minio.secretKey}")
    private String secretKey;

    @Value("${minio.publicUrl}")
    private String publicUrl;

    @Value("${minio.bucketName}")
    private String bucketName;


    @Bean
    public MinioClient minioClient() {
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(endpoint)
                    .credentials(accessKey, secretKey)
                    .build();

            // 确保 bucket 存在,如果不存在则创建
            boolean bucketExists = client.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );

            if (!bucketExists) {
                logger.info("Bucket '{}' 不存在,正在创建...", bucketName);
                client.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucketName)
                                .build()
                );
                logger.info("Bucket '{}' 创建成功", bucketName);
            } else {
                logger.info("Bucket '{}' 已存在", bucketName);
            }

            return client;
        } catch (Exception e) {
            logger.error("初始化 MinioClient 失败: {}", e.getMessage(), e);
            throw new RuntimeException("MinioClient 初始化失败", e);
        }
    }

    @Bean
    public String minioPublicUrl() {
        return publicUrl;
    }
}
