package cn.jmi.openatom.knowledge.service;

import cn.jmi.openatom.knowledge.config.AppProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

/**
 * Proxies management requests to the external openatom-system backend. Authenticates with a
 * service account (POST /auth/login) to obtain a Sa-Token, caches it, and forwards requests
 * using the {@code jmiopenatom} header. When {@code app.management.mock} is enabled (local dev),
 * returns deterministic in-memory data so the UI is usable without the external system.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManagementProxyService {

  private final AppProperties properties;
  private final RestClient restClient;

  // Cached service-account Sa-Token and its expiry epoch millis.
  private volatile String cachedToken;
  private volatile long tokenExpiresAt;
  private static final long TOKEN_REFRESH_MARGIN_MS = 60_000L;

  private final List<Map<String, Object>> mockActivities = new ArrayList<>(List.of(
      sampleActivity(1L, "开源沙龙第 8 期", "draft", "2026-07-01T14:00:00+08:00", "图书馆报告厅", 10),
      sampleActivity(2L, "DeepSeek 技术分享会", "published", "2026-06-28T19:00:00+08:00", "教学楼 A203", 5),
      sampleActivity(3L, "社团招新宣讲", "closed", "2026-06-15T18:30:00+08:00", "大学生活动中心", 0)));
  private final List<Map<String, Object>> mockNotifications = new ArrayList<>(List.of(
      sampleNotification(1L, "活动通知", "请准时参加开源沙龙", "activity", "2026-06-24T10:00:00+08:00"),
      sampleNotification(2L, "系统公告", "系统将于今晚 23:00 维护", "system", "2026-06-23T16:00:00+08:00")));
  private final AtomicLong mockActivityId = new AtomicLong(100);
  private final AtomicLong mockNotificationId = new AtomicLong(100);
  private final AtomicLong mockFormId = new AtomicLong(100);
  private final AtomicLong mockSubmissionId = new AtomicLong(100);

  private final List<Map<String, Object>> mockForms = new ArrayList<>(List.of(
      sampleForm(10L, "春季招新报名表", "published", List.of(
          Map.of("name", "姓名", "type", "text", "required", true),
          Map.of("name", "学号", "type", "text", "required", true),
          Map.of("name", "意向部门", "type", "select", "required", true,
              "options", List.of("技术部", "宣传部", "组织部")),
          Map.of("name", "自我介绍", "type", "textarea", "required", false))),
      sampleForm(11L, "活动反馈问卷", "closed", List.of(
          Map.of("name", "满意度", "type", "select", "required", true,
              "options", List.of("非常满意", "满意", "一般", "不满意")),
          Map.of("name", "建议", "type", "textarea", "required", false)))));
  private final List<Map<String, Object>> mockSubmissions = new ArrayList<>(List.of(
      sampleSubmission(1001L, 10L, "张三", Map.of("姓名", "张三", "学号", "20260001", "意向部门", "技术部", "自我介绍", "热爱编程")),
      sampleSubmission(1002L, 10L, "李四", Map.of("姓名", "李四", "学号", "20260002", "意向部门", "宣传部", "自我介绍", "擅长设计")),
      sampleSubmission(1003L, 11L, "王五", Map.of("满意度", "非常满意", "建议", "希望多办线下活动"))));

  /**
   * Forward a request to the external management system using a cached service-account token.
   *
   * @param method       HTTP method
   * @param externalPath path relative to the management base url (e.g. "activities", "notifications/admin")
   * @param queryString  raw query string (may be null)
   * @param body         parsed JSON body (may be null)
   */
  public ResponseEntity<Object> proxy(
      HttpMethod method, String externalPath, String queryString, Object body) {
    AppProperties.Management mgmt = properties.management();
    if (mgmt != null && mgmt.mock()) {
      return ResponseEntity.ok(mock(method, externalPath, queryString, body));
    }
    String baseUrl = mgmt == null || mgmt.baseUrl() == null || mgmt.baseUrl().isBlank()
        ? properties.oidc().issuer() : mgmt.baseUrl();
    String url = baseUrl.replaceAll("/+$", "") + "/" + externalPath
        + (queryString != null && !queryString.isBlank() ? "?" + queryString : "");
    URI uri = URI.create(url);
    String token = ensureToken(mgmt, baseUrl);
    if (token == null) {
      return ResponseEntity.status(401).body(Map.of(
          "code", 401, "message", "无法登录管理系统，请检查服务账号配置", "data", ""));
    }
    ResponseEntity<Object> response = doProxy(method, uri, token, body);
    // Token might have expired mid-flight — retry once with a fresh login.
    if (response.getStatusCode().value() == 401) {
      log.info("Management proxy got 401, refreshing service token and retrying once");
      invalidateToken();
      token = ensureToken(mgmt, baseUrl);
      if (token != null) response = doProxy(method, uri, token, body);
    }
    return response;
  }

  private ResponseEntity<Object> doProxy(HttpMethod method, URI uri, String token, Object body) {
    try {
      RestClient.RequestBodySpec spec = restClient.method(method)
          .uri(uri)
          .header("jmiopenatom", token)
          .accept(MediaType.APPLICATION_JSON);
      if (body != null && !method.equals(HttpMethod.GET) && !method.equals(HttpMethod.DELETE)) {
        spec.contentType(MediaType.APPLICATION_JSON).body(body);
      }
      return spec.retrieve().toEntity(Object.class);
    } catch (HttpStatusCodeException exception) {
      log.warn("Management proxy {} {} -> {}: {}", method, uri.getPath(),
          exception.getStatusCode(), exception.getResponseBodyAsString());
      Object errorBody = exception.getResponseBodyAs(Object.class);
      return ResponseEntity.status(exception.getStatusCode()).body(errorBody);
    } catch (Exception exception) {
      log.warn("Management proxy {} {} failed: {}", method, uri.getPath(), exception.getMessage());
      return ResponseEntity.status(502).body(Map.of(
          "code", 50000,
          "message", "无法连接管理系统: " + exception.getMessage(),
          "data", ""));
    }
  }

  // ---- binary proxy (for Excel export endpoints) --------------------------

  public ResponseEntity<byte[]> proxyBinary(HttpMethod method, String externalPath, String queryString) {
    AppProperties.Management mgmt = properties.management();
    if (mgmt != null && mgmt.mock()) {
      return ResponseEntity.ok()
          .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
          .header("Content-Disposition", "attachment; filename=form-submissions.xlsx")
          .body(mockExcel());
    }
    String baseUrl = mgmt == null || mgmt.baseUrl() == null || mgmt.baseUrl().isBlank()
        ? properties.oidc().issuer() : mgmt.baseUrl();
    String url = baseUrl.replaceAll("/+$", "") + "/" + externalPath
        + (queryString != null && !queryString.isBlank() ? "?" + queryString : "");
    URI uri = URI.create(url);
    String token = ensureToken(mgmt, baseUrl);
    if (token == null) {
      return ResponseEntity.status(401).body(null);
    }
    try {
      ResponseEntity<byte[]> response = restClient.method(method)
          .uri(uri)
          .header("jmiopenatom", token)
          .accept(MediaType.ALL)
          .retrieve()
          .toEntity(byte[].class);
      if (response.getStatusCode().value() == 401) {
        invalidateToken();
        token = ensureToken(mgmt, baseUrl);
        if (token != null) {
          response = restClient.method(method).uri(uri).header("jmiopenatom", token)
              .accept(MediaType.ALL).retrieve().toEntity(byte[].class);
        }
      }
      return response;
    } catch (HttpStatusCodeException exception) {
      log.warn("Management binary proxy {} {} -> {}: {}", method, uri.getPath(),
          exception.getStatusCode(), exception.getStatusCode());
      return ResponseEntity.status(exception.getStatusCode()).body(null);
    } catch (Exception exception) {
      log.warn("Management binary proxy {} {} failed: {}", method, uri.getPath(), exception.getMessage());
      return ResponseEntity.status(502).body(null);
    }
  }

  // ---- AI context: expose form data for the chat assistant ----------------

  /**
   * Fetch form definitions and recent submissions as Markdown text for the AI chat context.
   * Returns an empty string when no form-related data is available.
   */
  public String fetchFormsContextForAi() {
    ResponseEntity<Object> formsResp = proxy(HttpMethod.GET, "clubs/1/site-forms", null, null);
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> forms = asList(unwrap(formsResp.getBody()));
    if (forms == null || forms.isEmpty()) return "";
    StringBuilder sb = new StringBuilder();
    sb.append("# 表单系统数据\n\n");
    for (Map<String, Object> form : forms) {
      sb.append("## 表单：").append(form.getOrDefault("title", "未命名")).append("\n");
      sb.append("- 状态：").append(form.getOrDefault("status", "未知")).append("\n");
      Object fields = form.get("fields");
      if (fields instanceof List<?> fl && !fl.isEmpty()) {
        sb.append("- 字段：");
        for (Object f : fl) {
          if (f instanceof Map<?, ?> fm) {
            sb.append(fm.get("name")).append("(").append(fm.get("type")).append(") ");
          }
        }
        sb.append("\n");
      }
      Object formId = form.get("id");
      if (formId != null) {
        ResponseEntity<Object> subsResp = proxy(HttpMethod.GET,
            "site-forms/" + formId + "/submissions", "page=1&pageSize=20", null);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> subs = asList(unwrap(subsResp.getBody()));
        if (subs != null && !subs.isEmpty()) {
          sb.append("- 最近 ").append(subs.size()).append(" 条提交：\n");
          for (Map<String, Object> s : subs) {
            Object data = s.get("data");
            if (data instanceof Map<?, ?> dm) {
              sb.append("  - ").append(dm).append("\n");
            } else {
              sb.append("  - ").append(s).append("\n");
            }
          }
        }
      }
      sb.append("\n");
    }
    return sb.toString();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> asList(Object value) {
    if (value instanceof List<?> list) {
      List<Map<String, Object>> result = new ArrayList<>();
      for (Object item : list) {
        if (item instanceof Map<?, ?> m) result.add((Map<String, Object>) m);
      }
      return result;
    }
    return null;
  }

  private Object unwrap(Object proxyBody) {
    if (proxyBody instanceof Map<?, ?> m && m.get("data") != null) {
      Object data = m.get("data");
      if (data instanceof List<?> || data instanceof Map<?, ?>) return data;
    }
    return proxyBody;
  }

  // ---- service-account token management -----------------------------------

  private String ensureToken(AppProperties.Management mgmt, String baseUrl) {
    if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAt) {
      return cachedToken;
    }
    if (mgmt == null || mgmt.username() == null || mgmt.username().isBlank()
        || mgmt.password() == null || mgmt.password().isBlank()) {
      log.warn("Management service account not configured (app.management.username/password missing)");
      return null;
    }
    return login(mgmt, baseUrl);
  }

  @SuppressWarnings("unchecked")
  private synchronized String login(AppProperties.Management mgmt, String baseUrl) {
    // Double-check after acquiring lock — another thread may have logged in already.
    if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAt) {
      return cachedToken;
    }
    try {
      URI loginUri = URI.create(baseUrl.replaceAll("/+$", "") + "/auth/login");
      Map<String, Object> body = Map.of("username", mgmt.username(), "password", mgmt.password());
      Map<String, Object> result = restClient.post()
          .uri(loginUri)
          .contentType(MediaType.APPLICATION_JSON)
          .body(body)
          .retrieve()
          .body(new org.springframework.core.ParameterizedTypeReference<>() {});
      // Response is Result<ResponseLoginVO>: { code, message, data: { accessToken, expiresIn, ... } }
      if (result == null) {
        log.warn("Management login returned empty response");
        return null;
      }
      Object code = result.get("code");
      if (code instanceof Number n && n.intValue() != 0) {
        log.warn("Management login failed (code={}): {}", code, result.get("message"));
        return null;
      }
      Map<String, Object> data = result;
      if (result.get("data") instanceof Map<?, ?> d) {
        data = (Map<String, Object>) d;
      }
      Object tokenRaw = data.get("accessToken");
      if (tokenRaw == null || String.valueOf(tokenRaw).isBlank()) {
        log.warn("Management login succeeded but no accessToken in response: {}", result);
        return null;
      }
      String token = String.valueOf(tokenRaw);
      long expiresIn = 3600L;
      Object expiresInRaw = data.get("expiresIn");
      if (expiresInRaw instanceof Number n) expiresIn = n.longValue();
      cachedToken = token;
      tokenExpiresAt = System.currentTimeMillis() + expiresIn * 1000L - TOKEN_REFRESH_MARGIN_MS;
      log.info("Management service token acquired, expires in {}s", expiresIn);
      return cachedToken;
    } catch (Exception exception) {
      log.warn("Management service login failed: {}", exception.getMessage());
      return null;
    }
  }

  private void invalidateToken() {
    cachedToken = null;
    tokenExpiresAt = 0;
  }

  // ---- mock implementation -------------------------------------------------

  @SuppressWarnings("unchecked")
  private Object mock(HttpMethod method, String path, String queryString, Object body) {
    Map<String, Object> bodyMap = body instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    String[] parts = path.split("/");

    // Activities: /activities and /activities/{id}
    if ("activities".equals(parts[0])) {
      if (method.equals(HttpMethod.GET) && parts.length == 1) return page(mockActivities, queryString);
      if (method.equals(HttpMethod.GET) && parts.length == 2) return ok(findById(mockActivities, parts[1]));
      if (method.equals(HttpMethod.POST) && parts.length == 1) {
        Map<String, Object> created = new LinkedHashMap<>(bodyMap);
        created.put("id", mockActivityId.incrementAndGet());
        created.putIfAbsent("status", "draft");
        mockActivities.add(0, created);
        return ok(created);
      }
      if (method.equals(HttpMethod.PATCH) && parts.length == 2) {
        Map<String, Object> existing = findById(mockActivities, parts[1]);
        if (existing != null) existing.putAll(bodyMap);
        return ok(existing);
      }
      if (method.equals(HttpMethod.DELETE) && parts.length == 2) {
        mockActivities.removeIf(a -> String.valueOf(a.get("id")).equals(parts[1]));
        return ok(null);
      }
    }

    // Notifications: /notifications/admin, /notifications/admin/{id}, /notifications/{id}/read
    if ("notifications".equals(parts[0])) {
      if (method.equals(HttpMethod.GET) && parts.length == 2 && "admin".equals(parts[1]))
        return page(mockNotifications, queryString);
      if (method.equals(HttpMethod.POST) && parts.length == 2 && "admin".equals(parts[1])) {
        Map<String, Object> created = new LinkedHashMap<>(bodyMap);
        created.put("id", mockNotificationId.incrementAndGet());
        created.putIfAbsent("type", "activity");
        created.putIfAbsent("createdAt", LocalDateTime.now().toString());
        mockNotifications.add(0, created);
        return ok(created);
      }
      if (method.equals(HttpMethod.DELETE) && parts.length == 3 && "admin".equals(parts[1])) {
        mockNotifications.removeIf(n -> String.valueOf(n.get("id")).equals(parts[2]));
        return ok(null);
      }
      if (method.equals(HttpMethod.POST) && parts.length == 3 && "read".equals(parts[2])) {
        return ok(null);
      }
      if (method.equals(HttpMethod.GET) && parts.length == 1) return page(mockNotifications, queryString);
    }

    // Site forms: /site-forms/{id}, /site-forms/{id}/submissions, /site-forms/{id}/submissions/export
    if ("site-forms".equals(parts[0]) && parts.length >= 2) {
      String id = parts[1];
      if (method.equals(HttpMethod.GET) && parts.length == 2) return ok(findById(mockForms, id));
      if (method.equals(HttpMethod.GET) && parts.length == 3 && "submissions".equals(parts[2])) {
        List<Map<String, Object>> filtered = mockSubmissions.stream()
            .filter(s -> String.valueOf(s.get("formId")).equals(id)).toList();
        return page(filtered, queryString);
      }
      if (method.equals(HttpMethod.PATCH) && parts.length == 2) {
        Map<String, Object> existing = findById(mockForms, id);
        if (existing != null) existing.putAll(bodyMap);
        return ok(existing);
      }
      if (method.equals(HttpMethod.POST) && parts.length == 3 && "publish".equals(parts[2])) {
        Map<String, Object> existing = findById(mockForms, id);
        if (existing != null) existing.put("status", "published");
        return ok(existing);
      }
      if (method.equals(HttpMethod.POST) && parts.length == 3 && "close".equals(parts[2])) {
        Map<String, Object> existing = findById(mockForms, id);
        if (existing != null) existing.put("status", "closed");
        return ok(existing);
      }
    }

    // Forms list via club: /clubs/{clubId}/site-forms
    if ("clubs".equals(parts[0]) && parts.length == 3 && "site-forms".equals(parts[2])) {
      if (method.equals(HttpMethod.GET)) return ok(mockForms);
      if (method.equals(HttpMethod.POST)) {
        Map<String, Object> created = new LinkedHashMap<>(bodyMap);
        created.put("id", mockFormId.incrementAndGet());
        created.putIfAbsent("status", "draft");
        mockForms.add(0, created);
        return ok(created);
      }
    }

    // Public form submission: /site/forms/{formId}/submissions
    if ("site".equals(parts[0]) && parts.length == 4 && "forms".equals(parts[1]) && "submissions".equals(parts[3])) {
      if (method.equals(HttpMethod.POST)) {
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("id", mockSubmissionId.incrementAndGet());
        created.put("formId", Long.valueOf(parts[2]));
        created.put("data", bodyMap);
        created.put("createdAt", LocalDateTime.now().toString());
        mockSubmissions.add(0, created);
        return ok(created);
      }
    }

    return Map.of("code", 404, "message", "mock 未覆盖: " + method + " /" + path, "data", "");
  }

  private Object page(List<Map<String, Object>> list, String queryString) {
    int page = 1, pageSize = 10;
    if (queryString != null) {
      for (String pair : queryString.split("&")) {
        String[] kv = pair.split("=", 2);
        if (kv.length == 2) {
          if ("page".equals(kv[0])) page = parseInt(kv[1], 1);
          if ("pageSize".equals(kv[0])) pageSize = parseInt(kv[1], 10);
        }
      }
    }
    int from = Math.min((page - 1) * pageSize, list.size());
    int to = Math.min(from + pageSize, list.size());
    return ok(Map.of(
        "list", list.subList(from, to),
        "page", page,
        "pageSize", pageSize,
        "total", list.size()));
  }

  private static int parseInt(String value, int fallback) {
    try { return Integer.parseInt(value); } catch (NumberFormatException e) { return fallback; }
  }

  private Map<String, Object> findById(List<Map<String, Object>> list, String id) {
    return list.stream().filter(x -> String.valueOf(x.get("id")).equals(id)).findFirst().orElse(null);
  }

  private static Object ok(Object data) {
    return Map.of("code", 0, "message", "success", "data", data == null ? "" : data, "traceId", "mock");
  }

  private static Map<String, Object> sampleActivity(
      long id, String title, String status, String at, String location, int points) {
    Map<String, Object> a = new LinkedHashMap<>();
    a.put("id", id);
    a.put("title", title);
    a.put("summary", "示例活动摘要");
    a.put("activityAt", at);
    a.put("location", location);
    a.put("status", status);
    a.put("registrationRequired", true);
    a.put("participationPoints", points);
    a.put("coverUrl", "");
    a.put("createdAt", "2026-06-20T09:00:00+08:00");
    return a;
  }

  private static Map<String, Object> sampleNotification(
      long id, String title, String content, String type, String createdAt) {
    Map<String, Object> n = new LinkedHashMap<>();
    n.put("id", id);
    n.put("title", title);
    n.put("content", content);
    n.put("type", type);
    n.put("isAll", true);
    n.put("createdAt", createdAt);
    return n;
  }

  private static Map<String, Object> sampleForm(long id, String title, String status, List<Object> fields) {
    Map<String, Object> f = new LinkedHashMap<>();
    f.put("id", id);
    f.put("title", title);
    f.put("status", status);
    f.put("fields", fields);
    f.put("createdAt", "2026-06-01T09:00:00+08:00");
    return f;
  }

  private static Map<String, Object> sampleSubmission(long id, long formId, String submitter, Map<String, Object> data) {
    Map<String, Object> s = new LinkedHashMap<>();
    s.put("id", id);
    s.put("formId", formId);
    s.put("submitterName", submitter);
    s.put("data", data);
    s.put("createdAt", "2026-06-20T10:00:00+08:00");
    return s;
  }

  private static byte[] mockExcel() {
    // Minimal valid empty xlsx is complex; return a tiny CSV-like placeholder bytes so the
    // download still produces a file in mock mode. Real xlsx comes from the external system.
    String content = "表单,提交人,提交时间\n春季招新报名表,张三,2026-06-20 10:00\n春季招新报名表,李四,2026-06-20 10:05\n活动反馈问卷,王五,2026-06-20 11:00\n";
    return content.getBytes(StandardCharsets.UTF_8);
  }

  @SuppressWarnings("unused")
  private static String encode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }
}
