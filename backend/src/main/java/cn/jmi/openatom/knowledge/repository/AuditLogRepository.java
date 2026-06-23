package cn.jmi.openatom.knowledge.repository;

import cn.jmi.openatom.knowledge.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
