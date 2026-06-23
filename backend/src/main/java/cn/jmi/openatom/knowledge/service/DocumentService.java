package cn.jmi.openatom.knowledge.service;

import cn.jmi.openatom.knowledge.dto.DocumentDto;
import cn.jmi.openatom.knowledge.model.DocumentVersion;
import cn.jmi.openatom.knowledge.model.KnowledgeDocument;
import cn.jmi.openatom.knowledge.repository.DocumentVersionRepository;
import cn.jmi.openatom.knowledge.repository.KnowledgeDocumentRepository;
import cn.jmi.openatom.knowledge.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentService {
  private final KnowledgeDocumentRepository repository;
  private final DocumentVersionRepository versionRepository;
  private final AuditService auditService;

  public List<DocumentDto> list() {
    return repository.findAllByDeletedAtIsNullOrderByUpdatedAtDesc().stream().map(DocumentDto::from).toList();
  }

  public DocumentDto get(Long id) {
    KnowledgeDocument document = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("文档不存在"));
    if (document.getDeletedAt() != null) {
      throw new IllegalArgumentException("文档已被删除");
    }
    return DocumentDto.from(document);
  }

  @Transactional
  public DocumentDto create(DocumentDto input, CurrentUser user) {
    KnowledgeDocument document = new KnowledgeDocument();
    document.setTitle(input.title());
    document.setContent(input.content() == null ? "" : input.content());
    document.setUpdatedBySubject(user.subject());
    document.setUpdatedByName(user.name());
    document = repository.save(document);
    saveVersion(document, user);
    auditService.record(user, "DOCUMENT_CREATE", "document", document.getId(), document.getTitle());
    return DocumentDto.from(document);
  }

  @Transactional
  public DocumentDto update(Long id, DocumentDto input, CurrentUser user) {
    KnowledgeDocument document = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("文档不存在"));
    document.setTitle(input.title());
    document.setContent(input.content() == null ? "" : input.content());
    document.setVersion(document.getVersion() + 1);
    document.setUpdatedBySubject(user.subject());
    document.setUpdatedByName(user.name());
    document = repository.save(document);
    saveVersion(document, user);
    auditService.record(user, "DOCUMENT_UPDATE", "document", id, "version=" + document.getVersion());
    return DocumentDto.from(document);
  }

  public List<DocumentVersion> versions(Long id) {
    return versionRepository.findByDocumentIdOrderByVersionDesc(id);
  }

  @Transactional
  public void delete(Long id, CurrentUser user) {
    KnowledgeDocument document = repository.findById(id).orElseThrow(() -> new IllegalArgumentException("文档不存在"));
    document.setDeletedAt(LocalDateTime.now());
    repository.save(document);
    auditService.record(user, "DOCUMENT_DELETE", "document", id, document.getTitle());
  }

  private void saveVersion(KnowledgeDocument document, CurrentUser user) {
    DocumentVersion version = new DocumentVersion();
    version.setDocumentId(document.getId());
    version.setVersion(document.getVersion());
    version.setTitle(document.getTitle());
    version.setContent(document.getContent());
    version.setCreatedByName(user.name());
    versionRepository.save(version);
  }
}
