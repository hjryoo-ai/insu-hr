# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 저장소 현재 상태

**Phase 0~3 완료** (2026-07-18). `./gradlew build` 그린, 테스트 100개(+`@Disabled` 1). 마이그레이션 V1~V10.

- Phase 0: 멀티모듈 골격 5종, docker-compose, 공통 응답/예외/에러코드, BaseEntity+Auditing, Spotless, Testcontainers
- Phase 1: 공통코드+부록 A 시드(V2), 정책값(V3), 계정/역할/권한+Refresh 토큰(V4), UTC 규약(V5), AES-GCM·해시·마스킹 유틸, JWT 인증, Security 7 RBAC
- Phase 2: 조직+이력(V6), 인물+주소+FK 보강(V7), 조직 트리 시점 조회, 중복차단(유니크 제약+동시성), 복호화+접근로그, IntegrationRecorder 스텁, AuditorAware 전환
- Phase 3: 임직원+발령(V8), 인사기록카드 6종(V9), 휴가/연차(V10). 발령 반영은 **증분이 아니라 재계산**(`AppointmentApplyService`) — 스냅샷 = "CONFIRMED이고 발령일≤기준일인 발령 중 (발령일, ID) 최대"라는 함수(설계서 5.5). `Clock` 빈 주입으로 날짜 경계 테스트(시나리오 6a). 사번은 `SEQ_EMP_NO` 무의미 번호

다음은 설계서 §13.2의 **Phase 4 — 설계사 코어**: TB_AGENT + 상태머신(AgentLifecycleService), 후보등록/위촉/정지/해촉/재위촉/이동, 위촉계약, 계보. 완료 기준은 시나리오 1·4.

Phase 4+에서 갚아야 할 것:
- `OrgService.close()`의 **TB_AGENT 소속 검사가 아직 비어 있다** — TB_EMP 검사는 Phase 3에서 채웠다. `OrgCloseMemberIntegrationTest`에 `@Disabled("Phase 4: TB_AGENT 검사")` 테스트가 실행 가능한 형태의 빚으로 남아 있다. Phase 4에서 검사 추가 후 그 `@Disabled`를 제거
- `NoOpIntegrationRecorder` — Phase 6에서 실제 구현을 붙이고 **이 클래스를 삭제**한다(조건부 등록이 아니라 삭제)
- **Phase 7 `futureAppointApplyJob`** — `AppointmentApplyService`를 **감싸기만** 한다(반영 규칙 재구현 금지). 시나리오 6b(배치 래퍼+멱등)가 여기서 완결
- **Phase 8 `privacyPurgeJob`** — 대상 두 종류: 보존기간 경과 인물 + **역할 없는 인물**(설계서 5.2 v1.4)

## 단일 사양(SSOT)

`insuhr-design-spec.md`(현재 **v1.4**)가 이 프로젝트의 유일한 사양이다. 코드/판단이 설계서와 충돌하면 **설계서가 우선**한다.
설계서와 다르게 구현해야 할 이유가 있으면 임의로 벗어나지 말고 사용자에게 먼저 알린다.

**설계서가 현실과 어긋나는 것을 발견하면 CLAUDE.md나 코드 주석에만 우회 기록을 남기지 말고 설계서 본문을 개정하고 말미의 개정 이력에 남긴다.** 그렇게 하지 않으면 다음 세션이 "설계서 우선" 규칙을 근거로 정정을 되돌린다. 이 파일에는 결론만 두고 근거·상세는 설계서에 둔다.

설계서에서 자주 참조하게 될 절:
- §5.3 설계사 위촉 상태머신 전이표 / §5.4 모집자격 판정 규칙표
- §6.1 DB 명명 규칙 / §6.3~6.5 테이블 38종 스펙
- §7.2 엔드포인트 목록 / §9.3 이벤트 카탈로그
- §13.2 Phase별 작업과 완료 기준 / 부록 A 공통코드 시드 / 부록 B 위촉 E2E 시나리오

## 기술 스택

Java 21 (Corretto 21.0.9) · Spring Boot **4.1.0** (Spring Framework 7.0.8) · Oracle 23ai Free · Gradle 9.3.1 Kotlin DSL 멀티모듈 · Flyway 12.4.0 · Hibernate 7.4.1 · JUnit 6.0.3 · Testcontainers 2.0.5

버전은 `gradle/libs.versions.toml`과 Boot BOM이 관리한다. 영속성은 JPA가 주(主), 조회·배치용 네이티브 SQL은 외부 매퍼 없이 Spring `JdbcClient`를 쓴다 — MyBatis 등을 추가하지 말 것.

**Boot 3 예제를 그대로 옮기지 말 것.** Boot 4는 자동설정을 기술별 모듈로 쪼갰고(라이브러리만 추가하면 무증상으로 동작 안 함), 여러 클래스가 이동·제거됐다. 정정된 목록과 근거는 **설계서 §3.0 "Boot 4 이행 주의사항"** 에 있다 — 의존성이나 임포트를 새로 추가하기 전에 읽을 것. Spring Security 7도 6.x DSL이 통하지 않으므로 람다 DSL 기준 최신 문법으로 작성한다.

