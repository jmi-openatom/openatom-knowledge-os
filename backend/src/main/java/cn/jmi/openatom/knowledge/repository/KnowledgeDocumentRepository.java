package cn.jmi.openatom.knowledge.repository;

import cn.jmi.openatom.knowledge.model.KnowledgeDocument;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {
  List<KnowledgeDocument> findAllByDeletedAtIsNullOrderByUpdatedAtDesc();
}
