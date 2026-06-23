package cn.jmi.openatom.knowledge.service;

import cn.jmi.openatom.knowledge.dto.WikiDto;
import cn.jmi.openatom.knowledge.model.WikiPage;
import cn.jmi.openatom.knowledge.repository.WikiPageRepository;
import cn.jmi.openatom.knowledge.security.CurrentUser;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WikiService {
  private final WikiPageRepository repository;
  private final AuditService auditService;

  public List<WikiDto> tree() {
    List<WikiPage> all = repository.findByDeletedAtIsNullOrderBySortOrderAsc();
    return all.stream().filter(page -> page.getParentId() == null)
        .map(page -> WikiDto.from(page, children(page.getId(), all))).toList();
  }

  @Transactional
  public WikiDto save(Long id, WikiDto input, CurrentUser user) {
    WikiPage page = id == null ? new WikiPage() : repository.findById(id).orElseThrow(() -> new IllegalArgumentException("Wiki 页面不存在"));
    page.setParentId(input.parentId());
    page.setTitle(input.title());
    page.setType(input.type() == null ? "page" : input.type());
    page.setContent(input.content() == null ? "" : input.content());
    page.setSortOrder(input.sortOrder());
    page.setUpdatedByName(user.name());
    page = repository.save(page);
    auditService.record(user, id == null ? "WIKI_CREATE" : "WIKI_UPDATE", "wiki", page.getId(), page.getTitle());
    return WikiDto.from(page, List.of());
  }

  private List<WikiDto> children(Long parentId, List<WikiPage> all) {
    return all.stream().filter(page -> parentId.equals(page.getParentId()))
        .map(page -> WikiDto.from(page, children(page.getId(), all))).toList();
  }

  public WikiDto getWikiPageById(Long id) {
    WikiPage page = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Wiki 页面不存在"));
    if (page.getDeletedAt() != null) {
      throw new IllegalArgumentException("Wiki 页面已被删除");
    }
    return WikiDto.from(page, List.of());
  }

  @Transactional
  public void deleteWikiPage(Long id, CurrentUser user) {
    WikiPage page = repository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("Wiki 页面不存在"));
    if (page.getDeletedAt() != null) {
      throw new IllegalArgumentException("Wiki 页面已被删除");
    }
    LocalDateTime now = LocalDateTime.now();
    page.setDeletedAt(now);
    repository.save(page);
    auditService.record(user, "WIKI_DELETE", "wiki", page.getId(), page.getTitle());
    // Cascade soft-delete all descendants
    List<WikiPage> all = repository.findByDeletedAtIsNullOrderBySortOrderAsc();
    cascadeDelete(page.getId(), all, now, user);
  }

  private void cascadeDelete(Long parentId, List<WikiPage> all, LocalDateTime now, CurrentUser user) {
    for (WikiPage child : all) {
      if (parentId.equals(child.getParentId()) && child.getDeletedAt() == null) {
        child.setDeletedAt(now);
        repository.save(child);
        auditService.record(user, "WIKI_DELETE", "wiki", child.getId(), child.getTitle());
        cascadeDelete(child.getId(), all, now, user);
      }
    }
  }
}
