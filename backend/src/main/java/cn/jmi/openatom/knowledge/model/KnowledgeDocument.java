package cn.jmi.openatom.knowledge.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "knowledge_document")
public class KnowledgeDocument extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 300)
  private String title;

  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String content = "";

  @Column(nullable = false)
  private int version = 1;

  @Column(nullable = false, length = 100)
  private String updatedBySubject;

  @Column(nullable = false, length = 100)
  private String updatedByName;

  private LocalDateTime deletedAt;
}
