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
@Table(name = "user_profile")
public class UserProfile extends BaseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 100)
  private String subject;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 200)
  private String email;

  @Column(nullable = false, length = 20)
  private String role = "member";

  @Column(length = 500)
  private String avatar;

  private LocalDateTime lastLoginAt;
}
