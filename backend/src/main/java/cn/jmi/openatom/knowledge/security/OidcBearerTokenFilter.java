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
      setAuthentication(toUser(result));
      return true;
    } catch (Exception exception) {
      log.warn("OIDC token introspection failed: {}", exception.getMessage());
      return false;
    }
  }

  private CurrentUser toUser(Map<String, Object> claims) {
    List<String> roles = strings(claims.get("roles"));
    if (roles.isEmpty()) roles = strings(claims.get("role"));
    if (roles.isEmpty()) roles = strings(claims.get("authorities"));
    String role = mapRole(roles);
    return new CurrentUser(
        String.valueOf(claims.getOrDefault("sub", "unknown")),
        String.valueOf(claims.getOrDefault("name", claims.getOrDefault("username", "OpenAtom 用户"))),
        String.valueOf(claims.getOrDefault("email", "")),
        role,
        roles,
        permissionsForRole(role));
  }

  private String mapRole(List<String> roles) {
    if (roles.contains("admin") || roles.contains("super_admin")) return "admin";
    if (roles.stream().anyMatch(item -> item.equals("leader")
        || item.equals("operations_lead")
        || item.equals("department_head")
        || item.equals("club_admin"))) return "leader";
    if (roles.contains("guest")) return "guest";
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
