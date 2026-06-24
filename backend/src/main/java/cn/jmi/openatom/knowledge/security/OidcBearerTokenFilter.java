package cn.jmi.openatom.knowledge.security;

import cn.jmi.openatom.knowledge.config.AppProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@RequiredArgsConstructor
public class OidcBearerTokenFilter extends OncePerRequestFilter {
  private final AppProperties properties;
  private final RestClient restClient;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain) throws ServletException, IOException {
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      boolean authenticated = authenticate(authorization.substring(7));
      if (!authenticated && properties.security().devMode()) {
        authenticateDevUser();
      }
    } else if (properties.security().devMode()) {
      authenticateDevUser();
    }
    filterChain.doFilter(request, response);
  }

  private boolean authenticate(String token) {
    try {
      Map<String, Object> result = restClient.post()
          .uri(properties.oidc().introspectionUri())
          .contentType(MediaType.APPLICATION_FORM_URLENCODED)
          .body("token=" + java.net.URLEncoder.encode(token, java.nio.charset.StandardCharsets.UTF_8))
          .retrieve()
          .body(new org.springframework.core.ParameterizedTypeReference<>() {});
      if (result == null || !Boolean.TRUE.equals(result.get("active"))) return false;
      // OAuth server wraps claims inside a "data" field
      Map<String, Object> claims = result;
      if (result.containsKey("data") && result.get("data") instanceof Map<?, ?> dataMap) {
        claims = objectMapper.convertValue(dataMap, new org.springframework.core.ParameterizedTypeReference<>() {});
      }
      log.info("OIDC introspection claims: {}", objectMapper.writeValueAsString(claims));
      setAuthentication(toUser(claims));
      return true;
    } catch (Exception exception) {
      log.warn("OIDC token introspection failed: {}", exception.getMessage());
      return false;
    }
  }

  private CurrentUser toUser(Map<String, Object> claims) {
    log.info("OIDC claims keys: {}", claims.keySet());
    List<String> roles = strings(claims.get("roles"));
    if (roles.isEmpty()) roles = strings(claims.get("role"));
    if (roles.isEmpty()) roles = strings(claims.get("authorities"));
    List<String> permissions = strings(claims.get("permissions"));
    String role = mapRole(roles, permissions);
    log.info("Mapped roles [{}] permissions [{}] to role [{}]", roles, permissions, role);
    return new CurrentUser(
        String.valueOf(claims.getOrDefault("sub", "unknown")),
        String.valueOf(claims.getOrDefault("name", claims.getOrDefault("username", "OpenAtom 用户"))),
        String.valueOf(claims.getOrDefault("email", "")),
        role,
        roles,
        permissionsForRole(role));
  }

  private String mapRole(List<String> roles, List<String> permissions) {
    // Check roles first
    boolean isAdmin = roles.stream().anyMatch(r -> {
      String lower = r.toLowerCase();
      return lower.equals("admin") || lower.equals("super_admin")
          || lower.equals("administrator") || lower.equals("root")
          || lower.contains("社长") || lower.equals("president")
          || lower.equals("club_admin") || lower.equals("system_admin");
    });
    // If not admin by role, check permissions for admin-level access
    if (!isAdmin && !permissions.isEmpty()) {
      isAdmin = permissions.stream().anyMatch(p -> {
        String lower = p.toLowerCase();
        return lower.contains("admin") || lower.contains("manage")
            || lower.contains("delete") || lower.contains("system");
      });
    }
    if (isAdmin) return "admin";
    boolean isLeader = roles.stream().anyMatch(r -> {
      String lower = r.toLowerCase();
      return lower.equals("leader") || lower.equals("operations_lead")
          || lower.equals("department_head") || lower.equals("manager")
          || lower.contains("部长") || lower.contains("负责人")
          || lower.equals("vice_president") || lower.contains("副社长");
    });
    if (isLeader) return "leader";
    if (roles.stream().anyMatch(r -> r.equalsIgnoreCase("guest") || r.contains("访客"))) return "guest";
    return "member";
  }

  private List<String> permissionsForRole(String role) {
    return switch (role) {
      case "admin" -> List.of("file:read", "file:write", "file:delete", "doc:edit", "ai:chat", "admin:manage");
      case "leader" -> List.of("file:read", "file:write", "file:delete", "doc:edit", "ai:chat");
      case "member" -> List.of("file:read", "doc:edit", "ai:chat");
      default -> List.of("file:read");
    };
  }

  private List<String> strings(Object value) {
    if (value == null) return List.of();
    if (value instanceof List<?> list) return list.stream().map(String::valueOf).toList();
    try {
      return objectMapper.convertValue(value, new TypeReference<>() {});
    } catch (IllegalArgumentException exception) {
      return List.of(String.valueOf(value));
    }
  }

  private void authenticateDevUser() {
    CurrentUser user = new CurrentUser(
        "dev-admin",
        "本地管理员",
        "dev@openatom.local",
        "admin",
        List.of("admin"),
        List.of("file:read", "file:write", "file:delete", "doc:edit", "ai:chat", "admin:manage"));
    setAuthentication(user);
  }

  private void setAuthentication(CurrentUser user) {
    Set<String> names = new LinkedHashSet<>(user.permissions());
    user.roles().forEach(role -> names.add("ROLE_" + role.toUpperCase()));
    var authorities = new ArrayList<SimpleGrantedAuthority>();
    names.forEach(name -> authorities.add(new SimpleGrantedAuthority(name)));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(user, null, authorities));
  }
}
