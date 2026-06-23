package cn.jmi.openatom.knowledge.service;

import cn.jmi.openatom.knowledge.model.AuditLog;
import cn.jmi.openatom.knowledge.repository.AuditLogRepository;
import cn.jmi.openatom.knowledge.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditService {
  private final AuditLogRepository repository;

  public void record(CurrentUser user, String action, String resourceType, Object resourceId, String detail) {
    AuditLog log = new AuditLog();
    log.setActorSubject(user.subject());
    log.setActorName(user.name());
    log.setAction(action);
    log.setResourceType(resourceType);
    log.setResourceId(resourceId == null ? null : String.valueOf(resourceId));
    log.setDetail(detail);
    repository.save(log);
  }
}
