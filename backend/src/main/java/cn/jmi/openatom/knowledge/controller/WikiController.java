package cn.jmi.openatom.knowledge.controller;

import cn.jmi.openatom.knowledge.dto.WikiDto;
import cn.jmi.openatom.knowledge.security.CurrentUser;
import cn.jmi.openatom.knowledge.service.WikiService;
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
@RequestMapping("/api/wiki")
@RequiredArgsConstructor
public class WikiController {
  private final WikiService service;

  @GetMapping
  public List<WikiDto> tree() { return service.tree(); }

  @PostMapping
  @PreAuthorize("hasAnyAuthority('doc:edit', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public WikiDto create(@Valid @RequestBody WikiDto input, @AuthenticationPrincipal CurrentUser user) {
    return service.save(null, input, user);
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('doc:edit', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public WikiDto update(@PathVariable Long id, @Valid @RequestBody WikiDto input, @AuthenticationPrincipal CurrentUser user) {
    return service.save(id, input, user);
  }

  @GetMapping("/{id}")
  public WikiDto getById(@PathVariable Long id) {
    return service.getWikiPageById(id);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('doc:edit', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public void delete(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
    service.deleteWikiPage(id, user);
  }
}
