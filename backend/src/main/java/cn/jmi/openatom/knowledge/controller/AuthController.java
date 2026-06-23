package cn.jmi.openatom.knowledge.controller;

import cn.jmi.openatom.knowledge.model.UserProfile;
import cn.jmi.openatom.knowledge.repository.UserProfileRepository;
import cn.jmi.openatom.knowledge.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
  private final UserProfileRepository repository;

  @GetMapping("/me")
  public Map<String, Object> me(@AuthenticationPrincipal CurrentUser user) {
    UserProfile profile = repository.findBySubject(user.subject()).orElseGet(UserProfile::new);
    profile.setSubject(user.subject());
    profile.setName(user.name());
    profile.setEmail(user.email());
    profile.setRole(user.role());
    profile.setLastLoginAt(LocalDateTime.now());
    repository.save(profile);
    return Map.of(
        "sub", user.subject(),
        "name", user.name(),
        "email", user.email(),
        "role", user.role(),
        "roles", user.roles(),
        "permissions", user.permissions());
  }
}
