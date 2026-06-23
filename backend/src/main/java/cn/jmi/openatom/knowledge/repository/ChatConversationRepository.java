package cn.jmi.openatom.knowledge.repository;

import cn.jmi.openatom.knowledge.model.ChatConversation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {
  List<ChatConversation> findByUserSubjectOrderByUpdatedAtDesc(String userSubject);
}
