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
@Table(name = "audit_log")
public class AuditLog extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String actorSubject;

  @Column(nullable = false, length = 100)
  private String actorName;

  @Column(nullable = false, length = 100)
  private String action;

  @Column(nullable = false, length = 100)
  private String resourceType;

  @Column(length = 100)
  private String resourceId;

  @Column(length = 1000)
  private String detail;
}
