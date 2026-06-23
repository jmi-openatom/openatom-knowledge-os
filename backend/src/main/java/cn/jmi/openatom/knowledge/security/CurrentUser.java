package cn.jmi.openatom.knowledge.security;

import java.security.Principal;
import java.util.List;

public record CurrentUser(
    String subject,
    String name,
    String email,
    String role,
    List<String> roles,
    List<String> permissions) implements Principal {
  @Override
  public String getName() {
    return subject;
  }
}
