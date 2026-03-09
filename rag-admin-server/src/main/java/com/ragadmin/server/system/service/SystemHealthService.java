package com.ragadmin.server.system.service;

import com.ragadmin.server.infra.storage.MinioProperties;
import com.ragadmin.server.system.dto.DependencyHealthResponse;
import com.ragadmin.server.system.dto.HealthCheckResponse;
import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SystemHealthService {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final MinioProperties minioProperties;

    public SystemHealthService(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate stringRedisTemplate,
            MinioProperties minioProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.minioProperties = minioProperties;
    }

    public HealthCheckResponse check() {
        DependencyHealthResponse postgres = checkPostgres();
        DependencyHealthResponse redis = checkRedis();
        DependencyHealthResponse minio = checkMinio();
        String status = isUp(postgres) && isUp(redis) && isUp(minio) ? "UP" : "DEGRADED";
        return new HealthCheckResponse(status, postgres, redis, minio);
    }

    private DependencyHealthResponse checkPostgres() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            if (result != null && result == 1) {
                return new DependencyHealthResponse("UP", "PostgreSQL 连通正常");
            }
            return new DependencyHealthResponse("DOWN", "PostgreSQL 响应异常");
        } catch (DataAccessException ex) {
            return new DependencyHealthResponse("DOWN", buildMessage("PostgreSQL 检查失败", ex));
        }
    }

    private DependencyHealthResponse checkRedis() {
        if (stringRedisTemplate.getConnectionFactory() == null) {
            return new DependencyHealthResponse("DOWN", "Redis 连接工厂未初始化");
        }
        try (RedisConnection connection = stringRedisTemplate.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            if ("PONG".equalsIgnoreCase(pong)) {
                return new DependencyHealthResponse("UP", "Redis 连通正常");
            }
            return new DependencyHealthResponse("DOWN", "Redis 响应异常");
        } catch (Exception ex) {
            return new DependencyHealthResponse("DOWN", buildMessage("Redis 检查失败", ex));
        }
    }

    private DependencyHealthResponse checkMinio() {
        if (!minioProperties.isConfigured()) {
            return new DependencyHealthResponse("UNKNOWN", "MinIO 未完成本地配置");
        }
        try {
            MinioClient client = MinioClient.builder()
                    .endpoint(minioProperties.getBaseUrl())
                    .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
                    .build();
            boolean exists = client.bucketExists(BucketExistsArgs.builder()
                    .bucket(minioProperties.getBucketName())
                    .build());
            if (exists) {
                return new DependencyHealthResponse("UP", "MinIO 连通正常");
            }
            return new DependencyHealthResponse("DOWN", "MinIO Bucket 不存在");
        } catch (Exception ex) {
            return new DependencyHealthResponse("DOWN", buildMessage("MinIO 检查失败", ex));
        }
    }

    private boolean isUp(DependencyHealthResponse response) {
        return "UP".equals(response.status());
    }

    private String buildMessage(String prefix, Exception ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            return prefix;
        }
        return prefix + ": " + message;
    }
}
