package cn.jmi.openatom.knowledge.repository;

import cn.jmi.openatom.knowledge.model.WikiPage;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WikiPageRepository extends JpaRepository<WikiPage, Long> {
  List<WikiPage> findAllByOrderBySortOrderAsc();

  List<WikiPage> findByDeletedAtIsNullOrderBySortOrderAsc();
}
