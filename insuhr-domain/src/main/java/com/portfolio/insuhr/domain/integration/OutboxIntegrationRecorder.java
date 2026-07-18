package com.portfolio.insuhr.domain.integration;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 기준정보 변경을 ChangeLog + Outbox에 기록하는 실구현 (설계서 9.2, Phase 6).
 *
 * <p>{@code NoOpIntegrationRecorder}(스텁)를 대체한다 — Phase 2부터 서비스들이 이 인터페이스를 호출하도록 해뒀으므로, <b>서비스 코드는 한
 * 줄도 건드리지 않고</b> no-op에서 실발행으로 바뀐다(설계서 13.3). 그 스텁은 소스에서 삭제됐다.
 *
 * <p><b>호출부의 트랜잭션에 합류한다</b>(자체 {@code @Transactional} 없음). 업무 변경과 이벤트 기록이 전부 성공하거나 전부 롤백돼야 유실·유령
 * 이벤트가 없다(설계서 9.2). ChangeLog(전체 스냅샷)와 Outbox(9.3 페이로드)를 한 번에 남긴다.
 *
 * <p><b>직렬화는 Jackson 3 {@code tools.jackson}</b>(설계서 9.2). Boot 4 기본 매퍼이며 {@code
 * com.fasterxml.jackson}(2.x)과 섞지 않는다.
 */
@Component
public class OutboxIntegrationRecorder implements IntegrationRecorder {

  private static final int SCHEMA_VERSION = 1;

  private final IfOutboxRepository outboxRepository;
  private final IfChangeLogRepository changeLogRepository;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  public OutboxIntegrationRecorder(
      IfOutboxRepository outboxRepository,
      IfChangeLogRepository changeLogRepository,
      ObjectMapper objectMapper,
      Clock clock) {
    this.outboxRepository = outboxRepository;
    this.changeLogRepository = changeLogRepository;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  public void record(IntegrationEvent event) {
    Instant now = Instant.now(clock);

    // 변경 로그: 변경 후 전체 상태 (Pull API의 커서 대상, 설계서 9.4).
    changeLogRepository.save(
        IfChangeLog.of(
            event.aggregateType(),
            event.aggregateId(),
            event.changeType(),
            objectMapper.writeValueAsString(event.snapshot())));

    // 아웃박스: 9.3 표준 페이로드 (마스킹 이름·업무키만 — 민감 원문 금지).
    outboxRepository.save(
        IfOutbox.ready(
            event.eventUuid(),
            event.aggregateType(),
            event.aggregateId(),
            event.eventType(),
            objectMapper.writeValueAsString(payload(event, now)),
            now));
  }

  private Map<String, Object> payload(IntegrationEvent event, Instant occurredAt) {
    Map<String, Object> aggregate = new LinkedHashMap<>();
    aggregate.put("type", event.aggregateType());
    aggregate.put("id", event.aggregateId());
    aggregate.put("businessKey", event.businessKey());

    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("eventUuid", event.eventUuid());
    payload.put("eventType", event.eventType());
    payload.put("occurredAt", occurredAt.toString());
    payload.put("aggregate", aggregate);
    payload.put("data", event.data());
    payload.put("schemaVersion", SCHEMA_VERSION);
    return payload;
  }
}
