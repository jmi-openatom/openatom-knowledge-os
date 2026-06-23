package cn.jmi.openatom.knowledge.repository;

import cn.jmi.openatom.knowledge.model.KnowledgeFile;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeFileRepository extends JpaRepository<KnowledgeFile, Long> {
  List<KnowledgeFile> findByStatusNotOrderByUpdatedAtDesc(KnowledgeFile.Status status);
  List<KnowledgeFile> findTop20ByStatusAndExtractedTextContainingIgnoreCaseOrderByUpdatedAtDesc(
      KnowledgeFile.Status status, String query);
}