## 명령어

```bash
docker compose up -d oracle          # Oracle 23ai Free (jdbc:oracle:thin:@localhost:1521/FREEPDB1, insuhr/insuhr)
docker compose --profile kafka up -d # Kafka는 선택 프로파일에서만
./gradlew build                      # 전체 빌드 + 테스트 (매 Phase 종료 시 그린이어야 함)
./gradlew :insuhr-api:bootRun        # API 서버 → http://localhost:8080/actuator/health
./gradlew spotlessApply              # google-java-format. build 전에 돌릴 것 — spotlessCheck가 build를 깬다
./gradlew :insuhr-domain:test --tests '*AgentLifecycleServiceTest*'   # 단일 테스트
java -jar insuhr-batch/build/libs/insuhr-batch-0.0.1-SNAPSHOT.jar --job.name=eligibilityRefreshJob --targetDate=2026-07-17

# DB 직접 확인
docker exec -it insuhr-oracle sqlplus insuhr/insuhr@localhost:1521/FREEPDB1
```

Flyway가 기동 시 스키마를 구성하므로 수동 DDL 실행은 없다. **migrate 실행 주체는 api 하나로 고정**이고 **batch/relay는 validate 전용**(스키마가 어긋나면 기동 실패)이다 — 설계서 §4.2. 통합 테스트는 Docker 데몬이 떠 있어야 한다(Docker Desktop은 첫 기동 시 macOS 관리자 권한 프롬프트를 띄우므로 사용자 승인이 필요할 수 있다).

## 아키텍처

### 모듈 의존 방향 (역방향 금지)

```
insuhr-api ┐
insuhr-batch ├─→ insuhr-domain ─→ insuhr-common
insuhr-relay ┘
```

- `common`: 프레임워크 무관 공통 — 예외 체계, 응답 envelope, 암호화/마스킹 유틸, 공통코드 상수
- `domain`: JPA 엔티티 + 도메인 서비스 + 리포지토리. **모든 비즈니스 규칙이 여기 산다**
- `api` / `batch` / `relay`: 실행 모듈 3종. 배치 부하가 온라인에 영향 없도록 프로세스를 분리하는 것이 설계 의도
- 루트 패키지 `com.portfolio.insuhr`, 도메인별 `...{도메인}.domain / .application / .api`

### 계층 규칙

Controller → Application Service(`@Transactional`, 유스케이스 단위) → Domain → Repository(JPA) + `*QueryDao`(JdbcClient) → Oracle

- 상태 전이·위촉 가능 여부·모집자격 계산 같은 규칙을 Controller나 Application Service에 두지 않는다. 반드시 `insuhr-domain`의 엔티티 메서드/도메인 서비스로.
- 조회 전용 복잡 쿼리(현황판·통계·배치 Reader)는 `*QueryDao`에 JdbcClient 네이티브 SQL로 쓰고 **엔티티가 아닌 전용 record DTO**를 반환한다.
- 배치 Reader는 `JdbcPagingItemReader`/`JdbcCursorItemReader`. 대량 처리에 JPA Reader 지양.

### 핵심 설계 결정 — 여러 절을 읽어야 보이는 것들

**Person 1 : 역할 N.** `TB_PERSON`(주민번호 기준 유일) 아래 `TB_EMP`와 `TB_AGENT`가 각각 0..1로 붙는다. 직원 퇴사 후 설계사 위촉 같은 케이스에서 인물 레코드는 하나로 유지된다. 신규 등록은 항상 `RRN_HASH`로 기존 인물을 먼저 찾고, 있으면 역할만 추가한다. 주민번호 원문은 `TB_PERSON`에만 있고 타 테이블은 `PERSON_ID` 대리키만 참조한다.

**설계사 상태머신이 도메인의 중심.** `CANDIDATE → PENDING_ASSOC → ACTIVE ⇄ SUSPENDED → TERMINATED → (재위촉) CANDIDATE`. 전이는 전부 `AgentLifecycleService`를 통과해야 하고, 각 전이는 ① 사전조건 검사(§5.3 표) ② `TB_AGENT_APPOINT_HIST` 1행 기록 ③ Outbox 이벤트 발행을 함께 수행한다. 재위촉은 `AGENT_ID`와 과거 이력을 유지한다.

**모집자격(Eligibility)은 계산 도메인 + 스냅샷의 이중 구조.** `RecruitEligibilityService.evaluate()`가 위촉상태·판매자격·협회등록·보수교육·변액자격·재정보증·제재를 종합해 **종목별** 판정과 사유 목록을 낸다. API는 실시간 계산, `TB_AGENT.RECRUIT_ELIG_YN`은 `eligibilityRefreshJob`(매일 05:00)이 갱신하며 이 배치가 ACTIVE↔SUSPENDED 자동 전이의 주체다. 판정 실패는 예외가 아니라 **사유 배열을 담은 422 응답**으로 표현한다(§7.3).

