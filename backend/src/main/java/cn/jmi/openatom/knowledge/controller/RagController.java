package cn.jmi.openatom.knowledge.controller;

import cn.jmi.openatom.knowledge.dto.RagDtos;
import cn.jmi.openatom.knowledge.service.RagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/rag")
@RequiredArgsConstructor
public class RagController {
  private final RagService service;

  @PostMapping("/chat")
  @PreAuthorize("hasAnyAuthority('ai:chat', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public RagDtos.ChatResponse chat(@Valid @RequestBody RagDtos.ChatRequest request) {
    return service.chat(request);
  }

  @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  @PreAuthorize("hasAnyAuthority('ai:chat', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public SseEmitter chatStream(@Valid @RequestBody RagDtos.ChatRequest request) {
    return service.chatStream(request);
  }

  @PostMapping("/format")
  @PreAuthorize("hasAnyAuthority('ai:chat', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public RagDtos.FormatResponse format(@Valid @RequestBody RagDtos.FormatRequest request) {
    return service.format(request);
  }

  @PostMapping("/organize")
  @PreAuthorize("hasAnyAuthority('ai:chat', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public RagDtos.OrganizeResponse organize(@Valid @RequestBody RagDtos.OrganizeRequest request) {
    return service.organize(request);
  }

  @GetMapping("/status")
  @PreAuthorize("hasAnyAuthority('ai:chat', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public RagDtos.AiStatus status() {
    return service.status();
  }

  @PostMapping("/test")
  @PreAuthorize("hasAnyAuthority('ai:chat', 'ROLE_ADMIN', 'ROLE_LEADER')")
  public RagDtos.AiStatus testConnection() {
    return service.testConnection();
  }
}
