package cn.jmi.openatom.knowledge.dto;

import cn.jmi.openatom.knowledge.model.KnowledgeDocument;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

public record DocumentDto(
    Long id,
    @NotBlank String title,
    String content,
    int version,
    LocalDateTime updatedAt,
    String updatedBy) {
  public static DocumentDto from(KnowledgeDocument document) {
    return new DocumentDto(
        document.getId(),
        document.getTitle(),
        document.getContent(),
        document.getVersion(),
        document.getUpdatedAt(),
        document.getUpdatedByName());
  }
}
