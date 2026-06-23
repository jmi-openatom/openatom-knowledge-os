package cn.jmi.openatom.knowledge.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "knowledge_file")
public class KnowledgeFile extends BaseEntity {
  public enum Status { UPLOADING, PARSING, READY, FAILED, DELETED }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 500)
  private String name;

  @Column(nullable = false, length = 32)
  private String extension;

  @Column(nullable = false, length = 150)
  private String contentType;

  @Column(nullable = false)
  private long size;

  @Column(nullable = false, unique = true, length = 700)
  private String objectKey;

  @Column(length = 1000)
  private String tagsCsv = "";

  @Column(columnDefinition = "LONGTEXT")
  private String extractedText;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private Status status = Status.UPLOADING;

  @Column(nullable = false, length = 100)
  private String createdBySubject;

  @Column(nullable = false, length = 100)
  private String createdByName;

  @Column(length = 1000)
  private String failureMessage;

  public List<String> tags() {
    if (tagsCsv == null || tagsCsv.isBlank()) return List.of();
    return Arrays.stream(tagsCsv.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
  }

  public void tags(List<String> tags) {
    tagsCsv = String.join(",", tags == null ? new ArrayList<>() : tags);
  }
}