**기준정보 변경 = 업무테이블 + ChangeLog + Outbox, 한 트랜잭션.** 기준정보를 바꾸는 모든 Application Service 메서드는 같은 로컬 트랜잭션에서 `TB_IF_CHANGE_LOG`와 `TB_IF_OUTBOX`에 기록한다(분산 트랜잭션 불사용). 이 기록은 직접 하지 말고 공통 컴포넌트 **`IntegrationRecorder`**를 통한다. Phase 6 전에는 no-op 구현을 주입해두고, Phase 6에서 서비스 코드 수정 없이 실구현으로 교체하는 것이 설계 의도다.

**3계층 동기화가 하나의 원천에서 파생된다.** ① Outbox → relay → Webhook(기본)/Kafka(`kafka` 프로파일) ② `GET /sync/changes` 커서 Pull API ③ 일배치 CSV 스냅샷. 셋 다 `TB_IF_OUTBOX`/`TB_IF_CHANGE_LOG`에서 나오므로 상호 대사가 가능하다. relay는 `SELECT ... FOR UPDATE SKIP LOCKED`로 READY 이벤트를 집어 `EVENT_ID` 오름차순으로 보내 동일 aggId 순서를 보장하고, 수신측은 `eventUuid`로 멱등 처리한다.

**이벤트 페이로드에 민감정보 금지.** 마스킹된 이름과 업무키만 싣는다. 주민번호·계좌가 필요한 시스템은 Pull API + 복호화 권한으로 별도 조회한다.

## 코딩 규칙

- **DB 변경은 Flyway 마이그레이션 파일로만.** `ddl-auto`는 `validate`만 허용, 엔티티로 스키마를 만들지 않는다. 파티셔닝은 로컬에서 없어도 동작하도록 `V90__partitioning.sql`로 분리.
- **법령 의존 수치 하드코딩 금지.** 보수교육 주기(24개월), 재정보증 최소금액, 재위촉 냉각기간(6개월), 개인정보 보존기간 등은 전부 `TB_POLICY_CONFIG`에서 읽는다. 설계서의 수치는 포트폴리오용 기본값이며 실제 법령값이 아니다.
- **개인식별정보는 기본 마스킹.** 목록·상세 응답은 항상 마스킹 값, 원문은 전용 복호화 엔드포인트에서만 — 사유 입력 필수 + `TB_PRIVACY_ACCESS_LOG` 기록. 암호화는 AES-256-GCM, 암호문에 키 버전 프리픽스(`v1:...`)를 붙여 회전 가능하게. 키·pepper는 환경변수/시크릿에서 주입.
- **명명**: 테이블 `TB_{도메인}_{명사}`, PK는 `GENERATED AS IDENTITY` 단일 대리키, 업무키는 별도 UNIQUE. 컬럼 접미사 `_CD`(코드) `_YN`(CHAR(1)) `_DT`(DATE) `_AT`(TIMESTAMP) `_AMT` `_ENC`(암호문) `_HASH`.
- **에러코드**: `{도메인3자}-{HTTP류}{일련2자}` — COM/ORG/PER/EMP/AGT/LIC/EDU/GRT/SNC/IFC/AUT.
- **행 수준 접근 통제**: BRANCH_MANAGER·SUPPORT_STAFF는 본인 소속 조직 트리 하위만 조회 가능 — 공통 `OrgScopeFilter`가 쿼리에 조직 ID 목록을 강제 주입한다.
- 감사 이력은 DB 트리거가 아니라 Application AOP로 남긴다(테스트 용이성).
- 커밋: Conventional Commits (feat/fix/test/refactor/docs/chore).

## 테스트

- 모든 공개 API는 통합 테스트 1개 이상을 동반한다. 테스트 없는 변경 금지.
- 리포지토리·SQL·API 통합 테스트는 H2가 아니라 **Testcontainers의 실제 Oracle 컨테이너 + Flyway**로 돌린다.
- 설계서 §12의 필수 시나리오 테스트 8종이 사실상의 인수 기준이다. 각 Phase의 "완료 기준"이 어떤 시나리오 번호를 요구하는지 §13.2에 명시돼 있다.
- 단위 테스트가 반드시 커버해야 할 것: 상태머신 전이표 **전체**, 모집자격 판정 규칙(경계일자 포함), `NEXT_DUE_DT` 계산, 마스킹/암호화 유틸.

## 작업 순서

Phase 내에서는 **Flyway → 엔티티 → 도메인 서비스 → 애플리케이션 서비스 → 컨트롤러 → 테스트** 순으로 진행하고, 각 Phase는 독립 실행 가능한 상태(`./gradlew build` 그린)로 끝낸다.
