package cn.jmi.openatom.knowledge.dto;

import cn.jmi.openatom.knowledge.model.KnowledgeFile;
import java.time.LocalDateTime;
import java.util.List;

public record FileDto(
    Long id,
    String name,
    String extension,
    long size,
    List<String> tags,
    KnowledgeFile.Status status,
    String createdBy,
    LocalDateTime updatedAt,
    String failureMessage) {
  public static FileDto from(KnowledgeFile file) {
    return new FileDto(
        file.getId(),
        file.getName(),
        file.getExtension(),
        file.getSize(),
        file.tags(),
        file.getStatus(),
        file.getCreatedByName(),
        file.getUpdatedAt(),
        file.getFailureMessage());
  }
}
