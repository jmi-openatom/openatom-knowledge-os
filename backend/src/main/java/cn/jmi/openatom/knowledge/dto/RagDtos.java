package cn.jmi.openatom.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public final class RagDtos {
  private RagDtos() {}

  public record ChatRequest(@NotBlank String question, String scope) {}

  public record Source(
      Long id,
      String title,
      String fileName,
      String excerpt,
      int score,
      String type,
      String path,
      String createdBy,
      String updatedAt,
      long size,
      String extension) {}

  public record ChatResponse(String answer, List<Source> sources) {}

  public record FormatRequest(@NotBlank String markdown) {}

  public record FormatResponse(String markdown) {}

  public record OrganizeRequest(@NotBlank String question, @NotBlank String answer) {}

  public record OrganizeResponse(String title, String markdown) {}

  public record AiStatus(
      String provider,
      String model,
      boolean configured,
      String baseUrl) {}

  public record SearchResult(
      Long id,
      String title,
      String highlight,
      String resourceType,
      int score,
      List<String> tags) {}
}
