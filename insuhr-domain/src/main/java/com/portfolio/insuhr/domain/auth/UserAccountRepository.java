package com.portfolio.insuhr.domain.auth;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

  Optional<UserAccount> findByLoginId(String loginId);

  boolean existsByLoginId(String loginId);
}
