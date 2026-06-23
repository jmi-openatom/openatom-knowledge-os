package cn.jmi.openatom.knowledge.repository;

import cn.jmi.openatom.knowledge.model.UserProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {
  Optional<UserProfile> findBySubject(String subject);
}
