package com.ragadmin.server.document.parser;

import com.ragadmin.server.common.exception.BusinessException;
import com.ragadmin.server.infra.storage.support.MinioClientFactory;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class MinioPresignedMineruSourceResolver implements MineruSourceResolver {

    private static final Logger log = LoggerFactory.getLogger(MinioPresignedMineruSourceResolver.class);

    private final MinioClientFactory minioClientFactory;

    public MinioPresignedMineruSourceResolver(MinioClientFactory minioClientFactory) {
        this.minioClientFactory = minioClientFactory;
    }

    @Override
    public String resolve(DocumentParseRequest request) {
        try {
            return minioClientFactory.createClient().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(request.version().getStorageBucket())
                            .object(request.version().getStorageObjectKey())
                            .expiry(30 * 60)
                            .build()
            );
        } catch (Exception ex) {
            log.warn("生成 MinerU 读取地址失败，documentName={}, reason={}", request.document().getDocName(), ex.getMessage());
            throw new BusinessException("MINERU_SOURCE_URL_FAILED", "生成 MinerU 读取地址失败", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
