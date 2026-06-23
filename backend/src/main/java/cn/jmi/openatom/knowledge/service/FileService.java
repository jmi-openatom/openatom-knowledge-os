package cn.jmi.openatom.knowledge.service;

import cn.jmi.openatom.knowledge.dto.FileDto;
import cn.jmi.openatom.knowledge.dto.FilePreviewDto;
import cn.jmi.openatom.knowledge.model.KnowledgeFile;
import cn.jmi.openatom.knowledge.repository.KnowledgeFileRepository;
import cn.jmi.openatom.knowledge.security.CurrentUser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class FileService {
  private static final long MAX_SIZE = 100L * 1024 * 1024;
  private static final List<String> ALLOWED = List.of("doc", "docx", "xls", "xlsx", "ppt", "pptx", "pdf", "md", "txt");

  private final KnowledgeFileRepository repository;
  private final StorageService storage;
  private final SearchIndexService searchIndex;
  private final AuditService auditService;

  public List<FileDto> list() {
    return repository.findByStatusNotOrderByUpdatedAtDesc(KnowledgeFile.Status.DELETED)
        .stream().map(FileDto::from).toList();
  }

  @Transactional
  public FileDto upload(MultipartFile multipart, List<String> tags, CurrentUser user) {
    validate(multipart);
    String extension = extension(multipart.getOriginalFilename());
    String objectKey = LocalDate.now() + "/" + UUID.randomUUID() + "." + extension;
    KnowledgeFile file = new KnowledgeFile();
    file.setName(safeName(multipart.getOriginalFilename()));
    file.setExtension(extension.toUpperCase(Locale.ROOT));
    file.setContentType(multipart.getContentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : multipart.getContentType());
    file.setSize(multipart.getSize());
    file.setObjectKey(objectKey);
    file.tags(tags);
    file.setCreatedBySubject(user.subject());
    file.setCreatedByName(user.name());
    file.setStatus(KnowledgeFile.Status.UPLOADING);
    try {
      byte[] bytes = multipart.getBytes();
      storage.put(objectKey, new ByteArrayInputStream(bytes), bytes.length, file.getContentType());
      file.setStatus(KnowledgeFile.Status.PARSING);
      file = repository.save(file);
      parseAndIndex(file.getId(), bytes);
      auditService.record(user, "FILE_UPLOAD", "file", file.getId(), file.getName());
      return FileDto.from(file);
    } catch (Exception exception) {
      file.setStatus(KnowledgeFile.Status.FAILED);
      file.setFailureMessage(exception.getMessage());
      repository.save(file);
      throw new IllegalStateException("文件上传失败", exception);
    }
  }

  @Async
  @Transactional
  public void parseAndIndex(Long id, byte[] bytes) {
    KnowledgeFile file = repository.findById(id).orElseThrow();
    try {
      String text = extractText(file, bytes);
      file.setExtractedText(text == null ? "" : text);
      file.setStatus(KnowledgeFile.Status.READY);
      file.setFailureMessage(null);
      repository.save(file);
      searchIndex.index(file);
    } catch (Exception exception) {
      file.setStatus(KnowledgeFile.Status.FAILED);
      file.setFailureMessage(exception.getMessage());
      repository.save(file);
    }
  }

  private String extractText(KnowledgeFile file, byte[] bytes) throws Exception {
    String ext = file.getExtension() == null ? "" : file.getExtension().toUpperCase(Locale.ROOT);
    if ("MD".equals(ext) || "TXT".equals(ext)) {
      return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }
    BodyContentHandler handler = new BodyContentHandler(-1);
    Metadata metadata = new Metadata();
    metadata.set(Metadata.CONTENT_TYPE, file.getContentType());
    ParseContext context = new ParseContext();
    Parser parser = "PDF".equals(ext) ? new PDFParser() : new AutoDetectParser();
    parser.parse(new ByteArrayInputStream(bytes), handler, metadata, context);
    return handler.toString();
  }

  public ResponseEntity<InputStreamResource> download(Long id) {
    KnowledgeFile file = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("文件不存在"));
    InputStream input = storage.get(file.getObjectKey());
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(file.getContentType()))
        .contentLength(file.getSize())
        .header("Content-Disposition", "attachment; filename*=UTF-8''" +
            java.net.URLEncoder.encode(file.getName(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"))
        .body(new InputStreamResource(input));
  }

  public FilePreviewDto preview(Long id) {
    KnowledgeFile file = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("文件不存在"));
    return FilePreviewDto.from(file);
  }

  @Transactional
  public FilePreviewDto updateContent(Long id, String content, CurrentUser user) {
    KnowledgeFile file = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("文件不存在"));
    String ext = file.getExtension() == null ? "" : file.getExtension().toUpperCase(Locale.ROOT);
    if (!"MD".equals(ext) && !"TXT".equals(ext)) {
      throw new IllegalArgumentException("仅支持 Markdown 和文本文件的在线编辑");
    }
    file.setExtractedText(content == null ? "" : content);
    file = repository.save(file);
    searchIndex.delete(id);
    searchIndex.index(file);
    auditService.record(user, "FILE_EDIT", "file", id, file.getName());
    return FilePreviewDto.from(file);
  }

  @Transactional
  public void delete(Long id, CurrentUser user) {
    KnowledgeFile file = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("文件不存在"));
    file.setStatus(KnowledgeFile.Status.DELETED);
    repository.save(file);
    searchIndex.delete(id);
    auditService.record(user, "FILE_DELETE", "file", id, file.getName());
  }

  private void validate(MultipartFile file) {
    if (file.isEmpty()) throw new IllegalArgumentException("请选择要上传的文件");
    if (file.getSize() > MAX_SIZE) throw new IllegalArgumentException("单个文件不能超过 100MB");
    if (!ALLOWED.contains(extension(file.getOriginalFilename()))) throw new IllegalArgumentException("不支持该文件格式");
  }

  private String extension(String name) {
    if (name == null || !name.contains(".")) return "";
    return name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
  }

  private String safeName(String name) {
    if (name == null) return "未命名文件";
    return name.replaceAll("[\\\\/\\r\\n]", "_");
  }
}
