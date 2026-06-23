package cn.jmi.openatom.knowledge.dto;

import cn.jmi.openatom.knowledge.model.ChatConversation;
import cn.jmi.openatom.knowledge.model.ChatMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatDtos {
  private final ObjectMapper objectMapper;

  public record ConversationDto(Long id, String title, String updatedAt) {
    public static ConversationDto from(ChatConversation c) {
      return new ConversationDto(c.getId(), c.getTitle(), String.valueOf(c.getUpdatedAt()));
    }
  }

  public record MessageDto(Long id, String question, String answer, List<RagDtos.Source> sources, String createdAt) {
  }

  public ConversationDto toConversationDto(ChatConversation c) {
    return new ConversationDto(c.getId(), c.getTitle(), String.valueOf(c.getUpdatedAt()));
  }

  public MessageDto toMessageDto(ChatMessage m) {
    List<RagDtos.Source> sources = List.of();
    if (m.getSources() != null && !m.getSources().isBlank()) {
      try {
        sources = objectMapper.readValue(m.getSources(), new TypeReference<List<RagDtos.Source>>() {});
      } catch (Exception ignored) {}
    }
    return new MessageDto(m.getId(), m.getQuestion(), m.getAnswer(), sources, String.valueOf(m.getCreatedAt()));
  }
}
