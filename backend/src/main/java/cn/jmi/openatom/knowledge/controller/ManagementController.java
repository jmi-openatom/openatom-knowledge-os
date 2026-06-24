package cn.jmi.openatom.knowledge.controller;

import cn.jmi.openatom.knowledge.service.ManagementProxyService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Proxies activity, notification and form management endpoints to the external
 * openatom-system backend on behalf of the authenticated admin. The proxy authenticates with a
 * service account (configured via app.management.username/password) and forwards requests using a
 * cached Sa-Token. Excel export endpoints stream binary payloads through unchanged. All endpoints
 * inherit the global admin-only security rule in {@code SecurityConfig}.
 */
@RestController
@RequiredArgsConstructor
public class ManagementController {

  private static final String PREFIX = "/api/management/";

  private final ManagementProxyService proxyService;

  @RequestMapping("/api/management/activities/**")
  public ResponseEntity<Object> activities(
      HttpServletRequest request,
      @RequestBody(required = false) Object body) {
    return proxyService.proxy(
        HttpMethod.valueOf(request.getMethod()),
        extractExternalPath(request, "activities"),
        request.getQueryString(),
        body);
  }

  @RequestMapping("/api/management/notifications/**")
  public ResponseEntity<Object> notifications(
      HttpServletRequest request,
      @RequestBody(required = false) Object body) {
    return proxyService.proxy(
        HttpMethod.valueOf(request.getMethod()),
        extractExternalPath(request, "notifications"),
        request.getQueryString(),
        body);
  }

  @RequestMapping("/api/management/site-forms/**")
  public ResponseEntity<Object> siteForms(
      HttpServletRequest request,
      @RequestBody(required = false) Object body) {
    String path = extractExternalPath(request, "site-forms");
    // Excel export endpoints return binary xlsx — stream bytes through unchanged.
    if (path.endsWith("/submissions/export") && HttpMethod.GET.matches(request.getMethod())) {
      ResponseEntity<byte[]> binary = proxyService.proxyBinary(
          HttpMethod.GET, path, request.getQueryString());
      return ResponseEntity.status(binary.getStatusCode())
          .contentType(binary.getHeaders().getContentType() != null
              ? binary.getHeaders().getContentType()
              : MediaType.parseMediaType(
                  "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
          .header(HttpHeaders.CONTENT_DISPOSITION,
              binary.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION) != null
                  ? binary.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                  : "attachment; filename=form-submissions.xlsx")
          .body(binary.getBody());
    }
    return proxyService.proxy(
        HttpMethod.valueOf(request.getMethod()), path, request.getQueryString(), body);
  }

  @RequestMapping("/api/management/clubs/**")
  public ResponseEntity<Object> clubs(
      HttpServletRequest request,
      @RequestBody(required = false) Object body) {
    return proxyService.proxy(
        HttpMethod.valueOf(request.getMethod()),
        extractExternalPath(request, "clubs"),
        request.getQueryString(),
        body);
  }

  @RequestMapping("/api/management/forms/**")
  public ResponseEntity<Object> forms(
      HttpServletRequest request,
      @RequestBody(required = false) Object body) {
    // Maps internal /forms/** to external /site/forms/** (public submission endpoint).
    String path = "site/" + extractExternalPath(request, "forms");
    return proxyService.proxy(
        HttpMethod.valueOf(request.getMethod()), path, request.getQueryString(), body);
  }

  private String extractExternalPath(HttpServletRequest request, String resource) {
    String uri = request.getRequestURI();
    String contextPath = request.getContextPath();
    String base = contextPath + PREFIX + resource;
    String rest = uri.startsWith(base) ? uri.substring(base.length()) : "";
    // strip leading slash so the path is relative (e.g. "activities/123")
    if (rest.startsWith("/")) rest = rest.substring(1);
    return rest.isEmpty() ? resource : resource + "/" + rest;
  }
}
