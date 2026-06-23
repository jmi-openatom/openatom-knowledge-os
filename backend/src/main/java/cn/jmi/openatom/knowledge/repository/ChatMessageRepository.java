package cn.jmi.openatom.knowledge.repository;

import cn.jmi.openatom.knowledge.model.ChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
  List<ChatMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);
}
