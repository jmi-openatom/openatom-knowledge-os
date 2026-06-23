package cn.jmi.openatom.knowledge.service;

import cn.jmi.openatom.knowledge.config.AppProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "minio")
public class MinioStorageService implements StorageService {
  private final AppProperties properties;
  private final MinioClient client;

  public MinioStorageService(AppProperties properties) {
    this.properties = properties;
    this.client = MinioClient.builder()
        .endpoint(properties.storage().endpoint())
        .credentials(properties.storage().accessKey(), properties.storage().secretKey())
        .build();
    ensureBucket();
  }

  @Override
  public void put(String objectKey, InputStream input, long size, String contentType) {
    try {
      client.putObject(PutObjectArgs.builder()
          .bucket(properties.storage().bucket())
          .object(objectKey)
          .stream(input, size, -1)
          .contentType(contentType)
          .build());
    } catch (Exception exception) {
      throw new IllegalStateException("MinIO 文件上传失败", exception);
    }
  }

  @Override
  public InputStream get(String objectKey) {
    try {
      return client.getObject(GetObjectArgs.builder()
          .bucket(properties.storage().bucket())
          .object(objectKey)
          .build());
    } catch (Exception exception) {
      throw new IllegalArgumentException("MinIO 文件不存在", exception);
    }
  }

  @Override
  public void delete(String objectKey) {
    try {
      client.removeObject(RemoveObjectArgs.builder()
          .bucket(properties.storage().bucket())
          .object(objectKey)
          .build());
    } catch (Exception exception) {
      throw new IllegalStateException("MinIO 文件删除失败", exception);
    }
  }

  private void ensureBucket() {
    try {
      boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(properties.storage().bucket()).build());
      if (!exists) client.makeBucket(MakeBucketArgs.builder().bucket(properties.storage().bucket()).build());
    } catch (Exception exception) {
      throw new IllegalStateException("无法初始化 MinIO Bucket", exception);
    }
  }
}
