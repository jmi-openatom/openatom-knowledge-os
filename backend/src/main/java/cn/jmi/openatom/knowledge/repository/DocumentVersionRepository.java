package cn.jmi.openatom.knowledge.repository;

import cn.jmi.openatom.knowledge.model.DocumentVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {
  List<DocumentVersion> findByDocumentIdOrderByVersionDesc(Long documentId);
}
