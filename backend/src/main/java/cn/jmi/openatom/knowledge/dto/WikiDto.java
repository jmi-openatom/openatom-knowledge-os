package cn.jmi.openatom.knowledge.dto;

import cn.jmi.openatom.knowledge.model.WikiPage;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record WikiDto(
    Long id,
    Long parentId,
    @NotBlank String title,
    String type,
    String content,
    int sortOrder,
    List<WikiDto> children) {
  public static WikiDto from(WikiPage page, List<WikiDto> children) {
    return new WikiDto(
        page.getId(), page.getParentId(), page.getTitle(), page.getType(),
        page.getContent(), page.getSortOrder(), children);
  }
}
