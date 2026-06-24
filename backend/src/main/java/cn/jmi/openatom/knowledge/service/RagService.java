package cn.jmi.openatom.knowledge.service;

import cn.jmi.openatom.knowledge.config.AppProperties;
import cn.jmi.openatom.knowledge.dto.RagDtos;
import cn.jmi.openatom.knowledge.model.KnowledgeFile;
import cn.jmi.openatom.knowledge.repository.KnowledgeFileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {
  private static final Set<String> LLM_PROVIDERS = Set.of("openai-compatible", "deepseek");
  private static final String DEEPSEEK_DEFAULT_BASE_URL = "https://api.deepseek.com";
  private static final String DEEPSEEK_DEFAULT_MODEL = "deepseek-chat";
  private static final String OPENAI_DEFAULT_BASE_URL = "https://api.openai.com/v1";
  private static final ExecutorService STREAM_EXECUTOR = Executors.newCachedThreadPool();

  private static final String SYSTEM_PROMPT = "你是 OpenAtom 社团知识助手。根据给定资料回答，使用中文，并用 [编号] 标注引用。资料可能来自本地知识库或网络搜索，网络来源请标注链接。如果资料不足以回答问题，明确说明并建议补充哪些资料。请使用标准 Markdown 格式输出：标题后必须有空格（如 ### 标题），列表项前有空行，标记后有空格（如 - 项目），段落之间用空行分隔。";

  private final SearchIndexService searchIndexService;
  private final KnowledgeFileRepository fileRepository;
  private final AppProperties properties;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final WebSearchService webSearchService;
  private final ManagementProxyService managementProxyService;

  private static final Set<String> FORM_KEYWORDS = Set.of(
      "表单", "报名表", "问卷", "填写", "提交表单", "form", "表单系统", "提交记录", "报名情况");

  private boolean isFormRelated(String question) {
    if (question == null) return false;
    String lower = question.toLowerCase();
    return FORM_KEYWORDS.stream().anyMatch(kw -> lower.contains(kw.toLowerCase()));
  }

  public RagDtos.ChatResponse chat(RagDtos.ChatRequest request) {
    List<RagDtos.SearchResult> results = searchIndexService.search(request.question()).stream().limit(6).toList();
    List<RagDtos.Source> sources = results.stream()
        .map(result -> {
          KnowledgeFile file = fileRepository.findById(result.id()).orElse(null);
          return new RagDtos.Source(
              result.id(),
              result.title(),
              file == null ? result.title() : file.getName(),
              result.highlight(),
              result.score(),
              file == null ? "知识文档" : file.getExtension() + " 文档",
              "/资料库/" + (request.scope() == null ? "全部资料" : request.scope()) + "/",
              file == null ? "未知" : file.getCreatedByName(),
              file == null ? "" : String.valueOf(file.getUpdatedAt()),
              file == null ? 0 : file.getSize(),
              file == null ? "" : file.getExtension());
        })
        .toList();
    String answer = generate(request.question(), sources);
    return new RagDtos.ChatResponse(answer, sources);
  }

  public SseEmitter chatStream(RagDtos.ChatRequest request) {
    SseEmitter emitter = new SseEmitter(120_000L);
    List<RagDtos.SearchResult> results = searchIndexService.search(request.question()).stream().limit(6).toList();
    List<RagDtos.Source> sources = results.stream()
        .map(result -> {
          KnowledgeFile file = fileRepository.findById(result.id()).orElse(null);
          return new RagDtos.Source(
              result.id(),
              result.title(),
              file == null ? result.title() : file.getName(),
              result.highlight(),
              result.score(),
              file == null ? "知识文档" : file.getExtension() + " 文档",
              "/资料库/" + (request.scope() == null ? "全部资料" : request.scope()) + "/",
              file == null ? "未知" : file.getCreatedByName(),
              file == null ? "" : String.valueOf(file.getUpdatedAt()),
              file == null ? 0 : file.getSize(),
              file == null ? "" : file.getExtension());
        })
        .toList();

    // Web search: supplement local results when they are insufficient
    List<WebSearchService.WebResult> webResults = List.of();
    if (webSearchService.isEnabled() && sources.size() < 3) {
      log.info("Local results insufficient ({}), supplementing with web search", sources.size());
      webResults = webSearchService.search(request.question());
    }

    // Build combined sources list (local + web)
    List<RagDtos.Source> allSources = new ArrayList<>(sources);
    long webIdCounter = 90000L;
    for (WebSearchService.WebResult wr : webResults) {
      allSources.add(new RagDtos.Source(
          webIdCounter++,
          wr.title(),
          wr.title(),
          wr.snippet(),
          60,
          "网络搜索",
          wr.url(),
          "Web",
          "",
          0,
          "url"));
    }
    final List<RagDtos.Source> finalAllSources = allSources;
    final List<WebSearchService.WebResult> finalWebResults = webResults;

    STREAM_EXECUTOR.submit(() -> {
      try {
        emitter.send(SseEmitter.event().name("sources").data(finalAllSources));
        String provider = properties.ai().provider() == null ? "" : properties.ai().provider().toLowerCase();
        String apiKey = properties.ai().apiKey();
        if (LLM_PROVIDERS.contains(provider) && apiKey != null && !apiKey.isBlank()) {
          streamFromLLM(request.question(), request.history(), finalAllSources, emitter, provider);
        } else {
          streamMockAnswer(request.question(), finalAllSources, emitter);
        }
        emitter.send(SseEmitter.event().name("done").data("[DONE]"));
        emitter.complete();
      } catch (Exception e) {
        log.error("Stream error: {}", e.getMessage());
        try { emitter.send(SseEmitter.event().name("error").data(e.getMessage())); } catch (Exception ignored) {}
        emitter.complete();
      }
    });
    return emitter;
  }

  /**
   * When the question is form-related, fetch live form/submission data from the external
   * management system and return it as an extra context block for the LLM.
   */
  private String extraSystemContext(String question) {
    if (!isFormRelated(question)) return "";
    try {
      String formData = managementProxyService.fetchFormsContextForAi();
      if (formData != null && !formData.isBlank()) {
        return "\n\n# 表单系统实时数据（来自社团管理系统）\n" + formData;
      }
    } catch (Exception e) {
      log.warn("Failed to fetch form context for AI: {}", e.getMessage());
    }
    return "";
  }

  private String buildContext(List<RagDtos.Source> sources) {
    return sources.stream()
        .map(source -> {
          // For web sources (id >= 90000), use snippet directly
          if (source.id() >= 90000L) {
            String snippet = source.excerpt() != null ? source.excerpt() : "";
            return "[" + source.id() + "] " + source.title() + " (网络来源: " + source.path() + ")\n" + snippet;
          }
          KnowledgeFile file = fileRepository.findById(source.id()).orElse(null);
          String content = file != null ? file.getExtractedText() : source.excerpt();
          if (content != null && content.length() > 8000) content = content.substring(0, 8000);
          return "[" + source.id() + "] " + source.title() + "\n" + stripImages(content);
        })
        .collect(java.util.stream.Collectors.joining("\n\n"));
  }

  private void streamFromLLM(String question, List<RagDtos.ChatMessage> history, List<RagDtos.Source> sources, SseEmitter emitter, String provider) throws Exception {
    String baseUrl = resolveBaseUrl(provider);
    String model = resolveModel(provider);
    String context = buildContext(sources);
    String extra = extraSystemContext(question);
    // Build messages: system prompt + conversation history + current question with context
    List<Map<String, Object>> messages = new ArrayList<>();
    messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));
    // Include previous conversation turns (up to 10 messages to limit token usage)
    if (history != null) {
      int start = Math.max(0, history.size() - 10);
      for (RagDtos.ChatMessage msg : history.subList(start, history.size())) {
        if (msg.content() != null && !msg.content().isBlank()) {
          messages.add(Map.of("role", msg.role(), "content", stripImages(msg.content())));
        }
      }
    }
    messages.add(Map.of("role", "user", "content", "问题：" + stripImages(question) + "\n\n资料：\n" + context + extra));
    Map<String, Object> body = Map.of(
        "model", model,
        "temperature", 0.2,
        "stream", true,
        "messages", messages);
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
        .timeout(Duration.ofSeconds(120))
        .header("Authorization", "Bearer " + properties.ai().apiKey())
        .header("Content-Type", "application/json")
        .header("Accept", "text/event-stream")
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
        .build();
    HttpResponse<InputStream> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
    if (response.statusCode() >= 300) {
      String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
      throw new IllegalStateException(describeLlmError(response.statusCode(), errorBody));
    }
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
      String line;
      StringBuilder accumulated = new StringBuilder();
      while ((line = reader.readLine()) != null) {
        if (line.startsWith("data: ")) {
          String data = line.substring(6).trim();
          if ("[DONE]".equals(data)) break;
          try {
            JsonNode node = objectMapper.readTree(data);
            String content = node.path("choices").path(0).path("delta").path("content").asText("");
            if (!content.isEmpty()) {
              accumulated.append(content);
              if (looksLikeLlmError(accumulated.toString())) {
                emitter.send(SseEmitter.event().name("error").data("AI 服务暂不支持图片输入，请去掉图片后重试"));
                return;
              }
              emitter.send(SseEmitter.event().name("token").data(content));
            }
          } catch (Exception ignored) {}
        }
      }
    }
  }

  private void streamMockAnswer(String question, List<RagDtos.Source> sources, SseEmitter emitter) throws Exception {
    String fullAnswer = mockAnswer(question, sources);
    String[] chunks = fullAnswer.split("(?<=\\n|。|，|！|？|；|：|、|\\))");
    for (String chunk : chunks) {
      if (chunk.isBlank()) continue;
      emitter.send(SseEmitter.event().name("token").data(chunk));
      Thread.sleep(30);
    }
  }

  public RagDtos.FormatResponse format(RagDtos.FormatRequest request) {
    String markdown = request.markdown();
    if (markdown == null || markdown.isBlank()) {
      return new RagDtos.FormatResponse("");
    }
    String provider = properties.ai().provider() == null ? "" : properties.ai().provider().toLowerCase();
    String apiKey = properties.ai().apiKey();
    if (!LLM_PROVIDERS.contains(provider) || apiKey == null || apiKey.isBlank()) {
      return new RagDtos.FormatResponse(markdown);
    }
    String baseUrl = resolveBaseUrl(provider);
    String model = resolveModel(provider);
    try {
      String stripped = stripImages(markdown);
      String systemPrompt = "你是 Markdown 排版优化器。对用户给出的 Markdown 文本做排版优化，输出标准、整洁的 Markdown，要求："
          + "1) 标题标记后必须有一个空格（如 ### 标题）；"
          + "2) 列表标记后必须有一个空格（如 - 项目、1. 项目），列表块前后用空行分隔；"
          + "3) 段落之间用空行分隔，删除多余的连续空行（最多保留一个空行）；"
          + "4) 围栏代码块补全语言标记；"
          + "5) 表格格式化为标准 Markdown 表格；"
          + "6) 保留所有 [编号] 引用标记、链接、代码内容和原文语义，不得增删信息或改写措辞。"
          + "只输出优化后的 Markdown，不要任何解释或代码块包裹。";
      Map<String, Object> body = Map.of(
          "model", model,
          "temperature", 0,
          "messages", List.of(
              Map.of("role", "system", "content", systemPrompt),
              Map.of("role", "user", "content", stripped)));
      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
          .timeout(Duration.ofSeconds(60))
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        log.warn("Markdown format request failed ({}): {}", response.statusCode(),
            describeLlmError(response.statusCode(), response.body()));
        return new RagDtos.FormatResponse(markdown);
      }
      JsonNode root = objectMapper.readTree(response.body());
      String optimized = root.path("choices").path(0).path("message").path("content").asText("");
      if (optimized.isBlank() || looksLikeLlmError(optimized)) {
        log.warn("Markdown format returned blank or error-like content, falling back to original");
        return new RagDtos.FormatResponse(markdown);
      }
      return new RagDtos.FormatResponse(optimized.trim());
    } catch (Exception exception) {
      log.warn("Markdown format request error, returning original: {}", exception.getMessage());
      return new RagDtos.FormatResponse(markdown);
    }
  }

  public RagDtos.OrganizeResponse organize(RagDtos.OrganizeRequest request) {
    String question = request.question();
    String answer = request.answer();
    if (question == null || question.isBlank() || answer == null || answer.isBlank()) {
      return new RagDtos.OrganizeResponse(question, answer);
    }
    String provider = properties.ai().provider() == null ? "" : properties.ai().provider().toLowerCase();
    String apiKey = properties.ai().apiKey();
    if (!LLM_PROVIDERS.contains(provider) || apiKey == null || apiKey.isBlank()) {
      return new RagDtos.OrganizeResponse(deriveTitle(question), "# " + deriveTitle(question) + "\n\n" + answer);
    }
    String baseUrl = resolveBaseUrl(provider);
    String model = resolveModel(provider);
    try {
      String strippedAnswer = stripImages(answer);
      String systemPrompt = "你是知识整理助手。把用户给出的一组问答整理成一篇结构清晰、可长期查阅的 Wiki 文章，要求："
          + "1) 给出简洁的中文标题（不附带「问答」字样，不附Markdown标题标记）；"
          + "2) 正文用标准 Markdown：用 ## 小节划分，必要时用列表与表格；"
          + "3) 保留原回答中的 [编号] 引用标记与关键事实，不得增删信息或编造内容；"
          + "4) 删除寒暄与重复表述，合并同类要点；"
          + "5) 不要输出 JSON、不要用代码块包裹整篇文章。"
          + "第一行输出标题文本（纯文本，无 #），其后用一个空行，再输出整理后的 Markdown 正文（正文从 # 标题 开始）。";
      String userContent = "问题：\n" + question + "\n\n回答：\n" + strippedAnswer;
      Map<String, Object> body = Map.of(
          "model", model,
          "temperature", 0.2,
          "messages", List.of(
              Map.of("role", "system", "content", systemPrompt),
              Map.of("role", "user", "content", userContent)));
      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
          .timeout(Duration.ofSeconds(60))
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        log.warn("Organize request failed ({}): {}", response.statusCode(),
            describeLlmError(response.statusCode(), response.body()));
        return new RagDtos.OrganizeResponse(deriveTitle(question), "# " + deriveTitle(question) + "\n\n" + answer);
      }
      JsonNode root = objectMapper.readTree(response.body());
      String content = root.path("choices").path(0).path("message").path("content").asText("");
      if (content.isBlank() || looksLikeLlmError(content)) {
        log.warn("Organize returned blank or error-like content, falling back to original answer");
        return new RagDtos.OrganizeResponse(deriveTitle(question), "# " + deriveTitle(question) + "\n\n" + answer);
      }
      String[] parts = content.split("\n", 2);
      String title = parts[0].trim();
      String markdown = parts.length > 1 ? parts[1].trim() : content.trim();
      if (title.isEmpty()) title = deriveTitle(question);
      if (!markdown.startsWith("#")) markdown = "# " + title + "\n\n" + markdown;
      return new RagDtos.OrganizeResponse(title, markdown);
    } catch (Exception exception) {
      log.warn("Organize request error, returning fallback: {}", exception.getMessage());
      return new RagDtos.OrganizeResponse(deriveTitle(question), "# " + deriveTitle(question) + "\n\n" + answer);
    }
  }

  private String deriveTitle(String question) {
    String trimmed = question.trim();
    return trimmed.length() > 24 ? trimmed.substring(0, 24) + "…" : trimmed;
  }

  private String stripImages(String markdown) {
    if (markdown == null) return "";
    return markdown.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "");
  }

  private boolean looksLikeLlmError(String content) {
    if (content == null) return false;
    String lower = content.toLowerCase();
    return lower.contains("does not support image input")
        || lower.contains("cannot read")
        || lower.contains("image input is not supported")
        || lower.startsWith("error:");
  }

  public RagDtos.AiStatus status() {
    String provider = properties.ai().provider() == null ? "mock" : properties.ai().provider().toLowerCase();
    String apiKey = properties.ai().apiKey();
    boolean configured = LLM_PROVIDERS.contains(provider) && apiKey != null && !apiKey.isBlank();
    return new RagDtos.AiStatus(
        provider,
        configured ? resolveModel(provider) : "—",
        configured,
        configured ? resolveBaseUrl(provider) : "—");
  }

  public RagDtos.AiStatus testConnection() {
    String provider = properties.ai().provider() == null ? "" : properties.ai().provider().toLowerCase();
    String apiKey = properties.ai().apiKey();
    if (!LLM_PROVIDERS.contains(provider) || apiKey == null || apiKey.isBlank()) {
      return new RagDtos.AiStatus(provider, "—", false, "—");
    }
    String baseUrl = resolveBaseUrl(provider);
    String model = resolveModel(provider);
    try {
      Map<String, Object> body = Map.of(
          "model", model,
          "temperature", 0,
          "max_tokens", 8,
          "messages", List.of(Map.of("role", "user", "content", "你好，请回复“在线”")));
      HttpRequest httpRequest = HttpRequest.newBuilder()
          .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
          .timeout(Duration.ofSeconds(20))
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) {
        log.warn("AI connection test failed ({}): {}", response.statusCode(), response.body());
        return new RagDtos.AiStatus(provider, model, false, baseUrl);
      }
      return new RagDtos.AiStatus(provider, model, true, baseUrl);
    } catch (Exception exception) {
      log.warn("AI connection test error: {}", exception.getMessage());
      return new RagDtos.AiStatus(provider, model, false, baseUrl);
    }
  }

  private String generate(String question, List<RagDtos.Source> sources) {
    String provider = properties.ai().provider() == null ? "" : properties.ai().provider().toLowerCase();
    String apiKey = properties.ai().apiKey();
    if (!LLM_PROVIDERS.contains(provider) || apiKey == null || apiKey.isBlank()) {
      return mockAnswer(question, sources);
    }
    String baseUrl = resolveBaseUrl(provider);
    String model = resolveModel(provider);
    try {
      String context = buildContext(sources);
      String extra = extraSystemContext(question);
      Map<String, Object> body = Map.of(
          "model", model,
          "temperature", 0.2,
          "messages", List.of(
              Map.of("role", "system", "content", SYSTEM_PROMPT),
              Map.of("role", "user", "content", "问题：" + stripImages(question) + "\n\n资料：\n" + context + extra)));
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
        .timeout(Duration.ofSeconds(60))
          .header("Authorization", "Bearer " + apiKey)
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
          .build();
      HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() >= 300) throw new IllegalStateException(response.body());
      JsonNode root = objectMapper.readTree(response.body());
      return root.path("choices").path(0).path("message").path("content").asText();
    } catch (Exception exception) {
      log.warn("LLM request failed, using deterministic fallback: {}", exception.getMessage());
      return mockAnswer(question, sources);
    }
  }

  private String describeLlmError(int statusCode, String errorBody) {
    return switch (statusCode) {
      case 401 -> "AI 服务认证失败，请检查 API Key 是否正确";
      case 402 -> "AI 服务余额不足，请检查 API Key 账户额度";
      case 429 -> "AI 服务请求过于频繁，请稍后重试";
      default -> "AI 服务返回错误（HTTP " + statusCode + "）"
          + (errorBody != null && !errorBody.isBlank() ? ": " + errorBody.substring(0, Math.min(errorBody.length(), 200)) : "");
    };
  }

  private String resolveBaseUrl(String provider) {
    String configured = properties.ai().baseUrl();
    if (configured != null && !configured.isBlank()) return configured;
    return "deepseek".equals(provider) ? DEEPSEEK_DEFAULT_BASE_URL : OPENAI_DEFAULT_BASE_URL;
  }

  private String resolveModel(String provider) {
    String configured = properties.ai().model();
    if (configured != null && !configured.isBlank()) return configured;
    return "deepseek".equals(provider) ? DEEPSEEK_DEFAULT_MODEL : "gpt-4.1-mini";
  }

  private String mockAnswer(String question, List<RagDtos.Source> sources) {
    if (sources.isEmpty()) {
      return "当前知识库中没有检索到足够的相关资料。建议换一个更具体的关键词，或先将相关文件上传到资料库并等待解析完成。";
    }
    String citations = sources.stream().limit(3).map(source -> "[" + source.id() + "]").collect(java.util.stream.Collectors.joining(" "));
    return """
        根据知识库中与“%s”相关的资料，建议按以下方式推进：

        1. **先明确目标与范围**：确认这项工作的对象、预期结果、负责人和完成时间。
        2. **建立可执行清单**：把准备、执行和复盘拆成具体任务，并为每项任务标记责任人与截止时间。
        3. **复用已有资料**：优先参考往届模板、流程表和规范，避免重复设计。
        4. **保留过程记录**：将关键决策、版本和反馈沉淀到在线文档或 Wiki，方便后续检索。
        5. **完成复盘归档**：工作结束后补充数据、问题和改进建议，让资料持续可用。

        以上结论主要来自 %s。当前使用本地可运行的规则型回答器；配置 OpenAI 兼容模型后会自动切换为基于检索上下文的 LLM 生成。
        """.formatted(question, citations);
  }
}
