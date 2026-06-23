package cn.jmi.openatom.knowledge.service;

import cn.jmi.openatom.knowledge.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.storage", name = "provider", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements StorageService {
  private final AppProperties properties;

  @Override
  public void put(String objectKey, InputStream input, long size, String contentType) {
    try {
      Path target = path(objectKey);
      Files.createDirectories(target.getParent());
      Files.copy(input, target);
    } catch (IOException exception) {
      throw new IllegalStateException("本地文件存储失败", exception);
    }
  }

  @Override
  public InputStream get(String objectKey) {
    try {
      return Files.newInputStream(path(objectKey));
    } catch (IOException exception) {
      throw new IllegalArgumentException("文件不存在", exception);
    }
  }

  @Override
  public void delete(String objectKey) {
    try {
      Files.deleteIfExists(path(objectKey));
    } catch (IOException exception) {
      throw new IllegalStateException("删除本地文件失败", exception);
    }
  }

  private Path path(String objectKey) {
    Path root = Path.of(properties.storage().localRoot()).toAbsolutePath().normalize();
    Path target = root.resolve(objectKey).normalize();
    if (!target.startsWith(root)) throw new IllegalArgumentException("非法文件路径");
    return target;
  }
}
