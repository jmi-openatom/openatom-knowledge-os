package cn.jmi.openatom.knowledge.service;

import cn.jmi.openatom.knowledge.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.net.URI;
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
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.RegionUtil;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelExportService {

  private final AppProperties properties;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  /**
   * Generate a styled Excel file from markdown content.
   * If the markdown contains tables, they are parsed directly.
   * If no tables are found, AI is used to extract structured data.
   */
  public byte[] exportToExcel(String markdown, String title) throws Exception {
    List<String[][]> tables = parseMarkdownTables(markdown);

    if (tables.isEmpty()) {
      // No tables found — use AI to extract/organize data into table format
      log.info("No tables found in markdown, using AI to extract structured data");
      String aiMarkdown = aiOrganizeToTable(markdown, title);
      tables = parseMarkdownTables(aiMarkdown);
    }

    if (tables.isEmpty()) {
      // Still no tables — create a simple text sheet
      return createTextExcel(markdown, title);
    }

    return createStyledExcel(tables, title);
  }

  /**
   * Parse all markdown tables into rows of cells.
   */
  private List<String[][]> parseMarkdownTables(String md) {
    List<String[][]> tables = new ArrayList<>();
    String[] lines = md.split("\n");
    int i = 0;
    while (i < lines.length) {
      String line = lines[i].trim();
      if (line.startsWith("|") && line.endsWith("|") && line.split("\\|").length >= 3) {
        // Check next line is separator
        if (i + 1 < lines.length && lines[i + 1].trim().matches("^\\|?\\s*[-:]+[-|\\s:]+")) {
          List<String[]> rows = new ArrayList<>();
          rows.add(splitRow(line));
          i += 2; // skip header + separator
          while (i < lines.length && lines[i].trim().startsWith("|")) {
            rows.add(splitRow(lines[i].trim()));
            i++;
          }
          tables.add(rows.toArray(new String[0][]));
          continue;
        }
      }
      i++;
    }
    return tables;
  }

  private String[] splitRow(String line) {
    String cleaned = line.replaceAll("^\\||\\|$", "");
    String[] cells = cleaned.split("\\|");
    List<String> result = new ArrayList<>();
    for (String cell : cells) {
      String c = cell.trim();
      // Remove bold markers
      c = c.replaceAll("\\*\\*", "");
      // Remove citation markers like [13]
      c = c.replaceAll("\\[\\d+\\]", "").trim();
      result.add(c);
    }
    return result.toArray(new String[0]);
  }

  /**
   * Use AI to organize free-form text into a markdown table.
   */
  private String aiOrganizeToTable(String content, String title) throws Exception {
    String provider = properties.ai().provider() == null ? "" : properties.ai().provider().toLowerCase();
    String apiKey = properties.ai().apiKey();
    if (!isLlmAvailable(provider, apiKey)) {
      return content;
    }
    String baseUrl = resolveBaseUrl(provider);
    String model = resolveModel(provider);
    String systemPrompt = "你是数据整理专家。把用户给出的文本内容整理成结构化的 Markdown 表格格式。"
        + "要求：1) 提取关键信息组成表格；2) 表头用简短的中文；3) 每行一条数据，不要合并单元格；"
        + "4) 只输出 Markdown 表格，不要解释。如果内容不适合表格形式，原样返回。";
    Map<String, Object> body = Map.of(
        "model", model,
        "temperature", 0.1,
        "messages", List.of(
            Map.of("role", "system", "content", systemPrompt),
            Map.of("role", "user", "content", content)));
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions"))
        .timeout(Duration.ofSeconds(60))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
        .build();
    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() >= 300) {
      log.warn("AI table extraction failed ({}): {}", response.statusCode(), response.body());
      return content;
    }
    JsonNode root = objectMapper.readTree(response.body());
    String result = root.path("choices").path(0).path("message").path("content").asText("");
    return result.isBlank() ? content : result;
  }

  /**
   * Create a styled Excel workbook from parsed tables.
   */
  private byte[] createStyledExcel(List<String[][]> tables, String title) throws Exception {
    try (Workbook workbook = new XSSFWorkbook();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      // Styles
      XSSFCellStyle headerStyle = createHeaderStyle((XSSFWorkbook) workbook);
      XSSFCellStyle dataStyle = createDataStyle((XSSFWorkbook) workbook);
      XSSFCellStyle titleStyle = createTitleStyle((XSSFWorkbook) workbook);

      String sheetName = title != null && !title.isBlank()
          ? title.replaceAll("[\\\\/?*\\[\\]:]", "").substring(0, Math.min(title.length(), 31))
          : "数据";
      XSSFSheet sheet = (XSSFSheet) workbook.createSheet(sheetName);

      int rowIdx = 0;
      // Title row
      if (title != null && !title.isBlank()) {
        Row titleRow = sheet.createRow(rowIdx++);
        titleRow.createCell(0).setCellValue(title);
        titleRow.getCell(0).setCellStyle(titleStyle);
      }

      boolean firstTable = true;
      for (String[][] table : tables) {
        if (!firstTable) {
          rowIdx++; // blank row between tables
        }
        firstTable = false;

        for (int r = 0; r < table.length; r++) {
          Row row = sheet.createRow(rowIdx++);
          for (int c = 0; c < table[r].length; c++) {
            var cell = row.createCell(c);
            cell.setCellValue(table[r][c]);
            cell.setCellStyle(r == 0 ? headerStyle : dataStyle);
          }
        }
      }

      // Auto-size columns
      int maxCols = tables.stream().mapToInt(t -> t[0].length).max().orElse(10);
      for (int c = 0; c < maxCols; c++) {
        sheet.autoSizeColumn(c);
        // Ensure minimum width
        int width = sheet.getColumnWidth(c);
        if (width < 3000) sheet.setColumnWidth(c, 3000);
        if (width > 12000) sheet.setColumnWidth(c, 12000);
      }

      // Freeze header row (row after title)
      int freezeRow = (title != null && !title.isBlank()) ? 1 : 0;
      sheet.createFreezePane(0, freezeRow + 1);

      workbook.write(out);
      return out.toByteArray();
    }
  }

  private byte[] createTextExcel(String text, String title) throws Exception {
    try (XSSFWorkbook workbook = new XSSFWorkbook();
         ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      String sheetName = title != null && !title.isBlank()
          ? title.replaceAll("[\\\\/?*\\[\\]:]", "").substring(0, Math.min(title.length(), 31))
          : "内容";
      XSSFSheet sheet = workbook.createSheet(sheetName);
      XSSFCellStyle style = createDataStyle(workbook);
      style.setWrapText(true);

      String[] lines = text.split("\n");
      int rowIdx = 0;
      for (String line : lines) {
        Row row = sheet.createRow(rowIdx++);
        var cell = row.createCell(0);
        cell.setCellValue(line);
        cell.setCellStyle(style);
        row.setHeightInPoints(Math.max(20, (line.length() / 80 + 1) * 18));
      }
      sheet.setColumnWidth(0, 12000);
      workbook.write(out);
      return out.toByteArray();
    }
  }

  private XSSFCellStyle createHeaderStyle(XSSFWorkbook wb) {
    XSSFCellStyle style = wb.createCellStyle();
    style.setAlignment(HorizontalAlignment.CENTER);
    XSSFFont font = wb.createFont();
    font.setBold(true);
    font.setFontHeight(11);
    font.setFontName("微软雅黑");
    style.setFont(font);
    style.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
    style.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.MEDIUM);
    style.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
    style.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);
    style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
    return style;
  }

  private XSSFCellStyle createDataStyle(XSSFWorkbook wb) {
    XSSFCellStyle style = wb.createCellStyle();
    style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
    XSSFFont font = wb.createFont();
    font.setFontHeight(10);
    font.setFontName("微软雅黑");
    style.setFont(font);
    style.setBorderTop(org.apache.poi.ss.usermodel.BorderStyle.THIN);
    style.setBorderBottom(org.apache.poi.ss.usermodel.BorderStyle.THIN);
    style.setBorderLeft(org.apache.poi.ss.usermodel.BorderStyle.THIN);
    style.setBorderRight(org.apache.poi.ss.usermodel.BorderStyle.THIN);
    style.setWrapText(true);
    return style;
  }

  private XSSFCellStyle createTitleStyle(XSSFWorkbook wb) {
    XSSFCellStyle style = wb.createCellStyle();
    style.setAlignment(HorizontalAlignment.CENTER);
    XSSFFont font = wb.createFont();
    font.setBold(true);
    font.setFontHeight(16);
    font.setFontName("微软雅黑");
    style.setFont(font);
    style.setVerticalAlignment(org.apache.poi.ss.usermodel.VerticalAlignment.CENTER);
    return style;
  }

  private boolean isLlmAvailable(String provider, String apiKey) {
    return (provider.equals("deepseek") || provider.equals("openai-compatible"))
        && apiKey != null && !apiKey.isBlank();
  }

  private String resolveBaseUrl(String provider) {
    String configured = properties.ai().baseUrl();
    if (configured != null && !configured.isBlank()) return configured;
    return "deepseek".equals(provider) ? "https://api.deepseek.com" : "https://api.openai.com/v1";
  }

  private String resolveModel(String provider) {
    String configured = properties.ai().model();
    if (configured != null && !configured.isBlank()) return configured;
    return "deepseek".equals(provider) ? "deepseek-chat" : "gpt-4.1-mini";
  }
}
