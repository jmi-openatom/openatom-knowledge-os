package cn.jmi.openatom.knowledge.controller;

import cn.jmi.openatom.knowledge.dto.DocumentDto;
import cn.jmi.openatom.knowledge.model.DocumentVersion;
import cn.jmi.openatom.knowledge.security.CurrentUser;
import cn.jmi.openatom.knowledge.service.DocumentService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {
  private final DocumentService service;

  @GetMapping
  public List<DocumentDto> list() { return service.list(); }

  @GetMapping("/{id}")
  public DocumentDto get(@PathVariable Long id) { return service.get(id); }

  @PostMapping
  @PreAuthorize("hasAnyAuthority('doc:edit', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public DocumentDto create(@Valid @RequestBody DocumentDto input, @AuthenticationPrincipal CurrentUser user) {
    return service.create(input, user);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('doc:edit', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public DocumentDto update(@PathVariable Long id, @Valid @RequestBody DocumentDto input, @AuthenticationPrincipal CurrentUser user) {
    return service.update(id, input, user);
  }

  @GetMapping("/{id}/versions")
  public List<DocumentVersion> versions(@PathVariable Long id) { return service.versions(id); }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('doc:edit', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public void delete(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
    service.delete(id, user);
  }
}
