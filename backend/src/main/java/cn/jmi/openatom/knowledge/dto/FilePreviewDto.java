package cn.jmi.openatom.knowledge.dto;

import cn.jmi.openatom.knowledge.model.KnowledgeFile;

public record FilePreviewDto(
    Long id,
    String name,
    String extension,
    long size,
    String contentType,
    String createdBy,
    String updatedAt,
    String status,
    String extractedText,
    String previewType) {

  public static FilePreviewDto from(KnowledgeFile file) {
    String ext = file.getExtension() == null ? "" : file.getExtension().toUpperCase();
    String previewType = switch (ext) {
      case "MD" -> "markdown";
      case "TXT" -> "text";
      case "PDF" -> "pdf";
      case "DOCX" -> "docx";
      case "XLSX" -> "xlsx";
      case "PPTX" -> "pptx";
      case "PNG", "JPG", "JPEG", "GIF", "WEBP", "SVG" -> "image";
      default -> "text";
    };
    return new FilePreviewDto(
        file.getId(),
        file.getName(),
        file.getExtension(),
        file.getSize(),
        file.getContentType(),
        file.getCreatedByName(),
        String.valueOf(file.getUpdatedAt()),
        String.valueOf(file.getStatus()),
        file.getExtractedText() == null ? "" : file.getExtractedText(),
        previewType);
  }
}
