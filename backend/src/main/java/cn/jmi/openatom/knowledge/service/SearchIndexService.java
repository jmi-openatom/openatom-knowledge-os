package cn.jmi.openatom.knowledge.service;

import cn.jmi.openatom.knowledge.config.AppProperties;
import cn.jmi.openatom.knowledge.dto.RagDtos;
import cn.jmi.openatom.knowledge.model.KnowledgeFile;
import cn.jmi.openatom.knowledge.repository.KnowledgeFileRepository;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchIndexService {
  private final AppProperties properties;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final KnowledgeFileRepository fileRepository;

  public void index(KnowledgeFile file) {
    if (properties.search().endpoint() == null || properties.search().endpoint().isBlank()) return;
    try {
      Map<String, Object> document = new LinkedHashMap<>();
      document.put("id", file.getId());
      document.put("title", file.getName());
      document.put("content", file.getExtractedText());
      document.put("tags", file.tags());
      document.put("resourceType", "file");
      document.put("updatedAt", file.getUpdatedAt());
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(properties.search().endpoint() + "/" + properties.search().index() + "/_doc/" + file.getId()))
          .timeout(Duration.ofSeconds(8))
          .header("Content-Type", "application/json")
          .PUT(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(document)))
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) log.warn("Elasticsearch index failed: {}", response.body());
    } catch (Exception exception) {
      log.warn("Elasticsearch unavailable; file remains queryable through database fallback: {}", exception.getMessage());
    }
  }

  public void delete(Long id) {
    try {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(properties.search().endpoint() + "/" + properties.search().index() + "/_doc/" + id))
          .timeout(Duration.ofSeconds(5))
          .DELETE()
          .build();
      httpClient.send(request, HttpResponse.BodyHandlers.discarding());
    } catch (Exception exception) {
      log.debug("Elasticsearch delete skipped: {}", exception.getMessage());
    }
  }

  public List<RagDtos.SearchResult> search(String query) {
    List<RagDtos.SearchResult> elastic = searchElasticsearch(query);
    if (!elastic.isEmpty()) return elastic;
    java.util.LinkedHashSet<String> termSet = new java.util.LinkedHashSet<>(java.util.Arrays.stream(query
            .replaceAll("[，。！？、,.!?;；:：()（）\\[\\]{}]", " ")
            .split("\\s+"))
        .map(String::trim)
        .filter(term -> term.length() >= 2)
        .toList());
    java.util.regex.Matcher latinMatcher = java.util.regex.Pattern.compile("[A-Za-z0-9_-]{2,}").matcher(query);
    while (latinMatcher.find()) termSet.add(latinMatcher.group());
    String chineseKeywords = query.replaceAll("如何|怎么|什么|哪些|应该|可以|项目|请问|一下|相关", " ");
    java.util.Arrays.stream(chineseKeywords.split("\\s+"))
        .map(String::trim)
        .filter(term -> term.length() >= 2)
        .forEach(termSet::add);
    List<String> terms = List.copyOf(termSet);
    final List<String> finalTerms = terms.isEmpty() ? List.of(query) : terms;
    Map<Long, KnowledgeFile> matches = new LinkedHashMap<>();
    for (String term : finalTerms) {
      List<KnowledgeFile> found = fileRepository.findTop20ByStatusAndExtractedTextContainingIgnoreCaseOrderByUpdatedAtDesc(
          KnowledgeFile.Status.READY, term);
      found.forEach(file -> matches.putIfAbsent(file.getId(), file));
    }
    // Fallback: if no matches, try 2-gram sliding window on the query
    if (matches.isEmpty()) {
      String cleaned = query.replaceAll("[^\\u4e00-\\u9fa5A-Za-z0-9]", "");
      for (int i = 0; i + 2 <= cleaned.length(); i++) {
        String bigram = cleaned.substring(i, i + 2);
        fileRepository.findTop20ByStatusAndExtractedTextContainingIgnoreCaseOrderByUpdatedAtDesc(
            KnowledgeFile.Status.READY, bigram).forEach(file -> matches.putIfAbsent(file.getId(), file));
      }
    }
    return matches.values().stream()
        .map(file -> new RagDtos.SearchResult(
            file.getId(),
            file.getName(),
            excerpt(file.getExtractedText(), finalTerms.get(0)),
            "file",
            80,
            file.tags()))
        .toList();
  }

  private List<RagDtos.SearchResult> searchElasticsearch(String query) {
    if (properties.search().endpoint() == null || properties.search().endpoint().isBlank()) return List.of();
    try {
      Map<String, Object> body = Map.of(
          "size", 12,
          "query", Map.of("multi_match", Map.of(
              "query", query,
              "fields", List.of("title^3", "content", "tags^2"),
              "fuzziness", "AUTO")),
          "highlight", Map.of("fields", Map.of("content", Map.of(), "title", Map.of())));
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(properties.search().endpoint() + "/" + properties.search().index() + "/_search"))
          .timeout(Duration.ofSeconds(5))
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();
      HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) return List.of();
      JsonNode hits = objectMapper.readTree(response.body()).path("hits").path("hits");
      List<RagDtos.SearchResult> results = new ArrayList<>();
      for (JsonNode hit : hits) {
        JsonNode source = hit.path("_source");
        String highlight = hit.path("highlight").path("content").isArray()
            ? hit.path("highlight").path("content").get(0).asText()
            : excerpt(source.path("content").asText(), query);
        List<String> tags = new ArrayList<>();
        source.path("tags").forEach(value -> tags.add(value.asText()));
        results.add(new RagDtos.SearchResult(
            source.path("id").asLong(),
            source.path("title").asText(),
            highlight,
            source.path("resourceType").asText("file"),
            Math.min(100, (int) Math.round(hit.path("_score").asDouble() * 10)),
            tags));
      }
      return results;
    } catch (Exception exception) {
      log.debug("Elasticsearch search fallback: {}", exception.getMessage());
      return List.of();
    }
  }

  private String excerpt(String text, String query) {
    if (text == null || text.isBlank()) return "";
    int index = text.toLowerCase().indexOf(query.toLowerCase());
    int start = Math.max(0, index < 0 ? 0 : index - 80);
    int end = Math.min(text.length(), start + 260);
    return (start > 0 ? "…" : "") + text.substring(start, end).replaceAll("\\s+", " ") + (end < text.length() ? "…" : "");
  }
}
