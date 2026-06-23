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
@Table(name = "chat_message")
public class ChatMessage extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private Long conversationId;

  @Column(nullable = false)
  private String question;

  @Column(nullable = false, columnDefinition = "LONGTEXT")
  private String answer;

  @Column(columnDefinition = "LONGTEXT")
  private String sources;
}
