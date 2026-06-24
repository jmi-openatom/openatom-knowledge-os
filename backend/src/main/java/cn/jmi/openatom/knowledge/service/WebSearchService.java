package cn.jmi.openatom.knowledge.service;

import cn.jmi.openatom.knowledge.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchService {

  private final AppProperties properties;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public record WebResult(String title, String url, String snippet, String source) {}

  /**
   * Search the web using the configured provider (default: Tavily).
   * Returns empty list if web search is disabled, not configured, or fails.
   */
  public List<WebResult> search(String query) {
    AppProperties.WebSearch config = properties.webSearch();
    if (config == null || !config.enabled() || config.apiKey() == null || config.apiKey().isBlank()) {
      return List.of();
    }
    String provider = config.provider() == null ? "tavily" : config.provider().toLowerCase();
    int maxResults = config.maxResults() <= 0 ? 3 : config.maxResults();
    try {
      return switch (provider) {
        case "tavily" -> searchTavily(query, config.apiKey(), maxResults);
        default -> searchTavily(query, config.apiKey(), maxResults);
      };
    } catch (Exception e) {
      log.warn("Web search failed for query [{}]: {}", query, e.getMessage());
      return List.of();
    }
  }

  /**
   * Check if web search is enabled and configured.
   */
  public boolean isEnabled() {
    AppProperties.WebSearch config = properties.webSearch();
    return config != null && config.enabled()
        && config.apiKey() != null && !config.apiKey().isBlank();
  }

  private List<WebResult> searchTavily(String query, String apiKey, int maxResults) throws Exception {
    Map<String, Object> body = Map.of(
        "api_key", apiKey,
        "query", query,
        "max_results", maxResults,
        "search_depth", "basic");
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.tavily.com/search"))
        .timeout(Duration.ofSeconds(15))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
        .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 300) {
      log.warn("Tavily search failed ({}): {}", response.statusCode(),
          response.body().substring(0, Math.min(response.body().length(), 200)));
      return List.of();
    }
    JsonNode root = objectMapper.readTree(response.body());
    List<WebResult> results = new ArrayList<>();
    JsonNode resultsArray = root.path("results");
    if (resultsArray.isArray()) {
      for (JsonNode item : resultsArray) {
        String title = item.path("title").asText("");
        String url = item.path("url").asText("");
        String content = item.path("content").asText("");
        if (!title.isBlank() || !content.isBlank()) {
          results.add(new WebResult(
              title.isBlank() ? url : title,
              url,
              content.length() > 800 ? content.substring(0, 800) : content,
              "网络搜索"));
        }
      }
    }
    log.info("Tavily returned {} results for [{}]", results.size(), query);
    return results;
  }
}
