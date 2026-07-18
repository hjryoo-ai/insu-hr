package com.portfolio.insuhr.domain.notice;

import java.time.LocalDate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 알림 대기 큐 적재 (설계서 8 v2.0, Phase 7 후속).
 *
 * <p><b>팬아웃에서 검증된 {@code INSERT … SELECT … WHERE NOT EXISTS} 한 문장</b>을 그대로 재사용한다({@link
 * com.portfolio.insuhr.domain.integration.IfDeliveryDao} 참조). JPA로 저장하면 {@code UQ_NOTICE_QUEUE} 위반이
 * 영속성 컨텍스트를 rollback-only로 오염시켜(Phase 2 함정) 같은 청크 트랜잭션의 후속이 죽는다. {@code WHERE NOT EXISTS}는 위반을 애초에
 * 안 만들고 UQ는 크래시 백스톱으로만 둔다.
 *
 * <p>반환값이 <b>실제 삽입 행 수(0/1)</b>라는 점이 중요하다 — 호출한 배치 프로세서는 이 값이 1일 때만 {@code notice.created}를
 * 발행한다(설계서 8 v2.0 "행이 실제로 생성될 때만"). 재실행은 0을 돌려 no-op이 된다({@code futureAppointApplyJob}과 같은 조건부 발행
 * 패턴).
 *
 * <p>트랜잭션 경계는 호출자(배치 스텝 청크)가 건다.
 */
@Repository
public class NoticeQueueDao {

  private final JdbcClient jdbcClient;

  public NoticeQueueDao(JdbcClient jdbcClient) {
    this.jdbcClient = jdbcClient;
  }

  /**
   * 알림 한 건을 멱등 적재한다. 같은 {@code (유형,대상,기한,마일스톤)}이 이미 있으면 건너뛴다.
   *
   * @param payloadJson 마스킹·업무키만 담은 JSON (민감정보 금지, 설계서 9.3)
   * @return 새로 삽입한 행 수(1=신규, 0=이미 존재) — 호출자의 조건부 이벤트 발행 신호
   */
  public int enqueue(
      String noticeTypeCd,
      String targetTypeCd,
      long targetId,
      LocalDate dueDt,
      String milestoneCd,
      String payloadJson) {
    return jdbcClient
        .sql(
            """
            INSERT INTO TB_NOTICE_QUEUE
                   (NOTICE_TYPE_CD, TARGET_TYPE_CD, TARGET_ID, DUE_DT, MILESTONE_CD, PAYLOAD, CREATED_BY)
            SELECT :type, :targetType, :targetId, :dueDt, :milestone, :payload, 'BATCH'
              FROM DUAL
             WHERE NOT EXISTS (SELECT 1
                                 FROM TB_NOTICE_QUEUE q
                                WHERE q.NOTICE_TYPE_CD = :type
                                  AND q.TARGET_ID = :targetId
                                  AND q.DUE_DT = :dueDt
                                  AND q.MILESTONE_CD = :milestone)
            """)
        .param("type", noticeTypeCd)
        .param("targetType", targetTypeCd)
        .param("targetId", targetId)
        .param("dueDt", dueDt)
        .param("milestone", milestoneCd)
        .param("payload", payloadJson)
        .update();
  }
}
