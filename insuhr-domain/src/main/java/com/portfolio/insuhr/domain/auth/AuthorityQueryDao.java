package com.portfolio.insuhr.domain.auth;

import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 계정의 역할·권한 조회 (설계서 4.3의 QueryDao 규칙).
 *
 * <p>TB_USER_ROLE / TB_ROLE_PERM은 순수 조인 테이블이라 엔티티로 매핑하지 않는다. 필요한 건 "이 계정이 가진 PERM_CD 집합" 하나뿐이고, 그건
 * 조인 한 방이면 끝난다.
 */
@Repository
public class AuthorityQueryDao {

  private final JdbcClient jdbcClient;

  public AuthorityQueryDao(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /**
   * 계정이 가진 권한 코드({리소스}.{행위}) 집합.
   *
   * <p>여러 역할이 같은 권한을 주면 중복되므로 DISTINCT 한다.
   */
  public Set<String> findPermissionCodes(Long userId) {
    List<String> codes =
        jdbcClient
            .sql(
                """
                SELECT DISTINCT rp.PERM_CD
                  FROM TB_USER_ROLE ur
                  JOIN TB_ROLE_PERM rp ON rp.ROLE_CD = ur.ROLE_CD
                 WHERE ur.USER_ID = :userId
                """)
            .param("userId", userId)
            .query(String.class)
            .list();
    return Set.copyOf(codes);
  }

  /** 계정에 부여된 역할 코드 집합. */
  public Set<String> findRoleCodes(Long userId) {
    List<String> codes =
        jdbcClient
            .sql("SELECT ur.ROLE_CD FROM TB_USER_ROLE ur WHERE ur.USER_ID = :userId")
            .param("userId", userId)
            .query(String.class)
            .list();
    return Set.copyOf(codes);
  }

  /** 계정에 역할을 부여한다. 계정 관리 API(Phase 1 범위 밖)가 생기기 전까지 시드·테스트에서 쓴다. */
  public void grantRole(Long userId, String roleCd, String actor) {
    jdbcClient
        .sql(
            """
            INSERT INTO TB_USER_ROLE (USER_ID, ROLE_CD, CREATED_BY)
            VALUES (:userId, :roleCd, :actor)
            """)
        .param("userId", userId)
        .param("roleCd", roleCd)
        .param("actor", actor)
        .update();
  }
}
