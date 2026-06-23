package cn.jmi.openatom.knowledge.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "document_version")
public class DocumentVersion extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long documentId;

  @Column(nullable = false)
  private int version;

  @Column(nullable = false, length = 300)
  private String title;

  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String content;

  @Column(nullable = false, length = 100)
  private String createdByName;
}
