package com.portfolio.insuhr.api.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.insuhr.api.org.OrgService;
import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.api.support.FailingRecorderConfig;
import com.portfolio.insuhr.api.support.TestSeq;
import com.portfolio.insuhr.domain.org.OrgRepository;
import com.portfolio.insuhr.domain.org.OrgType;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

/**
 * 기록 실패 = 업무 롤백 (설계서 9.2 v1.6 — 트랜잭셔널 아웃박스의 존재 이유).
 *
 * <p>recorder가 호출자 트랜잭션에서 쓰므로, Outbox/ChangeLog 기록이 실패하면 업무 변경도 함께 굴러떨어져야 한다 — "기록 없으면 변경
 * 없음"(10.1.1 접근로그와 같은 방향). 실패하는 recorder를 주입해 조직 행이 커밋되지 않음을 단언한다.
 */
@Import(FailingRecorderConfig.class)
class RecorderFailureRollsBackTest extends AbstractIntegrationTest {

  @Autowired OrgService orgService;
  @Autowired OrgRepository orgRepository;

  @Test
  @DisplayName("recorder가 실패하면 조직 신설도 롤백돼 행이 남지 않는다")
  void recorderFailureRollsBackBusinessChange() {
    String cd = TestSeq.orgCd();

    assertThatThrownBy(
            () ->
                orgService.create(cd, "조직" + cd, OrgType.BRANCH, null, 0, LocalDate.of(2000, 1, 1)))
        .isInstanceOf(RuntimeException.class);

    // 같은 트랜잭션에서 recorder가 터졌으므로 조직 INSERT가 롤백됐다 — 유령 변경이 남지 않는다.
    assertThat(orgRepository.findByOrgCd(cd)).isEmpty();
  }
}
