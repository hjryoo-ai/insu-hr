package com.portfolio.insuhr.domain.person;

import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.integration.IntegrationEvent;
import com.portfolio.insuhr.domain.integration.IntegrationRecorder;
import com.portfolio.insuhr.domain.policy.PolicyConfigService;
import com.portfolio.insuhr.domain.policy.PolicyKey;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 개인정보 파기(익명화) 집행 (설계서 8, 10.2 Phase 8, {@code privacyPurgeJob}이 감싼다).
 *
 * <p>대상 판정(두 대상군)은 배치 Reader가 하고, 이 서비스는 <b>한 인물의 파기</b>를 원자적으로 집행한다: ① {@link
 * Person#anonymize}(암호화 컬럼·RRN 해시 NULL + 이름 마스킹) ② 주소 삭제 ③ 파기 대장 적재 ④ {@code person.purged} 발행. 넷이
 * 한 트랜잭션이라 파기가 전파(다운스트림도 사본을 지움)되거나 전부 롤백된다.
 *
 * <p><b>{@code person.purged}를 발행하는 이유</b>: 다운스트림이 동기화로 사본을 쥐고 있어 원천만 지우면 파기가 전파되지 않는다 — 컴플라이언스
 * 관점에서 이벤트가 파기의 일부다. 페이로드는 업무키({@code personId})와 파기유형만 싣고 민감 원문은 없다(9.3, 이미 있는 "민감정보 부재" 테스트가 이
 * 이벤트도 지킨다). 스냅샷이 이미 익명화됐으므로 ChangeLog 보존도 안전하다(Phase 6 결정의 배당금).
 *
 * <p>재실행 멱등: 이미 익명화된 인물({@link Person#isAnonymized})은 no-op — Reader가 애초에 걸러내지만 여기서 한 번 더 막는다.
 */
@Service
public class PersonPurgeService {

  private final PersonRepository personRepository;
  private final PrivacyPurgeDao purgeDao;
  private final IntegrationRecorder integrationRecorder;
  private final PolicyConfigService policyConfigService;
  private final ObjectMapper objectMapper;

  public PersonPurgeService(
      PersonRepository personRepository,
      PrivacyPurgeDao purgeDao,
      IntegrationRecorder integrationRecorder,
      PolicyConfigService policyConfigService,
      ObjectMapper objectMapper) {
    this.personRepository = personRepository;
    this.purgeDao = purgeDao;
    this.integrationRecorder = integrationRecorder;
    this.policyConfigService = policyConfigService;
    this.objectMapper = objectMapper;
  }

  /**
   * 한 인물을 익명화하고 대장·이벤트를 남긴다. 이미 파기됐으면 no-op.
   *
   * @param purgeTypeCd ROLE_ENDED | ORPHAN
   * @param basisDate 파기 근거 기준일(역할종료: max(퇴직·해촉일), 무역할: 인물 생성일)
   * @return 실제로 파기했으면 {@code true}
   */
  @Transactional
  public boolean purge(Long personId, String purgeTypeCd, LocalDate basisDate, LocalDate asOf) {
    Person person =
        personRepository
            .findById(personId)
            .orElseThrow(
                () ->
                    new BusinessException(PersonErrorCode.NOT_FOUND, "인물을 찾을 수 없습니다: " + personId));
    if (person.isAnonymized()) {
      return false; // 멱등 — 이미 파기됨
    }

    person.anonymize(); // 영속 엔티티 — 커밋 시 flush
    purgeDao.deleteAddresses(personId);
    purgeDao.insertLedger(
        personId,
        purgeTypeCd,
        objectMapper.writeValueAsString(basis(purgeTypeCd, basisDate, asOf)));

    integrationRecorder.record(
        IntegrationEvent.created(
            "person.purged",
            "PERSON",
            personId,
            String.valueOf(personId),
            Map.of("personId", personId, "purgeType", purgeTypeCd)));
    return true;
  }

  /** 파기 근거 스냅샷(정책값 포함) — 대장에 남겨 나중에 "왜 파기됐나"를 재구성 가능하게. 민감정보 없음. */
  private Map<String, Object> basis(String purgeTypeCd, LocalDate basisDate, LocalDate asOf) {
    Map<String, Object> m = new LinkedHashMap<>();
    m.put("purgeType", purgeTypeCd);
    m.put("basisDate", basisDate == null ? null : basisDate.toString());
    m.put("asOf", asOf.toString());
    if ("ORPHAN".equals(purgeTypeCd)) {
      m.put(
          "orphanPurgeDays", policyConfigService.getInt(PolicyKey.ORPHAN_PERSON_PURGE_DAYS, asOf));
    } else {
      m.put("retentionYears", policyConfigService.getInt(PolicyKey.PRIVACY_RETENTION_YEARS, asOf));
    }
    return m;
  }
}
