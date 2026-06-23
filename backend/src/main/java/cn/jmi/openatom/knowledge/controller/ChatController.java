package cn.jmi.openatom.knowledge.controller;

import cn.jmi.openatom.knowledge.dto.ChatDtos;
import cn.jmi.openatom.knowledge.dto.ChatDtos.ConversationDto;
import cn.jmi.openatom.knowledge.dto.ChatDtos.MessageDto;
import cn.jmi.openatom.knowledge.dto.RagDtos;
import cn.jmi.openatom.knowledge.model.ChatConversation;
import cn.jmi.openatom.knowledge.model.ChatMessage;
import cn.jmi.openatom.knowledge.repository.ChatConversationRepository;
import cn.jmi.openatom.knowledge.repository.ChatMessageRepository;
import cn.jmi.openatom.knowledge.security.CurrentUser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('ai:chat', 'ROLE_ADMIN', 'ROLE_LEADER')")
public class ChatController {
  private final ChatConversationRepository conversationRepo;
  private final ChatMessageRepository messageRepo;
  private final ChatDtos dtos;
  private final ObjectMapper objectMapper;

  @GetMapping("/conversations")
  public List<ConversationDto> listConversations(@AuthenticationPrincipal CurrentUser user) {
    return conversationRepo.findByUserSubjectOrderByUpdatedAtDesc(user.subject()).stream()
        .map(dtos::toConversationDto).toList();
  }

  @GetMapping("/conversations/{id}/messages")
  public List<MessageDto> getMessages(@PathVariable Long id) {
    return messageRepo.findByConversationIdOrderByCreatedAtAsc(id).stream()
        .map(dtos::toMessageDto).toList();
  }

  @PostMapping("/conversations")
  @Transactional
  public ConversationDto createConversation(@RequestBody Map<String, String> body, @AuthenticationPrincipal CurrentUser user) {
    ChatConversation conv = new ChatConversation();
    conv.setUserSubject(user.subject());
    conv.setTitle(body.getOrDefault("title", "新对话"));
    conv = conversationRepo.save(conv);
    return dtos.toConversationDto(conv);
  }

  @PostMapping("/conversations/{id}/messages")
  @Transactional
  public MessageDto saveMessage(@PathVariable Long id, @RequestBody Map<String, Object> body) throws Exception {
    ChatMessage msg = new ChatMessage();
    msg.setConversationId(id);
    msg.setQuestion(String.valueOf(body.get("question")));
    msg.setAnswer(String.valueOf(body.get("answer")));
    Object sources = body.get("sources");
    msg.setSources(sources != null ? objectMapper.writeValueAsString(sources) : "[]");
    msg = messageRepo.save(msg);
    ChatConversation conv = conversationRepo.findById(id).orElseThrow();
    conv.setTitle(String.valueOf(body.getOrDefault("title", conv.getTitle())));
    conversationRepo.save(conv);
    return dtos.toMessageDto(msg);
  }

  @DeleteMapping("/conversations/{id}")
  @Transactional
  public void deleteConversation(@PathVariable Long id) {
    messageRepo.findByConversationIdOrderByCreatedAtAsc(id).forEach(m -> messageRepo.delete(m));
    conversationRepo.deleteById(id);
  }
}
