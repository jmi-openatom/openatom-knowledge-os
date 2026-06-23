package cn.jmi.openatom.knowledge.controller;

import cn.jmi.openatom.knowledge.dto.FileDto;
import cn.jmi.openatom.knowledge.dto.FilePreviewDto;
import cn.jmi.openatom.knowledge.security.CurrentUser;
import cn.jmi.openatom.knowledge.service.FileService;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {
  private final FileService service;

  @GetMapping
  @PreAuthorize("hasAnyAuthority('file:read', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public List<FileDto> list() {
    return service.list();
  }

  @PostMapping
  @PreAuthorize("hasAnyAuthority('file:write', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public FileDto upload(
      @RequestParam("file") MultipartFile file,
      @RequestParam(required = false, defaultValue = "") String tags,
      @AuthenticationPrincipal CurrentUser user) {
    List<String> tagList = Arrays.stream(tags.split(",")).map(String::trim).filter(value -> !value.isBlank()).toList();
    return service.upload(file, tagList, user);
  }

  @GetMapping("/{id}/download")
  @PreAuthorize("hasAnyAuthority('file:read', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public ResponseEntity<InputStreamResource> download(@PathVariable Long id) {
    return service.download(id);
  }

  @GetMapping("/{id}/preview")
  @PreAuthorize("hasAnyAuthority('file:read', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public FilePreviewDto preview(@PathVariable Long id) {
    return service.preview(id);
  }

  @PutMapping("/{id}/content")
  @PreAuthorize("hasAnyAuthority('file:write', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public FilePreviewDto updateContent(
      @PathVariable Long id,
      @RequestBody UpdateContentRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    return service.updateContent(id, request.content(), user);
  }

  public record UpdateContentRequest(String content) {}

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyAuthority('file:delete', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public void delete(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
    service.delete(id, user);
  }
}
