package com.portfolio.insuhr.domain.integration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 발행할 기준정보 변경 이벤트 (설계서 9.3).
 *
 * <p>페이로드 표준 스키마의 도메인 표현이다. JSON 직렬화는 연계 계층(Phase 6)이 하며, 여기서는 무엇을 실을지만 정한다.
 *
 * <p><b>민감정보 금지</b>: {@code data}에는 마스킹된 이름과 업무키만 담는다. 주민번호·계좌 원문이 필요한 시스템은 Pull API + 복호화 권한으로 따로
 * 조회한다(설계서 9.3).
 *
 * @param eventUuid 멱등키. 수신측이 중복 처리를 막는 근거라 재전송에도 같은 값이어야 한다
 * @param eventType 9.3 카탈로그의 이벤트 타입. 예: {@code org.created}
 * @param aggregateType PERSON/EMP/AGENT/ORG
 * @param aggregateId 집계 식별자(대리키)
 * @param businessKey 업무키. 예: 조직코드, 설계사코드
 * @param data 이벤트 본문
 * @param changeType 변경 구분 — C(생성)/U(수정)/D(삭제). TB_IF_CHANGE_LOG용
 * @param snapshot 변경 후 전체 상태. Pull API(9.4)가 수신측 upsert에 쓰는 state-carried transfer
 */
public record IntegrationEvent(
    String eventUuid,
    String eventType,
    String aggregateType,
    Long aggregateId,
    String businessKey,
    Map<String, Object> data,
    String changeType,
    Map<String, Object> snapshot) {

  public IntegrationEvent {
    Objects.requireNonNull(eventType, "eventType");
    Objects.requireNonNull(aggregateType, "aggregateType");
    Objects.requireNonNull(aggregateId, "aggregateId");
    eventUuid = eventUuid == null ? UUID.randomUUID().toString() : eventUuid;
    // Map.copyOf 를 쓰지 않는 이유: null 값을 거부한다. 스냅샷에는 null이 정상적으로 들어간다
    // (루트 조직의 upOrgId, 미해촉 설계사의 terminateDt 등) — 그 필드가 null이라는 사실 자체가
    // 수신측이 알아야 할 상태다.
    data = data == null ? Map.of() : unmodifiableCopy(data);
    snapshot = snapshot == null ? Map.of() : unmodifiableCopy(snapshot);
  }

  private static Map<String, Object> unmodifiableCopy(Map<String, Object> source) {
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }

  /** 생성 이벤트. snapshot과 data가 같은 전체 상태다. */
  public static IntegrationEvent created(
      String eventType,
      String aggregateType,
      Long aggregateId,
      String businessKey,
      Map<String, Object> snapshot) {
    return new IntegrationEvent(
        null, eventType, aggregateType, aggregateId, businessKey, snapshot, "C", snapshot);
  }

  /** 변경 이벤트. */
  public static IntegrationEvent updated(
      String eventType,
      String aggregateType,
      Long aggregateId,
      String businessKey,
      Map<String, Object> snapshot) {
    return new IntegrationEvent(
        null, eventType, aggregateType, aggregateId, businessKey, snapshot, "U", snapshot);
  }
}
