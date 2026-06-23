package cn.jmi.openatom.knowledge.controller;

import cn.jmi.openatom.knowledge.model.AuditLog;
import cn.jmi.openatom.knowledge.model.UserProfile;
import cn.jmi.openatom.knowledge.repository.AuditLogRepository;
import cn.jmi.openatom.knowledge.repository.UserProfileRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('admin:manage', 'ROLE_ADMIN')")
public class AdminController {
  private final UserProfileRepository userRepository;
  private final AuditLogRepository auditRepository;

  @GetMapping("/members")
  public List<UserProfile> members() {
    return userRepository.findAll(Sort.by("name").ascending());
  }

  @PatchMapping("/members/{id}/role")
  public UserProfile updateRole(@PathVariable Long id, @RequestBody Map<String, String> input) {
    String role = input.get("role");
    if (!List.of("admin", "leader", "member", "guest").contains(role)) {
      throw new IllegalArgumentException("无效角色");
    }
    UserProfile profile = userRepository.findById(id).orElseThrow(() -> new IllegalArgumentException("成员不存在"));
    profile.setRole(role);
    return userRepository.save(profile);
  }

  @GetMapping("/audit-logs")
  public List<AuditLog> auditLogs() {
    return auditRepository.findAll(Sort.by("createdAt").descending());
  }
}
