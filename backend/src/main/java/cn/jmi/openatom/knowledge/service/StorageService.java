package cn.jmi.openatom.knowledge.service;

import java.io.InputStream;

public interface StorageService {
  void put(String objectKey, InputStream input, long size, String contentType);
  InputStream get(String objectKey);
  void delete(String objectKey);
}
