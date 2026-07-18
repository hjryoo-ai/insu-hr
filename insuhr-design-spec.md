# InsuHR — 보험사 통합 인사관리시스템 설계서

> **문서 목적**: Claude Code에서 이 문서만으로 시스템을 단계적으로 구현할 수 있도록 작성한 구현용 설계서 (포트폴리오 프로젝트)
> **버전**: 2.1 / 작성일: 2026-07-17 / 최종 개정: 2026-07-18 ([개정 이력](#개정-이력))
> **기술 스택**: Java 21 LTS · Spring Boot 4.1.0 · Oracle Database 23ai

---

## 목차

1. [시스템 개요](#1-시스템-개요)
2. [도메인 용어집](#2-도메인-용어집)
3. [기술 스택 및 개발 환경](#3-기술-스택-및-개발-환경)
4. [아키텍처 설계](#4-아키텍처-설계)
5. [도메인 모델 설계](#5-도메인-모델-설계)
6. [데이터베이스 설계 (Oracle)](#6-데이터베이스-설계-oracle)
7. [API 설계 (REST)](#7-api-설계-rest)
8. [배치 설계 (Spring Batch)](#8-배치-설계-spring-batch)
9. [대외 연계·동기화 설계](#9-대외-연계동기화-설계)
10. [보안 및 개인정보보호 설계](#10-보안-및-개인정보보호-설계)
11. [비기능 요구사항](#11-비기능-요구사항)
12. [테스트 전략](#12-테스트-전략)
13. [Claude Code 구현 가이드 (Phase별 작업 지시)](#13-claude-code-구현-가이드)
- [부록 A: 공통코드 초기 데이터](#부록-a-공통코드-초기-데이터)
- [부록 B: 핵심 시나리오 — 설계사 위촉 End-to-End](#부록-b-핵심-시나리오--설계사-위촉-end-to-end)

---

## 1. 시스템 개요

### 1.1 배경과 목적

보험사의 인적 자원은 크게 두 부류로 나뉜다.

- **임직원**: 정규직/계약직 직원, 영업지원 스탭(영업소 총무·지원 인력) 등 근로계약 기반 인력
- **설계사(FC)**: 근로계약이 아닌 **위촉계약** 기반의 보험모집인. 보험업법상 등록·교육·재정보증 등 별도의 법적 관리 요건이 존재

기존 인사시스템은 임직원 중심으로 설계되어 설계사의 위촉/해촉, 판매자격, 법정교육, 재정보증, 제재이력 등을 별도 시스템이나 수기로 관리하는 경우가 많다. 또한 인사 정보는 영업시스템, 수수료시스템, 급여시스템, 그룹웨어, SSO 등 **거의 모든 사내 시스템의 기준정보(Master Data)** 가 되므로, 변경 사항을 안전하고 일관되게 타 시스템에 전파하는 구조가 필수적이다.

**InsuHR의 목표**:

1. 임직원과 설계사를 **하나의 인물(Person) 모델 위에서 통합 관리**하되, 각 유형의 고유 속성은 분리된 도메인으로 관리
2. 설계사의 **모집 활동에 필요한 모든 법적/제도적 요건**(자격, 교육, 협회등록, 재정보증, 제재)을 시스템으로 관리하고, 요건 미충족 시 자동으로 모집자격을 통제
3. 인사 기준정보의 변경을 **이벤트 기반 + Pull API + 배치 파일**의 3계층으로 타 시스템에 동기화할 수 있는 표준 연계 구조 제공
4. 개인정보보호법·신용정보법 수준의 **암호화, 마스킹, 접근이력** 통제

### 1.2 사용자 유형

| 사용자 | 설명 | 주요 사용 기능 |
|---|---|---|
| 인사팀 담당자 | 본사 인사 운영 | 임직원 발령, 조직 개편, 인사기록 관리 |
| 영업관리 담당자 | 설계사 채널 운영 | 위촉/해촉, 자격·교육·재정보증 관리, 제재 처리 |
| 지점장/영업소장 | 영업 조직 관리자 | 소속 설계사 현황 조회, 위촉 품의 |
| 영업지원 스탭 | 지점/영업소 지원 인력 | 소속 설계사 서류 접수·현황 조회(제한 권한) |
| 임직원/설계사 본인 | 셀프서비스 | 본인 정보 조회, 증명서, 교육 이수 현황 |
| 타 시스템 (기계 계정) | 영업/수수료/급여/SSO 등 | 연계 API, 이벤트 구독, 배치 파일 수신 |
| 시스템 관리자 | IT 운영 | 공통코드, 권한, 연계 대상 시스템, 배치 모니터링 |

### 1.3 시스템 범위

**포함 (In Scope)**

- 조직 관리: 본사 조직(부·팀) + 영업 조직(지역단–지점–영업소) 계층 구조, 조직 개편 이력
- 인물(Person) 통합 마스터: 임직원·설계사 공통 신상정보, 주민등록번호 암호화 관리
- 임직원 관리: 채용~퇴직 생애주기, 인사발령(입사/승진/전보/휴직/복직/퇴직), 직급·직책, 인사기록카드(학력/경력/가족/자격/상벌), 근태·휴가 기본 관리
- 설계사 관리:
  - 위촉/해촉 생애주기와 상태머신 (후보등록 → 요건검증 → 위촉 → 활동 → 정지 → 해촉 → 재위촉)
  - 판매자격(생명보험/손해보험/제3보험/변액보험) 및 교차모집 등록 관리
  - 법정교육 관리: 등록교육(신규), 보수교육(주기 이수), 변액 자격교육, 완전판매 교육 — 주기·기준은 코드/설정으로 관리
  - 협회 등록/말소 이력 (생명보험협회·손해보험협회)
  - 재정보증 관리 (보증보험/공탁 등), 만기 통제
  - 제재·징계 이력, 불완전판매 이력, 모집자격 자동 통제
  - 위촉계약 정보, 수수료 지급계좌(암호화), 리크루팅 계보(도입자)
- 공통: 공통코드, 메뉴/역할 기반 권한(RBAC), 변경이력, 개인정보 접근로그
- 연계: Transactional Outbox 기반 이벤트 발행, 변경분 Pull API, 일배치 스냅샷 파일 생성
- 배치: 교육/재정보증/자격 만기 점검, 모집자격 일괄 통제, 스냅샷 생성, 개인정보 보존기한 파기

**제외 (Out of Scope)** — 포트폴리오 범위 통제를 위해 명시적으로 제외

- 급여 계산·수수료 계산 로직 자체 (기준정보 제공까지만 담당)
- 채용 ATS, 성과평가(MBO), e-러닝 콘텐츠(교육 "이수 결과"만 수신·관리)
- 실제 협회/신용정보원 대외계 전문 송수신 (인터페이스 규격과 Mock 구현으로 대체)
- 프런트엔드 화면 (REST API + OpenAPI 문서까지. 단, 화면 목록은 참고로 제공)

### 1.4 설계 원칙

1. **Person은 하나, 역할은 여럿**: 한 사람이 직원이었다가 설계사로 위촉될 수 있고 동시 이력이 남는다. 주민등록번호 기준으로 인물은 단일 레코드.
2. **법적 요건은 데이터로 강제**: "보수교육 미이수 설계사는 신계약 모집 불가" 같은 규칙은 문서가 아니라 상태 필드 + 배치 + API 검증으로 강제한다.
3. **기준정보의 단일 원천(SSOT)**: 타 시스템은 InsuHR가 발행한 이벤트/API/파일만 신뢰한다. 역방향 수정은 금지.
4. **모든 변경은 이력으로**: 발령, 위촉상태, 조직, 자격 등 핵심 엔티티는 유효기간형 이력 또는 감사 이력을 남긴다.
5. **법규 수치는 설정으로**: 보수교육 주기, 재정보증 최소금액, 개인정보 보존기간 등 법령·사규에 따라 달라질 수 있는 수치는 하드코딩하지 않고 정책 테이블/설정값으로 관리한다. (구현 시점에 실제 법령 확인 필요 — 본 문서의 수치는 포트폴리오용 기본값)

---

## 2. 도메인 용어집

| 용어 | 영문/코드 | 정의 |
|---|---|---|
| 설계사 | Agent, FC(Financial Consultant) | 보험사와 위촉계약을 맺고 보험을 모집하는 개인. 근로자가 아님 |
| 위촉 / 해촉 | Appointment / Termination | 설계사 계약의 시작/종료. 직원의 입사/퇴사에 대응 |
| 재위촉 | Re-appointment | 해촉된 설계사가 다시 위촉되는 것. 과거 이력 승계 |
| 판매자격 | Sales License | 보험 종목별 모집 자격. 생명보험/손해보험/제3보험/변액보험 |
| 변액보험 판매자격 | Variable Insurance License | 변액상품 판매를 위한 별도 자격. 전용 시험·교육 필요 |
| 교차모집 | Cross-selling Registration | 생보 설계사가 손보 상품을(또는 그 반대) 모집하도록 타 종목 협회에 추가 등록하는 것 |
| 등록교육 | Registration Education | 설계사 신규 등록 전 이수해야 하는 법정 교육 |
| 보수교육 | Continuing Education | 등록 후 일정 주기(기본값 2년)마다 이수해야 하는 법정 교육. 미이수 시 모집 불가 |
| 완전판매 교육 | Compliance Sales Education | 불완전판매 예방 목적의 사내/법정 교육 |
| 협회 등록 | Association Registration | 생명보험협회/손해보험협회에 대한 모집인 등록. 등록되어야 모집 가능 |
| 재정보증 | Financial Guarantee | 설계사의 금전사고에 대비해 회사가 요구하는 보증(보증보험 증권, 공탁 등) |
| 불완전판매 | Mis-selling | 상품 설명 의무 위반 등으로 계약이 해지·무효 처리된 판매 행위 |
| 제재 | Sanction | 금융당국/협회/사내 징계로 인한 모집 정지·경고 등 |
| 모집자격 상태 | Recruit Eligibility | 자격·교육·재정보증·제재를 종합한 "지금 모집 가능한가"의 계산된 상태 |
| 영업지원 스탭 | Sales Support Staff | 지점/영업소에서 설계사 활동을 지원하는 직원 유형(임직원의 한 구분) |
| 도입자 | Recruiter | 해당 설계사를 리크루팅한 기존 설계사/지점장. 조직 계보 관리에 사용 |
| 기준정보 | Master Data | 타 시스템이 참조하는 원천 인사/조직 데이터 |

---

## 3. 기술 스택 및 개발 환경

> 버전은 2026-07 기준 안정(stable) 릴리스. 구현 시점에 마이너 버전은 최신 패치로 갱신할 것.

| 구분 | 선택 | 버전 | 비고 |
|---|---|---|---|
| Language | Java | **21 (LTS)** | Temurin 배포판 권장. (Java 25 LTS도 호환 — 팀 표준에 따름) |
| Framework | Spring Boot | **4.1.0** | Spring Framework 7 기반. 3.5.x는 2026-06-30 OSS 지원 종료됨. **Boot 4는 자동설정을 기술별 모듈로 분리했다 — 라이브러리(`flyway-core` 등)만 추가하면 자동설정이 붙지 않는다. 아래 3.2 참조** |
| Persistence(주) | Spring Data JPA (Hibernate) | Boot BOM 관리 | 도메인 CRUD·연관관계 중심 |
| Persistence(보조) | Spring `JdbcClient` | Spring 내장 | 통계·대량조회·배치용 네이티브 SQL. 외부 매퍼 의존성 없이 Boot 4와 호환성 보장 |
| DB | Oracle Database | **23ai** (로컬: 23ai Free) | 운영 가정: 19c/23ai. 개발은 Docker `gvenzl/oracle-free` |
| JDBC Driver | ojdbc | ojdbc17 (23ai 계열) | Maven Central 배포본 |
| DB Migration | Flyway | Boot BOM 관리 (12.4.0) | `V{n}__desc.sql` 규칙, Oracle 문법. 의존성 3종 필수: **`org.springframework.boot:spring-boot-flyway`**(자동설정) + `org.flywaydb:flyway-database-oracle`(Oracle 지원) + `flyway-core`. **자동설정 모듈을 빠뜨리면 마이그레이션이 예외 없이 조용히 스킵된다** |
| Batch | Spring Batch | Boot BOM 관리 | 메타테이블은 동일 Oracle 스키마의 별도 테이블스페이스 |
| Security | Spring Security **7** + JWT | Boot BOM / jjwt 0.13.0 | Access/Refresh 토큰, RBAC. 6.x DSL 미호환 — 람다 DSL 기준 최신 문법. **직렬화 모듈은 `jjwt-gson`을 쓴다**(`jjwt-jackson`은 Jackson 2를 끌어옴 — 3.0 참조) |
| API 문서 | springdoc-openapi | Boot 4 호환 최신 | 구현 시점에 Boot 4(Framework 7) 지원 버전인지 확인 후 도입 |
| Messaging(선택) | Spring Kafka | Boot BOM 관리 | `kafka` 프로파일에서만 활성. 기본 프로파일은 Webhook 방식 |
| Mapping | MapStruct | **1.6.3** | Entity ↔ DTO. 1.7.x는 Beta만 릴리스됨(2026-07 기준) — 안정 최신은 1.6.3 |
| Test | JUnit 5(Jupiter 6.0.3), Testcontainers **2.0.5**, RestTestClient | Boot BOM 관리 | 통합 테스트는 실제 Oracle 컨테이너로. **TC 2.x 아티팩트명: `org.testcontainers:testcontainers-oracle-free`, `testcontainers-junit-jupiter`** (1.x의 `oracle-free`/`junit-jupiter`는 2.x에 없음). HTTP 클라이언트는 Spring 7의 `RestTestClient` 또는 `RestClient`+`@LocalServerPort` — **Boot 4에서 `TestRestTemplate`은 제거됨** |
| Build | Gradle (Kotlin DSL) | 최신 안정 | 멀티 모듈 |
| 기타 | Lombok, Spotless(google-java-format) | 최신 안정 | 코드 스타일 일관성 |

### 3.0 Boot 4 이행 주의사항 (v1.1 추가 — Phase 0에서 실증)

Boot 3 기준의 코드/예제를 그대로 옮기면 깨지는 지점들이다. 학습 데이터나 기존 프로젝트를 참고할 때 특히 주의한다.

| 항목 | Boot 3 (구) | Boot 4 (현행) |
|---|---|---|
| 자동설정 위치 | `spring-boot-autoconfigure`에 전부 포함 | **기술별 모듈로 분리** (`spring-boot-flyway`, `spring-boot-jdbc`, `spring-boot-persistence` …). 라이브러리만 넣고 자동설정 모듈을 빠뜨리면 **조용히 동작 안 함** |
| `@EntityScan` | `org.springframework.boot.autoconfigure.domain` | `org.springframework.boot.persistence.autoconfigure` |
| 테스트 HTTP 클라이언트 | `TestRestTemplate` | **제거됨**. Spring 7 `RestTestClient`(`org.springframework.test.web.servlet.client`) 또는 `RestClient` + `@LocalServerPort` |
| JSON | Jackson 2 (`com.fasterxml.jackson`) | **Jackson 3 기본** (`tools.jackson`, 3.1.4). Jackson 2(2.21.4)는 호환용으로만 잔존 |
| Testcontainers | 1.x (`oracle-free`, `junit-jupiter`) | **2.x** (`testcontainers-oracle-free`, `testcontainers-junit-jupiter`). 클래스 패키지는 `org.testcontainers.oracle`로 동일. Oracle 전용 `@ServiceConnection` 팩토리는 없고 범용 `JdbcContainerConnectionDetailsFactory`가 처리 |
| Spring Security | 6.x DSL | **7.1** — `authorizeRequests()`와 체이닝 스타일(`.csrf().disable()`)이 **제거**됨. `Customizer` 람다 오버로드만 남았다. `AccessDeniedHandler`도 `...security.web` → `...security.web.access`로 이동 |
| Hibernate `Instant` 매핑 | — | Hibernate 7 기본값(`TIMESTAMP_UTC`)은 `Instant`를 TIMESTAMP WITH TIME ZONE으로 다뤄, 6.2의 존 없는 TIMESTAMP 컬럼을 읽을 때 **ORA-18716**이 난다. 아래 참조 |
| `_YN` 컬럼 매핑 | — | 6.1의 `_YN`은 CHAR(1)인데 Hibernate는 String 속성을 VARCHAR2로 기대해 `ddl-auto=validate`가 기동을 막는다("wrong column type"). `YnConverter`(boolean ↔ 'Y'/'N') + `@JdbcTypeCode(SqlTypes.CHAR)`를 함께 쓴다 (v1.3) |

**Gradle 관련**

- 애노테이션 프로세서 경로는 `implementation`의 BOM을 물려받지 않는다. `annotationProcessor` / `testAnnotationProcessor` 각각에 platform을 선언해야 Lombok 버전이 해석된다.
- 루트 `build.gradle.kts`의 `libs` 타입세이프 접근자는 `subprojects {}` 블록 안에서 해석되지 않는다(서브프로젝트를 대상으로 조회하다 실패). 루트 스코프에서 값을 캡처해 넘긴다.

**jjwt 직렬화 모듈 선택 (v1.1 — Phase 1 실측)**

jjwt는 JSON 직렬화 모듈을 하나 요구한다. `jjwt-jackson`은 `com.fasterxml.jackson.core:jackson-databind`(**Jackson 2**)에 의존하므로, 이걸 쓰면 Boot 4 기본(Jackson 3)과 두 버전이 클래스패스에 공존한다.

- **HTTP 컨버터가 뒤바뀌지는 않는다** — 확인 결과 Boot 4는 Jackson 2 컨버터를 `spring.http.converters.preferred-json-mapper=jackson2`로 명시하거나 Jackson 3이 아예 없을 때만 등록한다(`PreferJackson2OrJacksonUnavailableCondition`). 즉 공존 자체는 안전하다.
- **그럼에도 `jjwt-gson`을 쓴다.** Jackson 2가 클래스패스에 없으면 우리 코드의 `com.fasterxml` 오임포트가 **컴파일 에러**가 된다. 9.2가 경고하는 "컴파일은 통과하고 런타임 설정만 안 먹는" 실수를 구조적으로 봉쇄하는 쪽이 낫다. Gson은 jjwt 내부에만 쓰이고, Gson HTTP 컨버터 역시 Jackson·JSONB 부재 시에만 등록되므로 API 직렬화에 영향이 없다.

**Oracle 식별자 대소문자**

Flyway 12는 Oracle에서 이력 테이블을 소문자 따옴표 식별자(`flyway_schema_history`, `installed_rank`)로 만든다. Oracle 23ai는 테이블명은 따옴표 없이 찾아주지만 컬럼은 `"installed_rank"`처럼 따옴표가 필요하다. 애초에 Flyway 내부 테이블을 SQL로 조회하지 말고 `Flyway` 빈의 `info()`를 쓴다. 우리 스키마는 6.1 규칙대로 전부 대문자이므로 이 문제가 없다.

**시각 타입 매핑 (v1.1 — Phase 1 실측)**

6.2의 감사 컬럼은 존 없는 `TIMESTAMP`인데 Hibernate 7은 `Instant`를 기본적으로 `TIMESTAMP_UTC`(= TIMESTAMP WITH TIME ZONE)로 매핑해 조회 시 **ORA-18716 "not in any time zone"**이 난다. 다음 두 설정을 함께 건다.

```yaml
spring.jpa.properties.hibernate.type.preferred_instant_jdbc_type: TIMESTAMP  # 존 없는 컬럼에 맞춤
spring.jpa.properties.hibernate.jdbc.time_zone: UTC                          # 적재 존을 UTC로 고정
```

`time_zone`을 UTC로 두는 이유: 존 없는 컬럼에 `Instant`를 넣으려면 "어느 존으로 적재하는가"의 규약이 필요하다. Oracle 컨테이너의 `SYSTIMESTAMP`(마이그레이션 시드의 DEFAULT)가 UTC이므로, 앱도 UTC로 맞춰야 시드 행과 앱 행의 기준이 갈리지 않는다. 응답의 `+09:00` 변환(7.1)은 API 계층이 담당한다.

**통합 테스트 컨테이너 생명주기 (v1.1 — Phase 1 실측)**

Testcontainers의 `@Testcontainers` + `@Container`(static) 조합을 쓰지 말 것. 이 조합은 컨테이너를 **테스트 클래스 단위**로 띄우고 내리는데 스프링 컨텍스트는 클래스 간에 캐시돼 재사용된다. 첫 클래스가 끝나며 컨테이너를 내리면 다음 클래스가 캐시된 컨텍스트의 죽은 커넥션 풀을 물고 **ORA-17008(Closed connection)**로 깨진다. 단일 클래스만 돌리면 통과하고 전체 빌드에서만 깨지므로 발견이 늦다.

대신 **싱글턴 컨테이너 패턴**을 쓴다 — static 초기화로 한 번 띄우고 명시적으로 내리지 않으며(JVM 종료 시 Ryuk가 정리), 접속 정보는 `@DynamicPropertySource`로 주입한다.

**Spring Batch 6 / Boot 4 이행 (v2.0 — Phase 7 진입에서 실측)**

Boot 4.1은 Spring Batch **6.0.x**를 끌어온다(Boot 3은 Batch 5.x). Security 7·Jackson 3에 이어 **세 번째** "메이저가 올라간 하위 프레임워크" — 학습 데이터의 Batch 4/5 예제를 그대로 옮기면 임포트부터 깨진다. jar를 직접 열어 확인한 이행 지점:

| 항목 | Batch 4/5 (구) | Batch 6 / Boot 4.1 (현행, 실측) |
|---|---|---|
| 메타테이블 스키마 초기화 | Boot이 `spring.batch.jdbc.initialize-schema`로 자동 생성 | **그 속성이 제거됨**. `spring-boot-batch` 4.1에 스키마 이니셜라이저 클래스 자체가 없다 — Boot이 배치 메타테이블을 **더 이상 만들지 않는다**. 그러므로 **Flyway가 소유**해야 한다(우리 스키마 소유 원칙과도 일치). DDL 원본은 `spring-batch-core-6.0.4.jar`의 `org/springframework/batch/core/schema-oracle.sql`(V14로 편입) |
| `@EnableBatchProcessing` | 붙여서 인프라 활성화 | **붙이지 말 것**. `BatchAutoConfiguration`이 `@ConditionalOnMissingBean(value=DefaultBatchConfiguration.class, annotation=EnableBatchProcessing.class)`이라 이 애노테이션(또는 `DefaultBatchConfiguration` 빈)이 있으면 **Boot 자동설정이 backoff**해 `JobRepository`·`JobLauncher`·트랜잭션매니저가 안 뜬다. Boot에 맡기고 붙이지 않는다 |
| 잡/스텝 빌더 | `JobBuilderFactory`/`StepBuilderFactory` 주입 | **팩토리 제거**. `new JobBuilder(name, jobRepository)`, `new StepBuilder(name, jobRepository)` 생성자 직접. 청크는 `.chunk(int, PlatformTransactionManager)`(→`SimpleStepBuilder`), 태스클릿은 `.tasklet(tasklet, txManager)` |
| `item`/`repeat` 패키지 | `org.springframework.batch.item.*`, `...repeat.*` | **`org.springframework.batch.infrastructure.*`로 이동**. `JdbcPagingItemReader`/`JdbcCursorItemReader`/`JdbcBatchItemWriter` → `...infrastructure.item.database`, `OraclePagingQueryProvider` → `...infrastructure.item.database.support`, `FlatFileItemWriter` → `...infrastructure.item.file`, `ItemReader/Processor/Writer`·`Chunk` → `...infrastructure.item` |
| core 패키지 재배치 | `org.springframework.batch.core.{Job,Step,JobParameters,…}` | `Job`→`...core.job.Job`, `Step`→`...core.step.Step`, `JobParameters/JobParameter/RunIdIncrementer`→`...core.job.parameters.*`, `JobRepository`→`...core.repository.JobRepository`, `Tasklet`→`...core.step.tasklet`, `StepContribution`→`...core.step`, `ChunkContext`→`...core.scope.context`, `JobExecutionListener`→`...core.listener` |
| 잡 파라미터 전달 | 다양 | **비옵션 인자만** 잡 파라미터가 된다 — `JobLauncherApplicationRunner`가 `ApplicationArguments.getNonOptionArgs()`를 `DefaultJobParametersConverter`로 변환. 그래서 실행은 `java -jar ... --spring.batch.job.name=<잡> targetDate=2026-07-18` — 잡 선택은 옵션 인자(`--spring.batch.job.name`), 파라미터는 **비옵션**(`targetDate=...`, `--` 없이). `--targetDate=`처럼 옵션으로 주면 스프링 프로퍼티로 먹혀 잡 파라미터가 안 된다 |
| 잡 자동 실행 게이트 | `spring.batch.job.names` | `spring.batch.job.name`(단수)로 실행할 잡 선택. `spring.batch.job.enabled`(기본 true)가 시동 시 `JobLauncherApplicationRunner`를 켠다 — **미지정 시 컨텍스트의 모든 잡이 돈다**. 그러므로 실행 시 `--spring.batch.job.name`을 **항상** 준다 |
| Reader/Writer 생성 | `new JdbcCursorItemReader()` + setter | **무인자 생성자 제거**. `JdbcCursorItemReaderBuilder`/`FlatFileItemWriterBuilder` 등 **빌더 우선**. `JdbcCursorItemReader`는 `(DataSource, sql, RowMapper)` 생성자만, `FlatFileItemWriter`는 `(resource, lineAggregator)` 생성자만 남았다 |
| 청크 스텝 트랜잭션 | `.chunk(int, txManager)` | 그 오버로드는 **deprecated(removal)**. `.<I,O>chunk(int)`(→`ChunkOrientedStepBuilder`) 뒤 `.reader().processor().writer().transactionManager(tx).faultTolerant().skip(..).skipLimit(..).skipListener(..)`. 스킵 리스너는 `.listener()`가 아니라 **`.skipListener()`** |
| 잡 파라미터 지연바인딩 | `@Value("#{jobParameters['x']}")` | Batch 6에선 **`null`로 떨어진다**(원인 미상, `#{stepExecution}` 주입은 정상). `@Value("#{stepExecution}") StepExecution`을 받아 `stepExecution.getJobParameters().getString("x")`로 읽는다 |
| 인크리멘터 + 업무 파라미터 | `.incrementer(new RunIdIncrementer())` 로 매일 재실행 | 잡에 인크리멘터가 있으면 `JobOperator.start(job, params)`가 **업무 파라미터를 버린다**(로그 "Additional parameters will be ignored", 인크리멘터 값만 남음). 업무 파라미터(`targetDate`)를 쓰는 잡은 **인크리멘터를 달지 않고** 호출자가 유니크 `run.id`를 함께 넘겨 재실행한다(8장 v2.0) |
| `ObjectMapper` 빈 | web 스타터가 딸려옴 | 배치는 web이 없어 Jackson 자동설정 모듈이 빠졌다 → `tools.jackson.databind.ObjectMapper` 빈 부재로 `OutboxIntegrationRecorder`(domain) 배선 실패. 필요가 시작되는 **domain이 `spring-boot-jackson`을 `api`로** 물어 모든 실행 모듈에 빈을 공급한다(구조로 강제, jpa-common.yml과 같은 원칙) |

### 3.1 로컬 개발 환경 (docker-compose)

```yaml
# docker-compose.yml (프로젝트 루트)
services:
  oracle:
    image: gvenzl/oracle-free:23-slim
    ports: ["1521:1521"]
    environment:
      ORACLE_PASSWORD: oracle
      APP_USER: insuhr
      APP_USER_PASSWORD: insuhr
    healthcheck:
      test: ["CMD", "healthcheck.sh"]
      interval: 10s
      timeout: 5s
      retries: 20
  # kafka는 선택: --profile kafka 로만 기동
  kafka:
    image: apache/kafka:latest
    profiles: ["kafka"]
    ports: ["9092:9092"]
```

- 접속 정보(로컬): `jdbc:oracle:thin:@localhost:1521/FREEPDB1`, user `insuhr`
- Flyway가 기동 시 스키마를 구성하므로 별도 수동 DDL 실행 불필요

---

## 4. 아키텍처 설계

### 4.1 전체 구성도

```
                        ┌────────────────────────────────────────────┐
                        │                InsuHR (본 시스템)             │
  [인사/영업관리 담당자]     │                                            │
        │ HTTPS          │  ┌──────────┐   ┌──────────┐   ┌─────────┐ │
        ▼                │  │ insuhr-  │   │ insuhr-  │   │ insuhr- │ │
  ┌───────────┐  REST    │  │   api    │   │  batch   │   │  relay  │ │
  │  (향후 UI) │ ───────► │  │ REST/JWT │   │SpringBatch│  │ Outbox  │ │
  └───────────┘          │  └────┬─────┘   └────┬─────┘   │ Relay   │ │
                         │       │              │         └────┬────┘ │
                         │       ▼              ▼              │      │
                         │  ┌─────────────────────────┐        │      │
                         │  │     Oracle 23ai          │        │      │
                         │  │  HR 스키마 + OUTBOX +     │        │      │
                         │  │  CHANGE_LOG + BATCH 메타  │        │      │
                         │  └─────────────────────────┘        │      │
                         └─────────────────────────────────────┼──────┘
                                                               │
                    ┌──────────────┬──────────────┬────────────┼───────────┐
                    ▼              ▼              ▼            ▼           ▼
              [영업시스템]     [수수료시스템]     [급여시스템]   [그룹웨어/SSO]  [DW/레거시]
              이벤트 구독      이벤트+Pull API    Pull API      이벤트 구독     일배치 파일
```

### 4.2 Gradle 멀티 모듈 구조

```
insuhr/
├── settings.gradle.kts
├── build.gradle.kts                  # 공통 플러그인/버전 관리 (BOM)
├── docker-compose.yml
├── CLAUDE.md                         # 13장 내용 기반으로 생성
├── insuhr-common/                    # 프레임워크 무관 공통
│   └── 예외 체계, 응답 포맷, 공통코드 상수, 암호화 유틸, 마스킹 유틸, 날짜 유틸
├── insuhr-domain/                    # 도메인 계층 (JPA 엔티티 + 도메인 서비스 + 리포지토리)
│   └── org / person / employee / agent / education / guarantee /
│       sanction / eligibility / integration / audit / code / auth
├── insuhr-api/                       # 실행 모듈 1: REST API 서버
│   └── controller / dto / mapper / security / config
├── insuhr-batch/                     # 실행 모듈 2: 배치 서버
│   └── job별 패키지 (8장 참조)
└── insuhr-relay/                     # 실행 모듈 3: Outbox 릴레이 (이벤트 발행기)
    └── poller / publisher(webhook, kafka) / retry
```

의존 방향: `api / batch / relay → domain → common` (역방향 금지)

**스키마 소유와 마이그레이션 실행 주체 (v1.1 — Phase 0 결정)**

- Flyway 마이그레이션 파일은 **insuhr-domain이 소유**한다(`insuhr-domain/src/main/resources/db/migration`). 엔티티와 스키마가 한 모듈에 있어야 같이 움직인다. 세 실행 모듈은 어느 쪽에서든 동일 마이그레이션을 본다.
- **migrate를 실행하는 주체는 insuhr-api 하나로 고정**한다. 여러 프로세스가 동시에 migrate를 시도하면 잠금 경합만 생기고 이득이 없다.
- **batch/relay는 validate 전용으로 켠다** — `FlywayMigrationStrategy` 빈을 `Flyway::validate`로 교체. 마이그레이션은 하지 않되 스키마 버전이 어긋나면 **기동 시점에 실패**한다. 끄지 않는 이유: api보다 batch가 먼저 뜨는 배포 순서 실수를 런타임 SQL 에러로 뒤늦게 발견하는 상황을 막기 위함.
- `insuhr-common`은 **의존성 0개**를 유지한다(4.2의 "프레임워크 무관"의 실질적 의미). Spring·Jackson이 필요한 코드는 상위 모듈로 올린다 — 13.2 Phase 1의 암호화 유틸 배치 기준 참조.

**마이그레이션 불변성 정책 (v1.5 — Phase 4 진입 결정)**

- **`phase-*` 태그에 포함되어 커밋된 마이그레이션 파일은 불변이다.** 이후 스키마 변경은 파일을 고치지 않고 **새 `ALTER` 마이그레이션(다음 버전 번호)을 추가**한다. Flyway는 적용된 파일의 체크섬을 저장하므로, 이미 적용된 파일을 고치면 로컬 도커 볼륨이 남아 있는 환경에서 **체크섬 불일치로 기동이 막힌다**. batch·relay가 validate 전용(위)이라 세 모듈이 같은 히스토리를 검증하는 구조라, 한 파일의 사후 수정은 세 프로세스를 전부 멈춘다.
- **판단 기준**: 그 파일이 phase 태그에 들어갔는가. 태그가 곧 "배포된 버전"의 대역이다 — 배포된 DDL은 되돌리지 않고 앞으로만 고치는 운영 규율을, 태그를 경계로 지금부터 연습한다.
- **아직 태그에 안 들어간 같은 Phase 내 파일은 자유롭게 고친다.** Phase 3의 `_CD` 폭 확장(6.1)은 V8~V10을 커밋 전에 고친 것이라 이 정책과 무관했다 — 그 파일들이 `phase-3` 태그에 들어간 지금부터 불변이다.

### 4.3 계층 구조 (insuhr-api 기준)

```
Controller (REST, 검증, 인증 컨텍스트)
   ↓ DTO
Application Service (@Transactional, 유스케이스 단위, Outbox 이벤트 기록)
   ↓
Domain (엔티티 + 도메인 서비스: 상태머신, 모집자격 계산 등 비즈니스 규칙)
   ↓
Repository (Spring Data JPA)  +  QueryDao (JdbcClient, 조회 전용)
   ↓
Oracle
```

**규칙**

- 비즈니스 규칙(위촉 가능 여부, 모집자격 계산, 상태 전이)은 반드시 `insuhr-domain`의 도메인 서비스/엔티티 메서드에 위치. Controller/Application Service에는 규칙을 두지 않는다.
- 기준정보 변경이 발생하는 모든 Application Service 메서드는 **같은 트랜잭션 안에서** `TB_IF_OUTBOX`에 이벤트를 INSERT 한다(9장).
- 조회 전용 복잡 쿼리(현황판, 통계, 배치 Reader)는 `*QueryDao` 클래스에 `JdbcClient` + 네이티브 SQL로 작성하고, JPA 엔티티를 반환하지 않고 전용 record DTO를 반환한다.
- **QueryDao의 쓰기 예외 — 릴레이 팬아웃 (v1.8).** QueryDao는 원칙적으로 조회 전용이지만, 릴레이 팬아웃(9.2)의 `TB_IF_DELIVERY` 생성은 **`JdbcClient`의 `INSERT ... SELECT ... WHERE NOT EXISTS`(또는 MERGE) 한 문장**으로 쓴다. JPA로 짜면 `UQ(EVENT_ID, SUBSCRIBER_ID)` 위반이 영속성 컨텍스트를 rollback-only로 오염시켜(5.2·10.1.1의 Phase 2 함정) 같은 트랜잭션 후속 처리가 죽는다. `WHERE NOT EXISTS`는 위반을 애초에 일으키지 않고(UQ는 크래시 재실행 백스톱으로만 남는다), "구독자 × TOPIC_FILTER × 미존재" 필터가 SQL 한 방이라 성능도 낫다 — 인프라 계층의 벌크 쓰기라 이 예외를 둔다.

### 4.4 패키지 명명

- 루트 패키지: `com.portfolio.insuhr`
- 도메인 하위 예: `com.portfolio.insuhr.agent.domain`, `...agent.application`, `...agent.api`(controller/dto)

---

## 5. 도메인 모델 설계

### 5.1 서브도메인 맵

| 서브도메인 | 책임 | 핵심 엔티티 |
|---|---|---|
| org | 조직 계층(본사/영업), 조직 개편 이력 | Org, OrgHist |
| person | 인물 통합 마스터, 개인 신상, 식별정보 암호화 | Person, PersonContact |
| employee | 임직원 생애주기, 발령, 인사기록카드, 근태/휴가 | Employee, EmpAppointment, EmpEducation, EmpCertificate, EmpCareer, EmpFamily, EmpRewardPunish, LeaveRequest |
| agent | 설계사 위촉 생애주기, 위촉계약, 계보 | Agent, AgentAppointHist, AgentContract |
| license | 판매자격, 협회 등록, 교차모집 | AgentLicense, AssocRegistration, CrossSellReg |
| education(agent) | 설계사 법정교육 이수 | AgentEducation, EducationPolicy |
| guarantee | 재정보증 | FinGuarantee |
| sanction | 제재/징계, 불완전판매 | AgentSanction, MisSellCase |
| eligibility | 모집자격 종합 판정(계산 도메인) | RecruitEligibility (뷰성 엔티티 + 판정 서비스) |
| integration | Outbox, 변경로그, 구독시스템, 전송로그 | OutboxEvent, ChangeLog, Subscriber, SendLog |
| code / auth / audit | 공통코드, 사용자/역할/권한, 접근·변경 감사 | CodeGroup, Code, UserAccount, Role, PrivacyAccessLog |

### 5.2 인물–역할 모델 (핵심 설계 결정)

```
TB_PERSON (인물 마스터, 주민번호로 유일)
   │ 1
   ├──── 0..1  TB_EMP    (임직원 역할: 사번, 재직상태, 직원구분)
   │ 1
   └──── 0..1  TB_AGENT  (설계사 역할: 설계사코드, 위촉상태, 채널)
```

- 동일인이 직원 퇴사 후 설계사로 위촉되는 실무 케이스를 지원한다. 두 역할 레코드가 모두 존재할 수 있으며, 각 역할의 활성 기간은 겹칠 수도(임직원 겸직 규정은 정책 플래그로 통제) 있다.
- 주민등록번호는 `TB_PERSON`에만 암호화 저장. 타 테이블은 `PERSON_ID`(대리키)만 참조.
- 신규 등록 시 주민번호 해시(`RRN_HASH`)로 기존 인물 존재 여부를 검사하여 중복 인물 생성을 차단하고, 존재하면 역할만 추가한다.

**중복 방어선은 검사가 아니라 제약이다 (v1.2 — Phase 2 결정)**

`POST /persons/check-duplicate`(7.2)는 **UX용**이다. 실제 방어선은 `UQ_PERSON_RRN` 유니크 제약이며, 검사-후-삽입(check-then-insert)에 의존하면 동시 등록 레이스에서 중복 인물이 생긴다. 두 요청이 동시에 "없음"을 확인하고 둘 다 INSERT 하는 창이 열리기 때문이다.

따라서 등록 서비스는 INSERT를 시도하고, 유니크 제약 위반(ORA-00001 → `DataIntegrityViolationException`)을 잡아 **"기존 인물에 역할만 추가"** 흐름으로 전환한다. 이 전환은 예외 처리가 아니라 정상 경로의 일부다.

12장 시나리오 8번(동일 주민번호로 직원+설계사 이중 역할)은 **동시 요청 버전 테스트를 함께 둔다** — 순차 호출만으로는 이 레이스가 드러나지 않는다.

**제약 위반 복구는 반드시 독립 트랜잭션에서 (v1.3 — Phase 2 실증)**

JPA에서 제약 위반이 나면 영속성 컨텍스트가 오염되고 트랜잭션이 rollback-only로 표시된다. **같은 트랜잭션 안에서는 복구 조회(`findByRrnHash`)조차 할 수 없다** — 8개 스레드 동시 등록에서 7개가 실패했다. 따라서 INSERT 시도를 `REQUIRES_NEW`에 가두고, 실패하면 멀쩡한 상태의 호출부에서 기존 인물을 찾는다. `saveAndFlush`가 필수다 — 커밋까지 미루면 예외가 catch 블록이 아니라 트랜잭션 종료 시점에 터진다.

**결과적 성질**: 인물 행은 호출부 트랜잭션과 무관하게 커밋된다. 상위 유스케이스(입사, 후보등록)가 뒤에서 실패하면 역할 없는 인물이 남지만, 이는 손상 데이터가 아니라 **재사용 가능한 상태**다 — 같은 주민번호로 다시 시도하면 그 인물을 찾아 쓴다. 인물 등록이 주민번호 기준으로 멱등이기에 성립하는 절충이며, Phase 3·4가 이 성질에 의존한다.

**단, 위 정리는 정합성 관점에서만 참이다 (v1.4)**. 역할 없는 인물 행도 암호화된 주민번호를 쥔 개인정보이고 보유 목적이 없으므로, 유예기간 경과 시 `privacyPurgeJob`의 파기 대상이 된다 — 8장 참조. "재사용 가능"은 무기한 보유의 근거가 되지 못한다.

### 5.3 설계사 위촉 상태머신

```
 CANDIDATE(후보등록)
     │ 요건검증 통과(자격+등록교육+재정보증)
     ▼
 PENDING_ASSOC(협회등록 신청중)
     │ 협회 등록번호 수신
     ▼
 ACTIVE(위촉/모집가능) ◄─────────────┐
     │                              │ 정지사유 해소
     ├── 제재/교육미이수/보증만료 ──► SUSPENDED(모집정지)
     │                              │ 해촉 처리
     ├────────── 해촉 신청/당연해촉 ──┴──► TERMINATED(해촉)
     ▼                                        │ 재위촉(냉각기간 정책 검사)
 TERMINATED ──────────────────────────────────┘──► CANDIDATE(재위촉, 기존 AGENT_ID 유지 + 이력 추가)
```

**전이 규칙 (도메인 서비스 `AgentLifecycleService`에 구현)**

| 전이 | 사전조건 |
|---|---|
| CANDIDATE → PENDING_ASSOC | 유효한 판매자격 1개 이상 + 등록교육 이수 + 유효한 재정보증 존재 |
| PENDING_ASSOC → ACTIVE | 협회 등록번호 등록 완료 |
| ACTIVE → SUSPENDED | 제재 시작 / 보수교육 기한 도과 / 재정보증 만료·실효 (사유코드 필수) |
| SUSPENDED → ACTIVE | 정지 사유 전부 해소 (모집자격 판정 서비스가 PASS) |
| ACTIVE·SUSPENDED → TERMINATED | 해촉 사유코드 + 해촉일 필수. 협회 말소 이벤트 동반 |
| TERMINATED → CANDIDATE | 재위촉 제한기간(정책값, 기본 6개월) 경과 + 해촉사유가 재위촉 금지 사유(모집질서 문란 등)가 아닐 것 |

모든 전이는 `TB_AGENT_APPOINT_HIST`에 1행씩 기록되고 Outbox 이벤트(`agent.status.changed`)를 발행한다.

**구현 규약 (v1.5 — Phase 4 진입 결정)**

- **전이표가 코드의 단일 원천이다.** 허용 전이를 enum 기반 맵 하나(`AgentStatus`의 `EnumMap<AgentStatus, Set<AgentStatus>>`)로 선언하고, 각 전이 메서드(`appoint`/`registerAssoc`/`suspend`/`resume`/`terminate`/`reappoint`)는 그 맵으로 `from→to`를 검사한다. 단위테스트는 **5×5 전체 행렬**(상태쌍 25칸)을 순회하며 허용 7건·금지 18건을 맵과 대조한다 — 13.2 Phase 4의 "전이표 전 케이스"를 문자 그대로 이행하는 방법이다.
- **전이는 원자적 검사+쓰기다.** 두 담당자가 같은 설계사에게 동시에 정지·해촉을 걸면 사전조건 검사와 쓰기 사이의 틈에서 불법 전이가 성립할 수 있다. `TB_AGENT`에 `VERSION` 컬럼(낙관적 잠금)을 두어 충돌을 감지하고, 진 쪽은 409로 응답한다(8스레드 동시 전이 테스트로 검증 — Phase 2 패턴 재사용).
- **위촉(CANDIDATE→PENDING_ASSOC)의 요건검증은 판정 SPI로 분리한다.** 요건(유효 판매자격·등록교육·재정보증)은 Phase 5 테이블에서 오므로, Phase 4는 도메인 SPI `RecruitmentRequirementChecker`를 두고 전이 메서드가 이를 호출한다. Phase 4 스텁은 통과시키고(happy path 성립), Phase 5가 실판정으로 **서비스 코드 수정 없이** 교체한다(IntegrationRecorder와 같은 패턴). 이 때문에 12장 **시나리오 1이 1a/1b로 갈린다** — 1a(Phase 4)는 상태머신이 미충족 판정을 받으면 422+사유배열로 성형하는지, 1b(Phase 5)는 실제 자격·보증 데이터가 미충족 판정을 내는지.
- **SUSPENDED→ACTIVE(resume)는 Phase 4에서 판정 게이트 없이 수동으로 연다.** 정지 사유의 다중성(제재+교육미이수 동시)을 상태 컬럼 하나로 추적할 수 없어, 올바른 복귀 조건("사유 전부 해소")은 판정 서비스가 도출하는 Phase 5 소관이다. Phase 4는 수동 resume만 구현하고, 게이트 부재를 `@Disabled("Phase 5: eligibility 게이트")` 테스트로 남긴다(OrgService.close() 패턴).
- **재위촉(TERMINATED→CANDIDATE)의 스냅샷 의미론 — "마스터=현재 상태, 과거는 이력에만".** 같은 `AGENT_ID`를 재사용하므로 CANDIDATE 복귀 시 현재상태 컬럼을 정리한다: `TERMINATE_DT`·`TERMINATE_RSN_CD`는 `NULL`로(이력 `TB_AGENT_APPOINT_HIST`에는 남는다), `RECRUIT_ELIG_YN`은 `'N'`으로 리셋, `LAST_APPOINT_DT`는 재위촉 후 실제 위촉(appoint) 완료 시 갱신, `FIRST_APPOINT_DT`는 보존(최초 위촉일은 불변 사실). 이 원칙을 재위촉에서 처음 시험한다.
- **계보 순환 방어.** 도입자(`RECRUITER_AGENT_ID`) 지정 시 조상 체인에 자신이 있으면 A→B→A 순환이 되어 계보 조회 `CONNECT BY`가 `ORA-01436`으로 죽는다. 지정 시점에 조상 체인 검사로 차단하고, 조회 쿼리에도 `NOCYCLE`을 이중 방어로 넣는다(조직 이관의 `guardAgainstCycle`과 같은 발상).
- **AGENT_CD 채번**은 사번과 동일하게 시퀀스 기반 무의미 번호(`SEQ_AGENT_CD` → `A00000001`). 근거는 6.4의 EMP_NO 규칙과 같다.

### 5.4 모집자격(Eligibility) 판정 규칙

`RecruitEligibilityService.evaluate(agentId)` 는 아래 항목을 모두 검사해 종합 판정과 사유 목록을 반환한다. API 조회 시 실시간 계산 + 일배치로 `TB_AGENT.RECRUIT_ELIG_YN` 스냅샷 갱신의 이중 구조.

| 검사 항목 | PASS 조건 | 실패 시 효과 |
|---|---|---|
| 위촉상태 | STATUS = ACTIVE | 모집 불가 |
| 판매자격 | 해당 종목 자격 상태 = VALID | 해당 종목 모집 불가 |
| 협회등록 | 해당 종목 협회 등록 유효(말소 아님) | 해당 종목 모집 불가 |
| 보수교육 | 다음 이수기한(NEXT_DUE_DT) ≥ 오늘 | 전 종목 모집 정지 대상 |
| 변액자격 | 변액상품에 한해 변액 판매자격 VALID | 변액상품만 모집 불가 |
| 재정보증 | 유효기간 내 + 최소보증금액(정책값) 이상 | 신계약 모집 정지 대상 |
| 제재 | 진행 중인 모집정지 제재 없음 | 제재 범위에 따라 정지 |

판정 결과는 종목별로 산출한다: `{생보: OK, 손보: OK(교차), 변액: BLOCKED(자격없음)}`

**구현 규약 (v1.6 — Phase 5 진입 결정)**

- **판정과 집행을 분리한다.** `RecruitEligibilityService.evaluate(agentId, asOfDate)`는 **부수효과 없는 순수 함수** — 데이터·날짜가 같으면 결과가 같고 상태 전이를 절대 하지 않는다. 전이는 별도 조정자 `AgentEligibilityReconciler`가 판정을 받아 `AgentLifecycleService` 관문으로 수행한다(ACTIVE↔SUSPENDED 자동 전이). 이 분리로 Phase 7 배치가 판정 함수를 그대로 재사용하고, `asOfDate` 덕에 앵커 Clock 테스트(6.2)가 판정 경계에도 적용된다.
- **보수교육 null 기저선 (최대 함정).** 갓 위촉된 설계사는 보수교육 이수 행이 0건이라 `max(NEXT_DUE_DT)`가 null이다. null을 "요건 없음"으로 읽으면 영원히 통과, "미충족"으로 읽으면 위촉 직후 전원 정지된다. **기저선: 이수 이력이 없으면 기한 = `LAST_APPOINT_DT` + 주기(`CONT_EDU_CYCLE_MONTHS`), 이수가 생기면 그 이수의 `NEXT_DUE_DT`가 기저선을 대체.** 세 경우(무이력/기한내/도과)를 각각 테스트한다.
- **경계는 포함(inclusive)으로 통일.** 보수교육 `NEXT_DUE_DT >= 오늘`(기한 당일 통과), 제재 `START_DT <= 오늘 <= END_DT` 활성(양끝 포함, `END_DT` null = 무기한), 재정보증 `END_DT >= 오늘`(당일까지 유효). 각 경계일을 앵커 Clock으로 1건씩 테스트한다.
- **종합 `RECRUIT_ELIG_YN`의 정의.** 판정은 종목별이지만 스냅샷은 YN 하나다. **`Y` = 공통 게이트(위촉상태 ACTIVE · 보수교육 · 재정보증 · 모집정지 제재 없음) 전부 통과 AND 모집 가능 종목 ≥ 1.** 변액 자격만 없는 설계사가 `N`이 되면 안 된다 — 변액은 종목 게이트일 뿐 공통 게이트가 아니다.
- **복수 재정보증은 합산.** 유효 보증이 여러 건이면 `MIN_GRNT_AMT` 비교는 유효 보증금액의 **합산** 기준이다(실무 관행).
- **`RECRUIT_ELIG_YN`은 "마지막 계산 결과"다.** v1.5까지의 "배치 스냅샷"에서 의미를 승격한다(6.4 주석도 v1.6 정정). 실시간 판정 경로도 이 값을 갱신하므로, 자격·교육·보증·제재의 **모든 쓰기가 reconciler를 거쳐 재판정**하고, 종합 판정이 **실제로 바뀔 때만** `agent.eligibility.changed`를 발행한다(직전 값과 비교 — 쓰기마다 발행하지 않는다). 제재 등록 시 자동 SUSPENDED(7.2)도 별도 코드가 아니라 이 경로다.
- **재위촉과 요건의 신선도는 정책값.** 과거 이수한 등록교육이 재위촉 위촉요건으로 유효한지는 정답이 없어 `TB_POLICY_CONFIG.REG_EDU_REUSE_ON_REAPPOINT`(기본 `Y` — 포트폴리오 가정)로 두고 하드코딩하지 않는다.

**불변식: 워크플로 종료 시점에 스냅샷 YN = evaluate() 결과 (v1.6 — Phase 5 리뷰)**

- **자격 쓰기는 reconcile를 유발하고, 상태 전이는 유발하지 않는다.** reconciler가 전이를 유발하므로 전이가 다시 reconciler를 부르면 재진입 고리가 된다. 그래서 `AgentLifecycleService`의 전이 메서드는 판정을 부르지 않는다.
- **둘을 겸하는 워크플로는 끝에서 1회 명시 호출한다.** 협회 등록번호 수신(`registerAssociation`)은 ① 자격 데이터(`TB_ASSOC_REG`) 삽입 ② PENDING_ASSOC→ACTIVE 전이 ③ `reconcile` 1회를 **이 순서로** 한 트랜잭션에서 한다. 자격 삽입이 전이보다 먼저이므로 ③의 판정이 협회등록을 보고 통과하고, `agent.appointed`와 `agent.eligibility.changed(Y)`가 함께 나간다.
- **낡은 N 창구를 두지 않는다.** 워크플로가 끝나면 `RECRUIT_ELIG_YN`은 곧바로 `evaluate()` 결과와 일치해야 한다. 활성화 후 다음 자격 쓰기나 새벽 배치까지 스냅샷이 N으로 남으면, 부록 B 6번의 계약(영업시스템은 `agent.appointed`를 "모집 가능"으로 처리)이 깨지고 목록 필터·일배치 파일에서 갓 위촉된 설계사가 하루 동안 부적격으로 보인다.
- **역방향이 더 중요하다.** 위촉 요건검증과 협회번호 수신 사이에 며칠이 흘러 재정보증이 만료될 수 있다. 활성화 시 reconcile이 없으면 보증 만료 상태로 ACTIVE가 되어 다음 배치까지 모집 가능으로 남는다 — 컴플라이언스상 최악이다. 그 경우 활성화 직후 즉시 SUSPENDED가 되는 것은 버그가 아니라 **정답**이다.
- **수렴(재진입 안전)**: `reconcile`를 연속 두 번 불러도 두 번째는 no-op(중복 이벤트 없음)이어야 한다 — 종합 판정이 바뀔 때만 발행하는 규칙이 이를 보장하고, 테스트가 이를 못 박는다.

### 5.5 임직원 발령 모델

- 발령(`TB_EMP_APPOINT`)은 **이벤트 소싱형 이력**: 발령유형(입사/승진/전보/겸직/휴직/복직/파견/퇴직) + 발령일 + 발령 후 소속/직급/직책.
- `TB_EMP`의 현재 소속/직급/직책 컬럼은 최신 발령을 반영한 **비정규화 스냅샷**이며, 발령 확정 트랜잭션에서 함께 갱신한다.
- 발령은 기안(DRAFT) → 확정(CONFIRMED) → (필요시) 취소(CANCELED)의 문서 상태를 가지며, 확정 시에만 스냅샷 갱신과 Outbox 발행이 일어난다. 미래일자 발령은 확정 상태로 저장 후 발령일 새벽 배치가 스냅샷에 반영한다.

**스냅샷은 증분 적용이 아니라 재계산이다 (v1.4)**

`TB_EMP` 스냅샷을 "발령이 도래할 때마다 현재값에 덮어쓰는" 증분 갱신으로 짜면, 배치 재실행·중복 실행·같은 날 다중 발령이 각각 다른 버그를 만든다. 대신 스냅샷을 **기준일자의 함수**로 정의한다.

> `TB_EMP` 스냅샷(기준일 D) = 해당 직원의 발령 중 `DOC_STATUS_CD='CONFIRMED' AND APPOINT_DT <= D`인 것들을 **`APPOINT_DT DESC, APPOINT_ID DESC`로 정렬한 첫 행**

이 정의에서 따라 나오는 성질:

- **멱등이 공짜다.** 몇 번을 다시 계산해도 같은 결과다. 8장이 모든 잡에 요구하는 재실행 안전성을 배치 코드가 따로 챙길 필요가 없다.
- **같은 날 다중 발령이 결정적이다.** 동일 `APPOINT_DT`가 여럿이면 `APPOINT_ID`(채번 순서 = 기안 순서)가 승자를 정한다. 정렬 규칙 하나로 끝나므로 "나중 것이 이긴다"를 별도로 구현하지 않는다.
- **"반영됨"이 파생 값이다.** 별도 `APPLIED_YN` 플래그를 두지 않는다 — 플래그는 스냅샷과 어긋날 수 있는 두 번째 진실이 된다. 반영 여부는 `CONFIRMED && APPOINT_DT <= 오늘`로 판정하며, 배치가 아직 안 돈 시점에도 정의가 흔들리지 않는다(배치는 함수의 결과를 물질화할 뿐이다).

계산 자체는 도메인 서비스 `AppointmentApplyService`가 소유하고, Phase 7의 `futureAppointApplyJob`은 이 서비스를 **감싸기만 한다**(대상 추출 = 발령일이 도래한 CONFIRMED 발령의 `EMP_ID`, 처리 = 재계산). 반영 규칙의 위험(날짜 경계, 동일일자 순서, 취소와의 상호작용)은 배치 배관이 아니라 이 함수에 있으므로 테스트도 여기에 집중한다.

**취소 의미론 (v1.4)**

- **이미 반영된 발령(`APPOINT_DT <= 오늘`)은 CANCELED로 만들 수 없다** — 409. 반영된 과거를 지우면 이력과 스냅샷이 어긋나고, 그 발령을 근거로 나간 Outbox 이벤트가 수신계에 남은 채 원인만 사라진다.
- 되돌리려면 **정정 발령**(원래 값으로 되돌리는 새 발령)을 기안·확정한다. 이력은 "잘못 냈다가 되돌렸다"는 사실을 그대로 보존한다.
- 취소가 가능한 것은 ① DRAFT ② CONFIRMED이지만 발령일이 미래인 건(= 아직 스냅샷에 안 들어간 예약분)뿐이다.

---

## 6. 데이터베이스 설계 (Oracle)

### 6.1 명명 규칙

- 테이블: `TB_{도메인}_{명사}` 대문자 스네이크. 예) `TB_AGENT_LICENSE`
- PK: 단일 NUMBER 대리키 `{엔티티}_ID`, 시퀀스 `SQ_{테이블명}` 또는 `GENERATED AS IDENTITY` (본 프로젝트는 **IDENTITY** 사용)
- 업무키(자연키)는 UNIQUE 제약으로 별도 유지: `EMP_NO`, `AGENT_CD` 등
- 컬럼 접미사: `_CD`(공통코드값), `_YN`(CHAR(1) 'Y'/'N'), `_DT`(DATE, 일자), `_AT`(TIMESTAMP, 일시), `_AMT`(금액), `_ENC`(암호문), `_HASH`(검색용 해시)
- **`_CD` 컬럼 폭은 `VARCHAR2(30)` (v1.4 — Phase 3 실증)**. `TB_CD.CD`가 `VARCHAR2(30)`이므로 그 값을 참조하는 컬럼도 같은 폭이어야 한다. 초기 표기 `VARCHAR2(10)`은 `SALES_SUPPORT`(13)·`PENDING_ASSOC`(13)·`COMMENDATION`(12)·`HIGH_SCHOOL`(11) 등에서 이미 넘쳐 `ORA-12899`를 낸다(코드 삽입 시점까지 잠복). 6.4의 개별 DDL이 `VARCHAR2(10)`으로 보여도 이 규칙이 우선한다. 엔티티도 `@Column(length = 30)`으로 맞춘다. `EMP_NO` 같은 업무키는 이 규칙과 무관하다
- 인덱스: `IX_{테이블약어}_{컬럼}`, FK: `FK_{자식}_{부모}`. **같은 컬럼 목록에 UNIQUE 제약/인덱스가 이미 있으면 일반 인덱스를 또 만들지 않는다** — Oracle은 `ORA-01408`로 거부한다(유니크 인덱스가 조회 경로를 겸한다). (v1.4)

### 6.2 공통(감사) 컬럼 — 모든 업무 테이블에 포함

```sql
CREATED_AT   TIMESTAMP    DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
CREATED_BY   VARCHAR2(50) NOT NULL,
UPDATED_AT   TIMESTAMP,
UPDATED_BY   VARCHAR2(50)
```

JPA에서는 `@MappedSuperclass BaseEntity` + Auditing으로 자동 세팅.

**시각 규약 (v1.2) — 모든 TIMESTAMP 컬럼은 UTC 기준으로 적재한다. 표시 변환은 애플리케이션 책임이다.**

- DDL 기본값에 `SYSTIMESTAMP`를 **직접 쓰지 않는다**. `SYSTIMESTAMP`는 **DB 호스트 타임존**을 따르므로, DB가 Asia/Seoul로 도는 순간 시드·직접 INSERT 행만 KST가 되고 앱 기록 행(UTC)과 9시간 어긋난다. 반드시 `SYS_EXTRACT_UTC(SYSTIMESTAMP)`를 쓴다.
- 애플리케이션은 `hibernate.jdbc.time_zone=UTC` + `preferred_instant_jdbc_type=TIMESTAMP`로 UTC를 적재한다(3.0).
- API 응답의 `+09:00` 변환(7.1)은 표현 계층에서 한다.
- **왜 규약으로 박는가**: 로컬 개발 컨테이너(gvenzl)가 UTC라 두 기준이 어긋나도 **증상이 안 보인다**. 날짜 경계 판정이 많은 배치(8장 — 교육 이수기한, 보증 만기, 미래발령 적용)에서 하루 어긋나는 버그로 뒤늦게 드러난다. `TimestampConventionTest`가 DDL 자체를 검사해 규약 위반을 막는다.

**시각과 날짜의 파생 규칙 (v1.5 — Phase 3 실증, `Clock` 빈에서 두 종류가 갈린다)**

주입된 `Clock`(`ClockConfig`, 존=업무존 KST) 하나에서 저장 시각과 업무 날짜 두 종류가 파생된다. 어느 쪽인지 모호해지는 순간 위 UTC 규약과 KST 업무존이 충돌하므로, 파생 방법을 셋으로 고정한다:

| 용도 | 파생 | 존 영향 |
|---|---|---|
| 저장 시각 (`*_AT`, `Instant`) | `Instant.now(clock)` | **없음** — Instant는 절대시각이라 존과 무관. UTC 적재 규약(위)이 그대로 성립한다 |
| 업무 날짜 (`*_DT`, `LocalDate`) | `LocalDate.now(clock)` | **KST** — 한국 영업일 달력 위의 "그 날". 발령일·입사일·기한 판정이 이걸 쓴다 |
| ~~`LocalDateTime.now(clock)`~~ | **금지** | 저장 시각인지 업무 날짜인지 모호해진다. 저장이면 `Instant`, 날짜면 `LocalDate`로 명시 |

- **왜 `LocalDate`는 KST여야 하는가**: UTC로 뽑으면 한국시간 **00:00~09:00 사이의 "오늘"이 전날로 계산된다**. 새벽 00:10에 도는 `futureAppointApplyJob`(8장)이 정확히 그 구간에서 돌아, UTC 존이면 발령일 도래분을 하루 늦게 반영한다. 그래서 `ClockConfig.BUSINESS_ZONE = Asia/Seoul`.
- **왜 `Instant`는 존 무관인가**: `Instant.now(clock)`은 clock의 존과 상관없이 같은 절대시각이다. 업무존을 KST로 둬도 저장되는 `*_AT`는 여전히 UTC로 적재된다(hibernate `time_zone=UTC`). 두 규약이 충돌하지 않는 이유가 이것이다.
- **규약의 앵커 테스트**: `Clock`을 `2026-08-01T00:05+09:00`(= UTC로는 아직 `2026-07-31T15:05Z`)에 고정하고, `LocalDate.now(clock)`이 8월 1일이며 그 시점 8월 1일자 발령이 반영됨을 단언한다. Phase 5의 기한 판정(`NEXT_DUE_DT >= 오늘`)과 Phase 7 새벽 배치가 전부 이 경계를 밟으므로, 이 테스트가 규약이 살아 있음을 지키는 파수꾼이다.

### 6.3 테이블 목록 (총 38개)

| 영역 | 테이블 | 설명 |
|---|---|---|
| 조직 | TB_ORG | 조직 마스터 (본사부서/지역단/지점/영업소 통합 계층) |
| | TB_ORG_HIST | 조직 개편 이력 (유효기간형) |
| 인물 | TB_PERSON | 인물 마스터 (주민번호 암호화 + 해시) |
| | TB_PERSON_ADDR | 주소 이력 |
| 임직원 | TB_EMP | 임직원 마스터 (현재 스냅샷) |
| | TB_EMP_APPOINT | 인사발령 이력 |
| | TB_EMP_SCHOOL / TB_EMP_CAREER / TB_EMP_FAMILY | 인사기록카드: 학력/경력/가족 |
| | TB_EMP_CERT | 직원 보유 자격증 |
| | TB_EMP_EDU | 직원 교육 이수 |
| | TB_EMP_REWARD_PUNISH | 상벌 |
| | TB_LEAVE_GRANT / TB_LEAVE_REQ | 연차 부여 / 휴가 신청 |
| 설계사 | TB_AGENT | 설계사 마스터 (현재 스냅샷 + 모집자격 캐시) |
| | TB_AGENT_APPOINT_HIST | 위촉상태 전이 이력 |
| | TB_AGENT_CONTRACT | 위촉계약 (계약유형, 수수료규정 버전, 지급계좌 암호화) |
| | TB_AGENT_LICENSE | 판매자격 (종목별) |
| | TB_ASSOC_REG | 협회 등록/말소 이력 |
| | TB_CROSS_SELL_REG | 교차모집 등록 |
| | TB_AGENT_EDU | 설계사 교육 이수 이력 |
| | TB_EDU_POLICY | 교육 정책 (교육유형별 주기/이수시간/필수여부) |
| | TB_FIN_GUARANTEE | 재정보증 |
| | TB_AGENT_SANCTION | 제재/징계 |
| | TB_MISSELL_CASE | 불완전판매 건 |
| 연계 | TB_IF_OUTBOX | 트랜잭셔널 아웃박스 |
| | TB_IF_CHANGE_LOG | 변경분 Pull API용 변경 로그 |
| | TB_IF_SUBSCRIBER | 구독(연계 대상) 시스템 |
| | TB_IF_SEND_LOG | 전송 이력 (webhook/kafka) |
| | TB_IF_FILE_LOG | 배치 파일 생성/전달 이력 |
| 공통 | TB_CD_GRP / TB_CD | 공통코드 그룹/코드 |
| | TB_POLICY_CONFIG | 정책값 (보수교육 주기, 최소보증금액, 재위촉 제한기간, 보존기간 등) |
| | TB_USER / TB_ROLE / TB_USER_ROLE / TB_ROLE_PERM | 계정/역할/권한 |
| | TB_AUTH_REFRESH_TOKEN | Refresh 토큰 (v1.1 추가 — 10.1의 "DB 저장·회전" 요건에 저장소가 필요한데 목록에 없었음). 토큰 원문이 아니라 SHA-256 해시를 저장 |
| | TB_PRIVACY_ACCESS_LOG | 개인정보 조회 접근로그 |
| | TB_AUDIT_LOG | 주요 테이블 변경 감사로그 (JSON diff) |

### 6.4 핵심 테이블 DDL

> 아래 DDL은 Flyway `V2__core_tables.sql` 이후 파일들의 원본이 된다. 나머지 테이블은 6.5의 컬럼 스펙에 따라 동일 규칙으로 생성한다.

```sql
-- 조직
CREATE TABLE TB_ORG (
    ORG_ID        NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    ORG_CD        VARCHAR2(10)  NOT NULL,          -- 업무키 (예: A0001)
    ORG_NM        VARCHAR2(100) NOT NULL,
    ORG_TYPE_CD   VARCHAR2(10)  NOT NULL,          -- CD:ORG_TYPE (HQ_DEPT/REGION/BRANCH/OFFICE)
    UP_ORG_ID     NUMBER,                          -- 자기참조
    ORG_LVL       NUMBER(2)     NOT NULL,
    SORT_ORD      NUMBER(5)     DEFAULT 0 NOT NULL,
    VALID_FROM_DT DATE          NOT NULL,
    VALID_TO_DT   DATE          DEFAULT DATE '9999-12-31' NOT NULL,
    USE_YN        CHAR(1)       DEFAULT 'Y' NOT NULL,
    CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    CONSTRAINT UQ_ORG_CD UNIQUE (ORG_CD),
    CONSTRAINT FK_ORG_UP FOREIGN KEY (UP_ORG_ID) REFERENCES TB_ORG(ORG_ID)
);
CREATE INDEX IX_ORG_UP ON TB_ORG(UP_ORG_ID);

-- 인물 마스터
CREATE TABLE TB_PERSON (
    PERSON_ID     NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    PERSON_NM     VARCHAR2(100) NOT NULL,
    RRN_ENC       VARCHAR2(512) NOT NULL,          -- 주민등록번호 AES-256-GCM 암호문(Base64)
    RRN_HASH      VARCHAR2(64)  NOT NULL,          -- SHA-256(주민번호+pepper), 중복검사/검색용
    BIRTH_DT      DATE          NOT NULL,
    GENDER_CD     VARCHAR2(10)  NOT NULL,          -- CD:GENDER
    MOBILE_ENC    VARCHAR2(256),
    EMAIL         VARCHAR2(100),
    NATIONALITY_CD VARCHAR2(10) DEFAULT 'KR' NOT NULL,
    CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    CONSTRAINT UQ_PERSON_RRN UNIQUE (RRN_HASH)
);

-- 임직원
CREATE TABLE TB_EMP (
    EMP_ID        NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    PERSON_ID     NUMBER        NOT NULL,
    EMP_NO        VARCHAR2(10)  NOT NULL,          -- 사번
    EMP_TYPE_CD   VARCHAR2(10)  NOT NULL,          -- CD:EMP_TYPE (REGULAR/CONTRACT/SALES_SUPPORT)
    ORG_ID        NUMBER        NOT NULL,          -- 현재 소속 (발령 스냅샷)
    JOB_GRADE_CD  VARCHAR2(10),                    -- 직급
    JOB_TITLE_CD  VARCHAR2(10),                    -- 직책
    HIRE_DT       DATE          NOT NULL,
    RESIGN_DT     DATE,
    EMP_STATUS_CD VARCHAR2(10)  NOT NULL,          -- CD:EMP_STATUS (ACTIVE/ON_LEAVE/RESIGNED)
    CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    CONSTRAINT UQ_EMP_NO UNIQUE (EMP_NO),
    CONSTRAINT FK_EMP_PERSON FOREIGN KEY (PERSON_ID) REFERENCES TB_PERSON(PERSON_ID),
    CONSTRAINT FK_EMP_ORG FOREIGN KEY (ORG_ID) REFERENCES TB_ORG(ORG_ID)
);
CREATE INDEX IX_EMP_ORG ON TB_EMP(ORG_ID);
-- 인물 1명당 임직원 역할은 0..1 (5.2). 유니크 인덱스가 카디널리티 방어선이자 PERSON_ID 조회 경로다.
-- 같은 컬럼에 일반 인덱스를 또 만들면 ORA-01408이므로 IX_EMP_PERSON은 두지 않는다. (v1.4)
CREATE UNIQUE INDEX UX_EMP_PERSON ON TB_EMP(PERSON_ID);

-- 사번 채번 (v1.4)
CREATE SEQUENCE SEQ_EMP_NO START WITH 1 INCREMENT BY 1 NOCYCLE;

-- 설계사
CREATE TABLE TB_AGENT (
    AGENT_ID          NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    PERSON_ID         NUMBER        NOT NULL,
    AGENT_CD          VARCHAR2(10)  NOT NULL,      -- 설계사코드
    CHANNEL_CD        VARCHAR2(10)  NOT NULL,      -- CD:CHANNEL (FC/TC/GA/BANCA/DM)
    ORG_ID            NUMBER        NOT NULL,      -- 소속 영업조직(지점/영업소)
    AGENT_STATUS_CD   VARCHAR2(10)  NOT NULL,      -- CD:AGENT_STATUS (5.3 상태머신)
    FIRST_APPOINT_DT  DATE,                        -- 최초 위촉일
    LAST_APPOINT_DT   DATE,                        -- 최근(재)위촉일
    TERMINATE_DT      DATE,
    TERMINATE_RSN_CD  VARCHAR2(10),                -- CD:TERM_RSN
    RECRUITER_AGENT_ID NUMBER,                     -- 도입자 (자기참조)
    RECRUIT_ELIG_YN   CHAR(1) DEFAULT 'N' NOT NULL, -- 모집자격 마지막 계산 결과 (v1.6 — 실시간·배치 둘 다 갱신, 5.4)
    ELIG_CHECKED_AT   TIMESTAMP,                     -- 마지막 판정 시각
    VERSION           NUMBER(19) DEFAULT 0 NOT NULL, -- 낙관적 잠금 (v1.5, 5.3 동시 전이 방어)
    CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    CONSTRAINT UQ_AGENT_CD UNIQUE (AGENT_CD),
    CONSTRAINT FK_AGENT_PERSON FOREIGN KEY (PERSON_ID) REFERENCES TB_PERSON(PERSON_ID),
    CONSTRAINT FK_AGENT_ORG FOREIGN KEY (ORG_ID) REFERENCES TB_ORG(ORG_ID),
    CONSTRAINT FK_AGENT_RECRUITER FOREIGN KEY (RECRUITER_AGENT_ID) REFERENCES TB_AGENT(AGENT_ID)
);
-- 인물 1명당 설계사 역할도 0..1 (5.2). EMP와 대칭 — 유니크 인덱스가 카디널리티 방어선이자 조회 경로.
CREATE UNIQUE INDEX UX_AGENT_PERSON ON TB_AGENT(PERSON_ID);
CREATE INDEX IX_AGENT_ORG_STATUS ON TB_AGENT(ORG_ID, AGENT_STATUS_CD);

-- 설계사코드 채번 (v1.5). EMP_NO와 같은 규칙: 무의미 번호, MAX+1 금지, 갭 허용. 포맷 A + 8자리 0패딩
CREATE SEQUENCE SEQ_AGENT_CD START WITH 1 INCREMENT BY 1 NOCYCLE;

-- 판매자격
CREATE TABLE TB_AGENT_LICENSE (
    LICENSE_ID     NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    AGENT_ID       NUMBER       NOT NULL,
    LICENSE_TYPE_CD VARCHAR2(10) NOT NULL,         -- CD:LICENSE_TYPE (LIFE/NONLIFE/THIRD/VARIABLE)
    LICENSE_NO     VARCHAR2(30),
    EXAM_PASS_DT   DATE,
    REG_DT         DATE,                           -- 자격 등록일
    STATUS_CD      VARCHAR2(10) NOT NULL,          -- CD:LICENSE_STATUS (VALID/SUSPENDED/REVOKED)
    CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    CONSTRAINT FK_LIC_AGENT FOREIGN KEY (AGENT_ID) REFERENCES TB_AGENT(AGENT_ID),
    CONSTRAINT UQ_LIC UNIQUE (AGENT_ID, LICENSE_TYPE_CD)
);

-- 설계사 교육
CREATE TABLE TB_AGENT_EDU (
    AGENT_EDU_ID  NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    AGENT_ID      NUMBER       NOT NULL,
    EDU_TYPE_CD   VARCHAR2(10) NOT NULL,           -- CD:EDU_TYPE (REG/CONTINUING/VARIABLE/COMPLIANCE)
    EDU_NM        VARCHAR2(200) NOT NULL,
    COMPLETE_DT   DATE         NOT NULL,
    EDU_HOURS     NUMBER(5,1)  NOT NULL,
    PROVIDER_NM   VARCHAR2(100),
    NEXT_DUE_DT   DATE,                            -- 다음 이수기한 (정책: 이수일 + 주기)
    CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    CONSTRAINT FK_AEDU_AGENT FOREIGN KEY (AGENT_ID) REFERENCES TB_AGENT(AGENT_ID)
);
CREATE INDEX IX_AEDU_DUE ON TB_AGENT_EDU(EDU_TYPE_CD, NEXT_DUE_DT);

-- 재정보증
CREATE TABLE TB_FIN_GUARANTEE (
    GRNT_ID       NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    AGENT_ID      NUMBER        NOT NULL,
    GRNT_TYPE_CD  VARCHAR2(10)  NOT NULL,          -- CD:GRNT_TYPE (SURETY_INS/DEPOSIT/PERSONAL)
    GRNT_AMT      NUMBER(15)    NOT NULL,
    ISSUER_NM     VARCHAR2(100),                   -- 보증기관(보증보험사 등)
    POLICY_NO     VARCHAR2(50),                    -- 증권번호
    START_DT      DATE          NOT NULL,
    END_DT        DATE          NOT NULL,
    STATUS_CD     VARCHAR2(10)  NOT NULL,          -- CD:GRNT_STATUS (ACTIVE/EXPIRED/CANCELED)
    CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    CONSTRAINT FK_GRNT_AGENT FOREIGN KEY (AGENT_ID) REFERENCES TB_AGENT(AGENT_ID)
);
CREATE INDEX IX_GRNT_END ON TB_FIN_GUARANTEE(STATUS_CD, END_DT);

-- 트랜잭셔널 아웃박스
CREATE TABLE TB_IF_OUTBOX (
    EVENT_ID      NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    EVENT_UUID    VARCHAR2(36)  NOT NULL,          -- 멱등키
    AGG_TYPE      VARCHAR2(30)  NOT NULL,          -- PERSON/EMP/AGENT/ORG ...
    AGG_ID        NUMBER        NOT NULL,
    EVENT_TYPE    VARCHAR2(60)  NOT NULL,          -- agent.status.changed 등 (9.3)
    PAYLOAD       CLOB          NOT NULL CHECK (PAYLOAD IS JSON),
    STATUS_CD     VARCHAR2(10)  DEFAULT 'READY' NOT NULL, -- READY/SENT/FAILED/SKIPPED
    RETRY_CNT     NUMBER(3)     DEFAULT 0 NOT NULL,
    OCCURRED_AT   TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    PUBLISHED_AT  TIMESTAMP,
    CONSTRAINT UQ_OUTBOX_UUID UNIQUE (EVENT_UUID)
);
CREATE INDEX IX_OUTBOX_STATUS ON TB_IF_OUTBOX(STATUS_CD, EVENT_ID);
```

**사번(`EMP_NO`) 채번 규칙 (v1.4)**

- **포맷: `E` + 8자리 0패딩 시퀀스** (`E00000001`). 연도·조직·직군 등 **의미를 넣지 않는다**.
- **의미 없는 번호인 이유**: 연도 프리픽스를 넣으면 소급 입사·재입사에서 사번의 연도와 실제 `HIRE_DT`가 어긋난다. 더 나쁜 것은 그 다음이다 — 사번에 연도가 있으면 근속·연차를 사번에서 추정하는 코드가 반드시 생기고, `HIRE_DT`라는 정답이 있는데도 파생 데이터를 두 벌 갖게 된다. 조회 조건이 필요하면 `HIRE_DT` 컬럼을 쓴다.
- **채번은 Oracle 시퀀스로.** `SELECT MAX(EMP_NO)+1` 방식은 두 요청이 같은 최대값을 읽는 창이 있어 동시 입사에서 충돌한다(`UQ_EMP_NO` 위반 → 입사 실패). 시퀀스는 트랜잭션 밖에서 원자적으로 증가하므로 이 창이 없다.
- **번호 갭은 허용한다.** 시퀀스는 롤백·캐시 소실 시 번호를 건너뛴다. 사번이 무의미 번호이므로 갭은 문제가 아니며, 갭을 없애려는 순간 위의 MAX+1 문제로 돌아간다.

### 6.5 나머지 테이블 컬럼 스펙 (요약)

| 테이블 | 주요 컬럼 (PK/감사컬럼 생략) |
|---|---|
| TB_ORG_HIST | ORG_ID(FK), CHANGE_TYPE_CD(신설/명칭변경/이관/폐지), BEFORE_JSON(CLOB), AFTER_JSON(CLOB), EFFECTIVE_DT |
| TB_PERSON_ADDR | PERSON_ID(FK), ADDR_TYPE_CD(자택/직장), ZIP_CD, ADDR_ENC, VALID_FROM_DT, VALID_TO_DT |
| TB_EMP_APPOINT | EMP_ID(FK), APPOINT_TYPE_CD, APPOINT_DT, ORG_ID, JOB_GRADE_CD, JOB_TITLE_CD, DOC_STATUS_CD(DRAFT/CONFIRMED/CANCELED), RSN_TXT |
| TB_EMP_SCHOOL | EMP_ID, SCHOOL_NM, MAJOR_NM, DEGREE_CD, GRAD_DT, GRAD_STATUS_CD |
| TB_EMP_CAREER | EMP_ID, COMPANY_NM, DEPT_NM, POSITION_NM, JOIN_DT, LEAVE_DT, JOB_DESC |
| TB_EMP_FAMILY | EMP_ID, RELATION_CD, FAMILY_NM, BIRTH_DT, COHABIT_YN |
| TB_EMP_CERT | EMP_ID, CERT_NM, ISSUER_NM, CERT_NO, ACQUIRE_DT, EXPIRE_DT |
| TB_EMP_EDU | EMP_ID, EDU_NM, EDU_TYPE_CD, START_DT, END_DT, EDU_HOURS, RESULT_CD |
| TB_EMP_REWARD_PUNISH | EMP_ID, RP_TYPE_CD(포상/징계), RP_CD, RP_DT, RSN_TXT |
| TB_LEAVE_GRANT | EMP_ID, YEAR_NO, GRANT_DAYS, USED_DAYS, EXPIRE_DT |
| TB_LEAVE_REQ | EMP_ID, LEAVE_TYPE_CD, START_DT, END_DT, DAYS, STATUS_CD(신청/승인/반려/취소), APPROVER_EMP_ID |
| TB_AGENT_APPOINT_HIST | AGENT_ID(FK), FROM_STATUS_CD, TO_STATUS_CD, EVENT_DT, RSN_CD, RSN_TXT, ORG_ID(당시 소속) |
| TB_AGENT_CONTRACT | AGENT_ID, CONTRACT_TYPE_CD, CONTRACT_DT, COMM_RULE_VER(수수료규정 버전), BANK_CD, ACCOUNT_ENC(지급계좌 암호화), ACCOUNT_HOLDER_NM, VALID_FROM_DT, VALID_TO_DT |
| TB_ASSOC_REG | AGENT_ID, ASSOC_CD(LIFE_ASSOC/NONLIFE_ASSOC), ASSOC_REG_NO, REG_DT, DEREG_DT, DEREG_RSN_CD, STATUS_CD |
| TB_CROSS_SELL_REG | AGENT_ID, TARGET_LINE_CD(교차 종목), PARTNER_COMPANY_NM, REG_DT, DEREG_DT, STATUS_CD |
| TB_EDU_POLICY | EDU_TYPE_CD, CYCLE_MONTHS(주기, 보수교육 기본 24), MIN_HOURS, MANDATORY_YN, APPLY_CHANNEL_CD, VALID_FROM_DT |
| TB_AGENT_SANCTION | AGENT_ID, SANCTION_ORG_CD(금융당국/협회/사내), SANCTION_TYPE_CD(모집정지/경고/과태료 등), START_DT, END_DT, RSN_TXT, RECRUIT_BLOCK_YN |
| TB_MISSELL_CASE | AGENT_ID, CONTRACT_NO(원계약 참조번호), CASE_TYPE_CD, OCCUR_DT, RESOLVE_DT, RESULT_CD, LINKED_SANCTION_ID |
| TB_IF_CHANGE_LOG | AGG_TYPE, AGG_ID, CHANGE_TYPE_CD(C/U/D), CHANGED_AT, SEQ_NO(전역 시퀀스), SNAPSHOT_JSON(CLOB) — Pull API의 커서 대상 |
| TB_IF_SUBSCRIBER | SYSTEM_CD, SYSTEM_NM, DELIVERY_TYPE_CD(WEBHOOK/KAFKA/FILE/PULL), ENDPOINT_URL, SECRET_ENC, TOPIC_FILTER(관심 이벤트 목록 JSON), USE_YN |
| TB_IF_SEND_LOG | EVENT_ID(FK), SYSTEM_CD, SEND_AT, HTTP_STATUS, RESULT_CD, RESP_BODY(CLOB), RETRY_NO |
| TB_IF_FILE_LOG | FILE_TYPE_CD(FULL/DELTA), TARGET_DT, FILE_PATH, ROW_CNT, CHECKSUM, STATUS_CD |
| TB_CD_GRP / TB_CD | GRP_CD, GRP_NM / GRP_CD, CD, CD_NM, SORT_ORD, USE_YN, ATTR1~3 |
| TB_POLICY_CONFIG | POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, VALID_FROM_DT — 예: CONT_EDU_CYCLE_MONTHS=24, MIN_GRNT_AMT=..., REAPPOINT_COOLDOWN_MONTHS=6, PRIVACY_RETENTION_YEARS=... |
| TB_USER | LOGIN_ID, PWD_HASH(BCrypt), PERSON_ID(FK, 임직원/설계사 계정), USER_TYPE_CD(HUMAN/SYSTEM), STATUS_CD, LAST_LOGIN_AT, PWD_CHANGED_AT, LOGIN_FAIL_CNT·LOCKED_AT(v1.1 — 10.1의 5회 실패 잠금에 필요) |
| TB_AUTH_REFRESH_TOKEN | USER_ID(FK), TOKEN_HASH(SHA-256, UNIQUE), ISSUED_AT, EXPIRES_AT, REVOKED_AT |
| TB_ROLE / TB_USER_ROLE / TB_ROLE_PERM | ROLE_CD, ROLE_NM / USER_ID, ROLE_CD / ROLE_CD, PERM_CD(리소스.행위, 예: agent.read, agent.rrn.decrypt) |
| TB_PRIVACY_ACCESS_LOG | USER_ID, TARGET_PERSON_ID, ACCESS_TYPE_CD(VIEW/DECRYPT/EXPORT), ACCESS_AT, MENU_OR_API, CLIENT_IP, PURPOSE_TXT. **TARGET_PERSON_ID의 FK는 "PERSON 행이 물리 삭제되지 않는다"는 전제에 의존한다** — `privacyPurgeJob`(8장)이 삭제가 아니라 익명화(행 유지)이기 때문에 성립한다. 파기 방식을 하드 삭제로 바꾸면 이 FK가 파기를 막는다 (v1.2) |
| TB_AUDIT_LOG | TABLE_NM, PK_VAL, ACTION_CD(C/U/D), BEFORE_JSON, AFTER_JSON, ACTED_AT, ACTED_BY |

### 6.6 이력 관리 전략

| 유형 | 대상 | 방식 |
|---|---|---|
| 유효기간형 | 조직, 위촉계약, 주소 | `VALID_FROM_DT ~ VALID_TO_DT` 컬럼. 현재행 = TO가 9999-12-31 |
| 이벤트형 | 발령, 위촉상태 전이, 협회 등록/말소 | 사건 1건 = 1행 append-only, 수정 대신 취소행 추가 |
| 감사형 | PERSON, AGENT, EMP 등 마스터 | Application 계층 AOP로 before/after JSON을 `TB_AUDIT_LOG`에 기록 (트리거 미사용 — 테스트 용이성) |

**이력 행은 항상 변경 후 전체 스냅샷을 담는다 (v1.2 확정 — Phase 2 전제)**

`TB_ORG_HIST.AFTER_JSON`(및 동종 이력 테이블)은 변경된 필드만이 아니라 **변경 후 해당 엔티티의 전체 상태**를 담는다. `BEFORE_JSON`도 마찬가지로 변경 전 전체 상태다.

이유는 기준일자 시점 조회(7.2 `GET /orgs/tree?asOfDate=`) 때문이다. 이력이 diff라면 기준일 트리를 복원하려고 최초 생성부터 diff를 순서대로 재생해야 하는데, 이는 구현·성능 양쪽에서 감당하기 어렵다. 전체 스냅샷이면 시점 조회가 다음 한 줄로 끝난다.

> 조직별로 `EFFECTIVE_DT <= :asOfDate` 인 이력 중 가장 최신 1건을 골라(`ROW_NUMBER()` 또는 `FETCH FIRST`) `AFTER_JSON`에서 필요한 필드를 꺼내 트리를 구성한다. Oracle 23ai면 `JSON_VALUE`로 충분하다.

이는 9.4의 Pull API가 `snapshot`(state-carried transfer)을 싣는 것과 같은 원리다 — 수신측이 순서 꼬임 없이 upsert할 수 있는 이유가 그것이다. 이력·이벤트 페이로드는 **일관되게 전체 상태**를 담는다.

### 6.7 인덱스·파티션·성능 전략

- 대량 이력 테이블(`TB_IF_CHANGE_LOG`, `TB_IF_SEND_LOG`, `TB_PRIVACY_ACCESS_LOG`, `TB_AUDIT_LOG`)은 월 단위 **인터벌 레인지 파티션** (`PARTITION BY RANGE (…_AT) INTERVAL (NUMTOYMINTERVAL(1,'MONTH'))`) 적용. 로컬 개발에서는 파티션 없이도 동작하도록 Flyway 스크립트를 분리(`V90__partitioning.sql`, 프로파일성 적용).
- 조회 패턴 기반 복합 인덱스: `(ORG_ID, AGENT_STATUS_CD)`, `(EDU_TYPE_CD, NEXT_DUE_DT)`, `(STATUS_CD, END_DT)` 등 배치 Reader가 풀스캔하지 않도록 설계.
- 페이징은 Oracle 12c+ `OFFSET … FETCH NEXT` 사용. 대용량 커서 조회(Pull API)는 `SEQ_NO > :cursor ORDER BY SEQ_NO FETCH NEXT :n ROWS ONLY`의 키셋 페이징.

### 6.8 암호화 컬럼 정리

| 컬럼 | 방식 | 조회 |
|---|---|---|
| TB_PERSON.RRN_ENC | AES-256-GCM (앱 레벨), 키는 환경변수/KMS | `agent.rrn.decrypt` 권한자만 복호화 API, 접근로그 필수 |
| TB_PERSON.RRN_HASH | SHA-256 + pepper | 동일인 검사, 완전일치 검색 |
| TB_PERSON.MOBILE_ENC / TB_PERSON_ADDR.ADDR_ENC | AES-256-GCM | 기본 마스킹 표시 (010-****-1234) |
| TB_AGENT_CONTRACT.ACCOUNT_ENC | AES-256-GCM | 수수료시스템 연계 시에만 복호화 |
| TB_IF_SUBSCRIBER.SECRET_ENC | AES-256-GCM | 릴레이 서명용 |

운영 환경에서는 Oracle TDE(테이블스페이스 암호화)를 병행한다고 가정하되, 애플리케이션 레벨 암호화가 1차 통제.

---

## 7. API 설계 (REST)

### 7.1 공통 규약

- Base URL: `/api/v1`
- 인증: `Authorization: Bearer {JWT}` (로그인 제외 전 API). 시스템 계정은 별도 `client_credentials`형 토큰 발급 API 사용
- 표준 응답 envelope:

```json
{ "success": true, "data": { }, "error": null, "traceId": "..." }
{ "success": false, "data": null,
  "error": { "code": "AGT-4003", "message": "보수교육 미이수로 위촉할 수 없습니다.",
             "details": [ {"field": "continuingEducation", "reason": "NEXT_DUE_DT_PASSED"} ] },
  "traceId": "..." }
```

- 에러코드 체계: `{도메인 3자}-{HTTP류}{일련2자}` — COM(공통), ORG, PER, EMP, AGT, LIC, EDU, GRT, SNC, IFC(연계), AUT(인증)
- 페이징: `?page=0&size=20&sort=field,desc` → 응답 `data.content[] + page{number,size,totalElements,totalPages}`
- 목록 조회의 개인식별정보는 항상 마스킹 값으로 반환. 원문은 별도 복호화 엔드포인트에서만.
- 날짜는 `yyyy-MM-dd`, 일시는 ISO-8601(`+09:00` 포함)
- **민감정보 복호화 계열은 전부 `POST` + 사유 본문 (v1.4)**. 조회 의미상 GET이 자연스럽지만 GET을 쓰면 **열람 사유가 쿼리스트링에 실려 URL로 샌다** — 액세스로그·프록시 로그·브라우저 히스토리·Referer에 남고, 그중 어느 것도 `TB_PRIVACY_ACCESS_LOG` 같은 통제를 받지 않는다. 사유를 본문에 담으면 이 경로가 전부 닫히고, GET이 아니므로 캐시·프리페치·링크 크롤링에도 걸리지 않는다(부수 효과지만 개인정보에선 중요하다). 적용 대상: 주민번호(`POST /persons/{personId}/rrn`), 지급계좌(Phase 5), 이후 추가되는 모든 `*_ENC` 복호화 API.

### 7.2 엔드포인트 목록

**인증 (AUT)**

| Method | Path | 설명 |
|---|---|---|
| POST | /auth/login | 로그인 → Access(30분)/Refresh(14일) 발급 |
| POST | /auth/refresh | 토큰 재발급 (회전 — 10.1) |
| POST | /auth/system-token | 시스템 계정 토큰 발급 (연계용) |
| PUT | /auth/password | **백로그 (v1.2 추가)** — 비밀번호 변경. 10.1의 "최근 3개 재사용 금지 / 90일 변경 강제"를 강제할 곳이 없어 해당 정책이 미구현으로 남아 있다. 구현 시 `TB_USER_PWD_HIST`를 함께 만든다 |

**조직 (ORG)**

| Method | Path | 설명 |
|---|---|---|
| GET | /orgs/tree?type=&asOfDate= | 조직도 트리 (기준일자 시점 조회 지원) |
| GET | /orgs/{orgId} | 조직 상세 |
| POST | /orgs | 조직 신설 |
| PUT | /orgs/{orgId} | 명칭변경/이관 (이력 자동 기록 + Outbox) |
| POST | /orgs/{orgId}/close | 조직 폐지 (하위조직/소속인원 존재 시 409) |

**인물/임직원 (PER/EMP)**

| Method | Path | 설명 |
|---|---|---|
| POST | /persons/check-duplicate | RRN 해시 기반 기존 인물 검사 |
| POST | /persons/{personId}/rrn | 주민번호 복호화 조회 — `person.rrn.decrypt` 권한 + 사유 입력 필수(본문), 접근로그 기록. **GET이 아닌 이유는 7.1 참조 (v1.4 정정)** |
| POST | /employees | 입사 처리 (인물 생성/연결 + 입사 발령 동시 생성) |
| GET | /employees?orgId=&status=&empType=&keyword= | 임직원 목록 (마스킹) |
| GET | /employees/{empId} | 상세 (인사기록카드 요약 포함) |
| POST | /employees/{empId}/appointments | 발령 기안 (유형/발령일/대상 소속·직급·직책) |
| POST | /appointments/{appointId}/confirm | 발령 확정 → 스냅샷 반영(당일/과거) 또는 예약(미래) + Outbox |
| POST | /appointments/{appointId}/cancel | 발령 취소 |
| GET/POST/PUT/DELETE | /employees/{empId}/schools · /careers · /families · /certs · /educations · /reward-punishments | 인사기록카드 하위 CRUD |
| GET/POST | /employees/{empId}/leaves, /leaves/{id}/approve | 휴가 신청/승인 |

**설계사 (AGT)**

| Method | Path | 설명 |
|---|---|---|
| POST | /agents/candidates | 후보 등록 (인물 생성/연결, 상태=CANDIDATE) |
| GET | /agents?orgId=&status=&channel=&eligible=&keyword= | 설계사 목록 |
| GET | /agents/{agentId} | 360° 상세: 기본+자격+교육+보증+제재+계보 요약 |
| POST | /agents/{agentId}/appoint | 위촉 실행 — 요건검증 후 PENDING_ASSOC 전이. 미충족 시 422 + 미충족 사유 배열 |
| POST | /agents/{agentId}/assoc-registrations | 협회 등록번호 입력 → ACTIVE 전이 |
| POST | /agents/{agentId}/suspend · /resume | 모집정지/해제 (사유코드 필수) |
| POST | /agents/{agentId}/terminate | 해촉 (사유코드, 협회 말소 이벤트 동반) |
| POST | /agents/{agentId}/reappoint | 재위촉 (냉각기간·금지사유 검사) |
| PUT | /agents/{agentId}/transfer | 소속 이동 (지점 간) |
| GET | /agents/{agentId}/eligibility | 모집자격 실시간 판정 (종목별 결과 + 사유) |
| GET/POST/PUT | /agents/{agentId}/licenses | 판매자격 관리 |
| GET/POST | /agents/{agentId}/educations | 교육 이수 등록 (NEXT_DUE_DT 자동계산) |
| GET/POST/PUT | /agents/{agentId}/guarantees | 재정보증 등록/갱신 |
| GET/POST/PUT | /agents/{agentId}/sanctions | 제재 등록/종료 (RECRUIT_BLOCK_YN=Y면 자동 SUSPENDED) |
| GET/POST | /agents/{agentId}/missell-cases | 불완전판매 건 관리 |
| GET/POST | /agents/{agentId}/cross-sell-registrations | 교차모집 등록 |
| GET | /agents/{agentId}/contracts, POST 동일 | 위촉계약/지급계좌 관리 |
| POST | /agents/{agentId}/contracts/{contractId}/account | **백로그 (v1.6)** — 지급계좌 복호화 조회. `agent.account.decrypt` 권한 + 사유 본문(POST+사유 일반 규칙, 7.1). 이벤트·스냅샷은 계좌를 싣지 않으므로(9.3, 민감정보 부재 테스트) 수수료시스템이 계좌를 얻는 **유일한 경로**가 이 엔드포인트다. 미구현이면 "이벤트에도 API에도 없는" 데이터가 된다. 구현 시 `TB_PRIVACY_ACCESS_LOG` 기록 |
| GET | /agents/{agentId}/genealogy | 도입 계보 트리 (재귀: CONNECT BY 또는 재귀 CTE) |

**연계 (IFC)** — `/sync/*`는 외부 **시스템 계정**(SYSTEM 역할), `/admin/*`는 **연계 운영자**(IT_ADMIN, `integration.admin`). 데이터를 받는 주체와 연계를 관리하는 주체가 다르므로 권한을 분리한다(v1.9).

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | /sync/changes?aggType=&cursor=&size= | `sync.read` | 변경분 Pull (TB_IF_CHANGE_LOG 키셋 커서 + 워터마크 지연 9.4) |
| GET | /sync/snapshot/agents?asOfDate= · /employees · /orgs | `sync.export` | 전체 스냅샷 조회 (대량, 스트리밍) |
| POST/GET | /admin/subscribers · /{id}/activate · /{id}/deactivate | `integration.admin` | 구독 시스템 관리(비활성 시 미전송분 SKIPPED 수렴 9.2) |
| POST | /admin/outbox/{eventId}/resend | `integration.admin` | FAILED 전달 레코드를 PENDING으로 되돌려 재전송 |

**공통/관리**

| Method | Path | 설명 |
|---|---|---|
| GET/POST/PUT | /codes, /codes/{grpCd} | 공통코드 관리 |
| GET/PUT | /policies | 정책값(TB_POLICY_CONFIG) 관리 |
| GET | /admin/privacy-access-logs?targetPersonId=&from=&to= | 개인정보 접근이력 조회 |
| GET/POST/PUT | /admin/users, /admin/roles | 계정/권한 관리 |

### 7.3 대표 요청/응답 예시 — 위촉 실행

```
POST /api/v1/agents/1024/appoint
{ "appointDt": "2026-08-01", "contractTypeCd": "FC_STD", "commRuleVer": "2026-1" }

422 Unprocessable Entity (요건 미충족 시)
{ "success": false,
  "error": { "code": "AGT-4221", "message": "위촉 요건이 충족되지 않았습니다.",
    "details": [
      { "field": "license",      "reason": "NO_VALID_LICENSE",   "message": "유효한 판매자격이 없습니다." },
      { "field": "finGuarantee", "reason": "GUARANTEE_EXPIRED",  "message": "재정보증이 만료되었습니다." } ] } }
```

---

## 8. 배치 설계 (Spring Batch)

모든 잡은 `insuhr-batch` 모듈. 실행: `java -jar insuhr-batch.jar --spring.batch.job.name={잡명} [targetDate=yyyy-MM-dd]`
(잡 선택은 옵션 인자, `targetDate`는 **비옵션** 잡 파라미터 — Boot 4 배선은 3.0 표 참조).
공통 규칙: 재실행 안전(멱등), chunk 기반(기본 500), 실패 시 skip 로그 + 잡 실패 처리, 잡 결과는 Spring Batch 메타테이블 + 운영 로그.

| Job | 주기 | 처리 내용 |
|---|---|---|
| `eligibilityRefreshJob` | 매일 05:00 | 전 ACTIVE/SUSPENDED 설계사를 `AgentEligibilityReconciler`로 재조정. **역할 = 쓰기 없이 날짜 경계가 넘어가는 전이의 유일한 포착자**(v2.0, 아래) — 보수교육 기한 도과·재정보증 만료처럼 트리거하는 쓰기가 없는 사건을 배치가 잡아 ACTIVE↔SUSPENDED 자동 전이 + 사유 기록 + `RECRUIT_ELIG_YN` 갱신 + `agent.eligibility.changed` 발행 |
| `continuingEduNoticeJob` | 매일 06:00 | 보수교육 이수기한 D-60/D-30/D-7 대상 추출 → 알림 대기 테이블 적재(알림 발송 자체는 외부 시스템 몫, 이벤트 발행) |
| `guaranteeExpiryJob` | 매일 06:10 | 재정보증 만료 도래(D-30 알림 / D-0 EXPIRED 처리). 만료 처리 시 eligibility 재판정 트리거 |
| `licenseValidityJob` | 매일 06:20 | 자격 정지/말소 반영, 협회 말소 상태와 위촉상태 정합성 점검 리포트 |
| `futureAppointApplyJob` | 매일 00:10 | 발령일이 도래한 CONFIRMED 발령을 TB_EMP 스냅샷에 반영 + Outbox 발행 |
| `annualLeaveGrantJob` | 매년 01-01 / 입사기념일 | 연차 부여 (근속 기준 정책값 참조) |
| `hrSnapshotFileJob` | 매일 04:00 | 조직/임직원/설계사 전체 및 증분 스냅샷 파일 생성 (9.5 레이아웃) → TB_IF_FILE_LOG 기록 |
| `outboxDlqSweepJob` | 10분 주기 | **정체 감시 스위프**(v2.0 재정의, 아래) — 재전송이 아니라, 한도 소진 `FAILED` 전달 레코드 집계·알림 대상 적재 + 오래 머문 `FANNED_OUT`/`PENDING` 탐지 리포트. 백오프 재시도는 릴레이가 `NEXT_RETRY_AT`로 이미 소유하므로 잡은 **재전송하지 않는다**(이중 전송 경합 방지) |
| `privacyPurgeJob` | 매월 1일 | 개인정보 익명화(암호화 컬럼 NULL + 이름 마스킹 치환) + 파기 대장 기록. 대상 ① 해촉/퇴직 후 보존기간(정책값 `PRIVACY_RETENTION_YEARS`) 경과 인물 ② **역할이 끝내 생성되지 않은 인물** — 생성 후 `ORPHAN_PERSON_PURGE_DAYS`(정책값, Phase 8에서 시드) 경과하도록 TB_EMP·TB_AGENT 어느 쪽도 붙지 않은 행 (v1.4, 근거는 아래) |
| `dataQualityJob` | 매일 07:00 | 정합성 점검: 소속조직 폐지 상태인 재직자, ACTIVE인데 협회등록 없는 설계사 등 → 리포트 적재 |

배치 Reader는 `JdbcPagingItemReader` 또는 `JdbcCursorItemReader`(JdbcClient SQL 재사용) 사용, JPA Reader 지양(대량 처리 성능).

**Phase 7 진입 결정 (v2.0)**

- **배치 메타테이블은 Flyway가 소유한다 (V14).** Boot 4.1은 배치 스키마를 더 이상 자동 생성하지 않는다(3.0 표 — `spring.batch.jdbc.initialize-schema` 제거, 이니셜라이저 클래스 부재). 그러므로 `spring-batch-core-6.0.4.jar`의 `schema-oracle.sql`을 **V14**로 편입해 스키마 소유를 Flyway로 일원화한다. api가 migrate, batch/relay는 validate(4.2)이므로 배치 메타테이블도 이 소유권 체계에 자연히 든다. 잘못 뜬 이니셜라이저가 CREATE를 시도할 여지도 없다.
- **잡 파라미터 규약: `targetDate`(식별) + 호출자 지정 `run.id`(식별).** 잡은 **시스템 날짜를 직접 읽지 않는다** — `targetDate`(비옵션 잡 파라미터)를 받아 도메인에 `asOf`로 넘긴다. 6.2의 앵커 Clock 테스트가 배치까지 관통하는 배선이며(도메인이 `LocalDate.now(clock)`을 쓰던 자리에 배치는 `targetDate`를 주입), **멱등성 테스트가 "같은 `targetDate` 2회 실행 = 같은 결과"로 자연 성립**한다. 같은 `targetDate` 재실행은 **호출자가 유니크한 식별 파라미터 `run.id`를 함께 주는 것**으로 허용한다(둘 다 식별 → 새 `JobInstance`). **`RunIdIncrementer`(잡 레벨 인크리멘터)는 쓰지 않는다** — Batch 6 실측: 잡에 인크리멘터가 있으면 `JobOperator.start(job, params)`가 넘긴 업무 파라미터(`targetDate`)를 **버리고**(로그 "Additional parameters will be ignored") 인크리멘터 파라미터만 남긴다. 인크리멘터는 업무 파라미터가 없는 잡에나 맞다. 배치 파라미터는 `@Value("#{jobParameters['targetDate']}")` 지연바인딩이 아니라 `@Value("#{stepExecution}")` 주입 후 `stepExecution.getJobParameters().getString(...)`로 읽는다(3.0 표 — 전자는 Batch 6에서 `null`).
- **restart 의미론은 의도적으로 쓰지 않는다.** 위 규약에서 매 실행이 새 `JobInstance`가 되므로(호출자 run.id가 매번 다름), 실패한 인스턴스를 이어서 재개하는 Spring Batch의 restart 의미론은 **쓰지 않고**, 대신 새 실행으로 처음부터 다시 돌린다. 이것이 안전한 전제는 **잡이 감싸는 도메인 연산이 재계산 멱등이라는 점**(Phase 3 발령 재계산·Phase 5 reconcile 모두 "기준일의 함수")이다 — 같은 `targetDate`로 처음부터 다시 계산해도 결과가 같으니 중간 상태를 이어붙일 필요가 없다. 재계산 원칙(5.5)이 없었다면 이 선택은 위험했을 것이다.
- **`eligibilityRefreshJob`의 역할 재정의 — 날짜 경계 전이의 유일한 포착자.** Phase 5에서 실시간 reconcile(`AgentEligibilityReconciler`)이 이미 서 있으므로, 이 잡은 더 이상 "스냅샷 갱신기"가 아니다. **자격·교육·보증·제재의 모든 쓰기는 이미 그 쓰기 트랜잭션에서 reconcile된다**(5.4). 배치만이 잡을 수 있는 것은 **트리거하는 쓰기가 없이 날짜만 넘어가는 전이** — 보수교육 이수기한 도과, 재정보증 만료처럼 "그날이 오면" 자격이 바뀌는 사건이다. 구현: Reader는 대상(ACTIVE/SUSPENDED 설계사) **ID만 페이징**으로 집고, Processor가 기존 `reconciler.reconcile(agentId, asOf)`를 **그대로 호출**(판정·전이·발행을 재구현하지 않는다). Phase 3의 REQUIRED 원칙(10.1.1) 덕에 reconcile이 청크 트랜잭션에 자연 합류하고 Outbox 기록도 같은 트랜잭션에 묶인다. 개별 설계사 실패는 skip+로그, 잡 전체는 완주. **시나리오 2(보수교육 기한 도과 → SUSPENDED + 이벤트)가 여기서 완결된다.**
- **`outboxRetryJob` → `outboxDlqSweepJob`으로 재정의(재전송 아님).** §8 원안의 "FAILED 재전송"은 낡았다 — 백오프 재시도는 릴레이가 `NEXT_RETRY_AT`로 이미 소유한다(9.2 v1.8). 같은 일을 배치도 하면 **이중 전송 경합**이 생긴다. 잡의 역할을 **정체 감시 스위프**로 바꾼다: 한도(`OUTBOX_MAX_RETRY`) 소진 `FAILED` 전달 레코드를 집계해 알림/리포트 테이블에 적재하고, 비정상적으로 오래 머문 `FANNED_OUT`/`PENDING`을 탐지한다. 재전송은 하지 않고 **관측만** 한다(수동 재전송은 `POST /admin/outbox/{id}/resend`가 담당, 7.2). `dataQualityJob`에 흡수해도 무방하다.
- **`privacyPurgeJob`(Phase 8)의 대상·이벤트 확정.** 대상은 **두 종류** — ① 보존기간(`PRIVACY_RETENTION_YEARS`) 경과 인물 ② **역할 없는 인물**(`ORPHAN_PERSON_PURGE_DAYS` 경과, Phase 2의 REQUIRES_NEW 결정에서 파생 — 아래 v1.4 블록). 익명화는 **`person.anonymized` 이벤트를 발행한다**(수신 시스템도 파기하도록 파기를 전파) — 페이로드는 업무키(`personId`)만 싣고 민감 원문은 없다(9.3 페이로드 규칙 그대로, 이미 있는 "민감정보 부재" 테스트가 이 이벤트도 지킨다). 구현은 Phase 8.

**Phase 7 후속 — 알림/리포트 출력 테이블 설계 (v2.0, V15에서 구현)**

§8이 참조하는 "알림 대기 테이블"은 6.3의 테이블 목록에 **없었다**(설계서 원본의 누락). 잡별 테이블을 만들지 않고 **범용 큐 2종**으로 채운다 — 멱등이 유니크 키에서 공짜로 나오게.

- **`TB_NOTICE_QUEUE`** — D-30/D-7/D-0 같은 기한 도래 알림의 적재처(발송 자체는 외부 몫, 8장 원칙). 컬럼: `NOTICE_ID`(IDENTITY PK), `NOTICE_TYPE_CD`(CD: 보수교육기한/보증만료/…), `TARGET_TYPE_CD`+`TARGET_ID`(예: AGENT/1024), `DUE_DT`, `MILESTONE_CD`(D30/D7/D0 등), `PAYLOAD`(JSON, 마스킹·업무키만), `STATUS_CD`(PENDING/SENT/…), 감사컬럼. **멱등의 핵심 = `UQ(NOTICE_TYPE_CD, TARGET_ID, DUE_DT, MILESTONE_CD)`.** 이 유니크 키 덕에 알림 잡이 `futureAppointApplyJob`과 **같은 패턴**으로 떨어진다 — 적재는 팬아웃에서 검증된 `INSERT … SELECT … WHERE NOT EXISTS` JdbcClient 한 문장(§4.3, rollback-only 함정 회피), **행이 실제로 생성될 때만 `notice.created` 발행**, 재실행은 no-op.
- **`TB_DQ_FINDING`** — `licenseValidityJob`·`dataQualityJob`의 정합성 점검 리포트 출력(별도 테이블 대신 통합). 컬럼: `FINDING_ID`(IDENTITY PK), `RULE_CD`, `TARGET_TYPE_CD`+`TARGET_ID`, `DETAIL`(JSON), `FOUND_DT`, 감사컬럼. 같은 dedup 원리(룰·대상·일자 유니크)로 재실행 멱등.
- **후속 세션 완료 기준**: V15(위 2표) + 남은 6잡, **각 잡의 재실행 멱등 테스트(dedup 키 검증)** + 알림 이벤트의 **조건부 발행**(행 생성 시에만), 전부 그린이면 그때 `phase-7` 태그. `dataQualityJob`은 이 후속으로 편입한다(13.2에서 Phase 8 목록에서 제외 — 아래).

**Phase 7 후속 구현 완료 — 6잡 + V15 (v2.1)**

V15와 남은 6잡을 구현하며 확정한 결정(위 설계의 실현):

- **`guaranteeExpiryJob` = 로스터에서 상태를 직접 바꾸는 유일한 잡.** 두 스텝 — ① D-30 알림(부등식+dedup, 아래 continuingEdu와 동형) ② `END_DT < targetDate`인 ACTIVE 보증을 EXPIRED로 물질화. 물질화는 도메인 `GuaranteeExpiryService`가 소유하고 **온라인 `AgentCredentialService.changeGuaranteeStatus`와 같은 규약**(엔티티 변경 → `reconcileAsOf`)을 타므로 "만료 처리 시 eligibility 재판정 트리거"가 별도 코드 없이 성립한다. **판정은 `STATUS_CD`가 아니라 `FinGuarantee.isActiveOn`의 기간 술어(`END_DT >= asOf`)로 유효 보증을 센다**(확인) — 물질화 경계(`END_DT < targetDate`)가 판정 경계와 정확히 대칭이라, 이 잡과 `eligibilityRefreshJob` 중 무엇이 먼저 돌든 판정이 같고(순서 무관), 이 잡이 하루 죽어도 refresh가 모집 통제를 보장한다. `STATUS_CD == ACTIVE` 결합조건은 기간 만료와 무관한 취소(CANCELED)를 걸러내는 독립 좁힘으로 유지.
- **알림 마일스톤은 등식이 아니라 부등식 + dedup.** `DUE_DT <= targetDate + M AND UQ 미존재`로 짜서 잡이 며칠 죽었다 복구돼도 놓친 마일스톤이 늦게라도 한 번 나간다(등식이면 영영 안 나감). 정상 운영에선 D-M 첫날 한 번, 다운타임 뒤엔 늦게 한 번. `continuingEduNotice`(D-60/30/7)·`guaranteeExpiry`(D-30) 공통. 조건부 발행: `NoticeQueueDao.enqueue`(단문 `INSERT…WHERE NOT EXISTS`)가 행을 실제로 만든 경우(반환 1)에만 `notice.created`를 발행 — `futureAppointApplyJob`과 같은 패턴, 페이로드는 마스킹·업무키만.
- **`annualLeaveGrantJob`은 회계연도 일괄로 확정.** §8 원안의 "매년 1/1 / 입사기념일" 병기는 서로 다른 부여 모델(회계연도 일괄 vs 개별 기산)이라 포트폴리오 범위에선 회계연도 일괄 하나로 택일한다(1년 미만자 월 단위 개별 기산은 범위 밖). 부여일수는 근속 기준 정책값(`ANNUAL_LEAVE_BASE_DAYS`/`_MAX_DAYS`/`_BONUS_PER_YEARS`, V15 시드)에서 읽어 도메인 `AnnualLeaveGrantService`가 계산. 재실행 멱등 = `UQ_LEAVE_GRANT(EMP_ID, YEAR_NO)` + Reader의 `NOT EXISTS` 필터.
- **`licenseValidityJob` ↔ `dataQualityJob` 경계 = 잡은 둘로 유지, 골격은 공유.** 셋(+`outboxDlqSweep`)이 `TB_DQ_FINDING`에 쓰므로 "룰 평가 → FINDING 적재" 뼈대(`DqRule` + `DqFindingDao.runRule` + `DqSweepTasklet`)를 추출하되 잡은 합치지 않는다 — 대상 도메인·스케줄이 달라 합치면 실패 격리·스케줄 독립성을 잃는다. 경계: **licenseValidity = 살아있는 설계사의 자격 상태 모순**(ACTIVE인데 REVOKED 자격 / DEREGISTERED 협회 — 자격·협회는 날짜 아닌 상태로만 유효, 6.4), **dataQuality = 구조적 결손**(폐지 조직 `USE_YN='N'` 재직자, 등록 협회 전무 ACTIVE 설계사). DQ 잡은 상태를 안 바꾸므로 이벤트 없음(전이는 reconcile 몫). 각 룰 SELECT는 대상당 한 행(GROUP BY/DISTINCT)이어야 한 문장 내 UQ 충돌이 없다.
- **`outboxDlqSweepJob`은 관측만**(재정의대로): 한도 소진 `FAILED` 전달 + 오늘 이전(`CAST(발생시각 AS DATE) < targetDate`) `READY`/`FANNED_OUT`/`PENDING` 정체를 `TB_DQ_FINDING`에 적재, 재전송 없음(백오프는 릴레이가 `NEXT_RETRY_AT`로 소유). `TB_NOTICE_QUEUE`(행동 촉구 알림) vs `TB_DQ_FINDING`(운영 발견)의 용도 구분이 이로써 선다.
- **완료**: V15 + 6잡 + 각 잡 dedup 키 재실행 멱등 테스트 + 알림 조건부 발행 전부 그린 → `phase-7` 태그 조건 충족.

**역할 없는 인물도 파기 대상이다 (v1.4)**

5.2(v1.3)에서 확정했듯 `TB_PERSON` 행은 **호출부 트랜잭션과 무관하게 커밋**된다(제약 위반 복구를 위해 INSERT를 REQUIRES_NEW에 가뒀기 때문). 그래서 입사·후보등록이 인물 생성 뒤에 실패하면 역할 없는 인물이 남는다. 5.2는 이를 "손상이 아니라 재사용 가능 상태"라고 정리했는데, **그 정리는 정합성 관점에서만 참이다.**

개인정보 관점에서 보면 그 행은 암호화된 주민번호를 쥔 개인정보이고, 붙을 역할이 없으므로 **보유 목적이 없다**. 목적 없는 개인정보를 무기한 들고 있는 것은 최소보유 원칙과 어긋난다 — 재사용 가능성은 보유 근거가 되지 못한다. 유예기간(`ORPHAN_PERSON_PURGE_DAYS`)을 두는 것은 등록 절차가 중단됐다 재개되는 정상 케이스를 죽이지 않기 위함이며, 그 기간이 지나면 "재사용될 인물"이 아니라 "목적 없이 남은 개인정보"로 본다.

`privacyPurgeJob`이 삭제가 아니라 **익명화(행 유지)** 라는 점은 여기서도 같다 — 6.5의 `TARGET_PERSON_ID` FK 전제가 이 대상에도 그대로 걸린다.

---

## 9. 대외 연계·동기화 설계

### 9.1 3계층 동기화 전략

| 계층 | 방식 | 지연 | 대상 시스템 예 | 용도 |
|---|---|---|---|---|
| ① 이벤트 Push | Transactional Outbox → Relay → Webhook(기본) / Kafka(프로파일) | 초~분 | 영업시스템, 그룹웨어/SSO | 상태 변경 즉시 전파 (위촉/해촉/발령/조직개편) |
| ② 변경분 Pull | `GET /sync/changes` 커서 API | 수 분 (폴링) | 수수료시스템, 급여시스템 | 수신측 주도 동기화, 유실 복구 |
| ③ 배치 파일 | 일 1회 전체+증분 파일 | 1일 | DW, 레거시(파일 인터페이스만 가능한 시스템) | 전수 대사(reconciliation), 초기적재 |

세 계층은 동일한 `TB_IF_CHANGE_LOG`/`TB_IF_OUTBOX` 원천에서 파생되므로 상호 검증이 가능하다. 수신 시스템은 ①로 실시간 반영하고 ③으로 일 단위 대사하는 조합을 권장.

- **신규 구독자 초기적재는 백필이 아니다 (v1.6).** 과거 Outbox를 전부 되쏘는 백필은 순서·양쪽에서 위험하다. 올바른 온보딩은 **③ 스냅샷 API로 현재 전수 상태를 받고, 그 시점의 워터마크 `SEQ_NO`를 커서 시작점으로 삼아 ②로 이어받는** 것이다. 스냅샷이 기준선을, 커서가 그 이후 증분을 담당한다.

### 9.2 Transactional Outbox 흐름

```
[Application Service Tx]
  1. 업무 테이블 변경 (예: TB_AGENT.STATUS = TERMINATED)
  2. TB_IF_CHANGE_LOG INSERT (스냅샷 JSON)
  3. TB_IF_OUTBOX INSERT (이벤트 JSON, STATUS=READY)
  -- commit (전부 성공 or 전부 롤백 → 유실/유령 이벤트 없음)

[insuhr-relay 프로세스]
  loop: READY 이벤트를 EVENT_ID 순으로 SELECT ... FOR UPDATE SKIP LOCKED (N건)
     → 구독자별 필터(TOPIC_FILTER) 매칭 → 전송
        - webhook: POST {ENDPOINT_URL}, 헤더 X-InsuHR-Signature: HMAC-SHA256(body, secret)
        - kafka:  topic = insuhr.{aggType} , key = aggId (파티션 순서 보장)
     → 성공: SENT + TB_IF_SEND_LOG / 실패: RETRY_CNT++, 백오프 후 재시도, 한도 초과 시 FAILED
```

- **멱등성**: 수신측은 `eventUuid`로 중복 처리 방지. 재전송은 항상 안전.
- **순서**: 동일 aggId 이벤트는 EVENT_ID 오름차순으로만 전송(집계 단위 순서 보장).
- **직렬화 (v1.1)**: Boot 4 기본 JSON은 **Jackson 3**(`tools.jackson`)이다. 페이로드/스냅샷 JSON을 다루는 코드는 `com.fasterxml.jackson`(2.x) 임포트를 섞지 말 것 — 두 버전이 클래스패스에 공존하므로 컴파일은 통과하고 런타임에 설정이 안 먹는 형태로 어긋난다. `ObjectMapper` 대신 Jackson 3의 `JsonMapper`를 쓰고, 커스텀 모듈·직렬화기도 3.x 기준으로 작성한다.

**전송 의미론·순서·서명 (v1.6 — Phase 6 진입 결정)**

- **at-least-once, exactly-once 아님.** 전송 성공 후 SENT 마킹 전에 릴레이가 죽으면 재기동 후 같은 이벤트를 또 보낸다. 이것이 **계약**이다 — 수신측은 `eventUuid` 멱등으로 중복을 흡수한다. exactly-once를 흉내 내려 하지 않는다.
- **순서 보장은 상태 조회만으로 부족하다.** READY를 EVENT_ID 순으로 집더라도, aggX의 이벤트 N이 FAILED로 재시도 대기 중일 때 N+1을 먼저 보내면 순서가 깨진다. 릴레이 조회에 **"같은 `aggType+aggId`에 미전송(READY/FAILED) 선행 이벤트가 있으면 후행은 집지 않는다"** 게이트를 넣는다(같은 aggId는 직렬, 다른 aggId는 병렬). 실패→후행 보류→재시도 성공→후행 전송 순서를 WireMock으로 검증한다.
- **서명에 타임스탬프를 포함해 리플레이를 막는다.** `X-InsuHR-Signature: HMAC-SHA256(timestamp + "." + body, secret)` + `X-InsuHR-Timestamp` 헤더. 수신측은 타임스탬프 시간창 밖이면 거절한다. 서명이 본문만 덮으면 캡처된 요청을 무한 재생할 수 있다.
- **재시도**: 비2xx·타임아웃은 실패. 지수 백오프로 `RETRY_CNT`를 늘리며 재시도하고, 한도(정책값 `OUTBOX_MAX_RETRY`) 초과 시 `FAILED` + `POST /admin/outbox/{eventId}/resend` 수동 재전송. 전송 시도마다 `TB_IF_SEND_LOG` 1행(성공·실패 모두).

**relay 구현 설계 (v1.6 — Phase 6 리뷰). 이 절이 릴레이 세션의 진입 설계다.**

- **다중 구독자 팬아웃 — 단일 `STATUS_CD`로는 부족하다.** 구독자 A 성공·B 실패면 이벤트 상태가 정의 불가다. 매칭 구독자별 **전달 레코드**로 팬아웃한다(표 `TB_IF_DELIVERY`: `DELIVERY_ID, EVENT_ID, SUBSCRIBER_ID, AGG_TYPE, AGG_ID, STATUS(PENDING/SENT/FAILED/SKIPPED), RETRY_CNT, NEXT_RETRY_AT`). 이벤트×구독자 단위로 상태를 갖고, `TB_IF_OUTBOX.STATUS_CD`는 요약으로 강등한다. 이 표와 폴러 인덱스 2종은 아직 태그 안 된 V13에 넣었다(같은 Phase, ALTER 불필요).
- **팬아웃 시점 = 릴레이 픽업, 릴레이는 2단계 (v1.7).** 전달 레코드는 recorder(발행)가 아니라 **릴레이 픽업 시점의 활성 구독자 × `TOPIC_FILTER` 매칭**으로 만든다. 릴레이를 둘로 쪼갠다 — ① **팬아웃 단계**: Outbox를 `EVENT_ID` 순으로 소비해 `TB_IF_DELIVERY` 레코드 생성 ② **전달 단계**: 구독자별로 자기 레코드만 순서대로 전송. 팬아웃이 이미 순서를 매긴 뒤라 순서 게이트가 **"같은 (구독자, aggId)의 선행 전달 레코드가 미전송(PENDING/FAILED)이면 후행 보류"** 로 단순해진다 — Outbox까지 거슬러 볼 필요가 없다. **신규 구독자가 과거 이벤트를 소급 수신하지 않는다는 성질이 이 정의에서 공짜로 나온다**(팬아웃은 그 구독자가 활성인 시점 이후 이벤트만 만든다) — 9.1의 "백필 아닌 스냅샷+워터마크 온보딩"과 정확히 맞물린다.
- **순서 게이트는 (구독자, aggId) 단위.** 죽은 구독자 하나가 다른 구독자의 전달을 막지 않는다. 특정 구독자에게 aggX의 이벤트 N이 미전송이면, 그 구독자에게 N+1을 보류할 뿐 다른 구독자·다른 aggId는 흐른다. **구독자 격리**를 WireMock 테스트로 검증하고 완료 기준에 넣는다.
- **비활성 구독자의 종결 = SKIPPED (v1.7).** 미전송 전달 레코드를 남긴 채 구독자가 비활성화(`USE_YN='N'`)되면 Outbox 요약이 영원히 수렴 못 한다. 비활성화 시 그 구독자의 미전송(PENDING/FAILED) 레코드를 **SKIPPED**로 종결하고, Outbox 요약 규칙을 **"전 전달 레코드가 SENT 또는 SKIPPED면 SENT"** 로 둔다(원래 DDL의 SKIPPED가 여기서 제 용도를 찾는다). 구독자 격리 테스트에 이 케이스를 한 줄 더한다.
- **폴러 인덱스(V13).** 릴레이는 폴링 루프라 두 쿼리가 시스템 최다 실행 SQL이 된다 — 전달 픽업 `IX_DELIVERY_PICKUP(SUBSCRIBER_ID, STATUS_CD, NEXT_RETRY_AT)`, 순서 게이트 `IX_DELIVERY_GATE(SUBSCRIBER_ID, AGG_TYPE, AGG_ID, DELIVERY_ID)`. V13을 여는 김에 함께 넣었다.

**릴레이 상태 전이·소비 표시 (v1.8 — 릴레이 세션 진입 확정)**

- **Outbox 소비 표시 = `READY → FANNED_OUT`.** 팬아웃이 어떤 Outbox 행을 이미 처리했는지 상태로 안다 — 팬아웃 단계는 `READY`만 집어 전달 레코드를 만들고 그 행을 `FANNED_OUT`으로 전이시킨다. `UQ(EVENT_ID, SUBSCRIBER_ID)`는 팬아웃이 전달 레코드를 만든 뒤 `FANNED_OUT` 전이 전에 죽은 경우의 **크래시 재실행 백스톱**일 뿐(재실행 시 `WHERE NOT EXISTS`가 기존 레코드를 건너뛴다) — 정상 흐름의 소비 표시는 상태 전이가 한다. Outbox 상태: `READY → FANNED_OUT → SENT`(요약), 값 집합에 `FANNED_OUT` 추가.
- **구독자 0명 이벤트 = 즉시 `SENT`(전달 대상 없음).** 매칭 구독자가 0명이면 전달 레코드가 하나도 안 생겨 "전 전달 레코드가 SENT/SKIPPED" 요약 규칙이 **공허하게 참**이 된다. 이를 명시적으로 `READY → SENT`(대상 없음) 종결로 정의한다 — 안 그러면 그 Outbox 행이 `READY`도 `SENT`도 아닌 유령으로 영원히 남는다. 팬아웃 단계에서 삽입 건수가 0이면 곧바로 SENT.
- **게이트의 종결 집합 = {SENT, SKIPPED}.** "선행 미전송" 판정을 상태 집합으로 못박는다 — 종결(후행 통과) = {SENT, SKIPPED}, 선행이 {PENDING, FAILED} 중 하나면 후행 보류. `FAILED`(재시도 소진)도 후행을 막는다(순서 유지) — 수동 재전송으로 재개되거나 구독자 비활성화로 `SKIPPED` 종결돼야 후행이 풀린다. **`SKIPPED`가 후행을 막지 않는다**는 점이 비활성 종결 결정과 순서 게이트를 아귀 맞춘다.
- **요약 갱신의 주체 = 종결 시점의 형제 검사.** 전달 레코드가 종결(SENT/SKIPPED)될 때마다 같은 `EVENT_ID`의 형제를 검사해 전부 종결이면 Outbox 요약을 `SENT`로 올린다. **단일 릴레이 인스턴스 가정(위 SKIP LOCKED 항)** 아래 팬아웃·전달 두 단계를 한 루프에서 순차 실행하므로 형제 검사·요약 갱신에 경합이 없어 락 없이 성립한다 — 요약 정합성은 이 단일 인스턴스 가정에 의존한다.
- **전달 레코드 신규 = `PENDING`, `NEXT_RETRY_AT` 기본 생성시각(V13).** 픽업 쿼리가 `STATUS='PENDING' AND NEXT_RETRY_AT <= 현재` 단일 술어로 떨어져 `IX_DELIVERY_PICKUP`을 깔끔히 탄다(NULL-or 분기 제거). 실패 시 `RETRY_CNT++` + `NEXT_RETRY_AT = 현재 + 백오프`로 `PENDING` 유지, 한도(`OUTBOX_MAX_RETRY`) 초과 시 `FAILED`(수동 재전송 대상).
- **SKIP LOCKED와 게이트의 상호작용.** "선행 미전송 존재" 검사는 잠금과 무관한 일반 조회여야 한다 — 다른 워커가 잠근 선행 행도 "미전송"으로 보여야 후행이 보류돼 순서가 지켜진다. 포트폴리오는 **단일 릴레이 인스턴스**를 가정한다. 스케일아웃하려면 aggId 해시로 파티셔닝해 같은 aggId를 한 워커가 처리하는 것이 전제다.
- **픽업은 락 없는 일반 `SELECT`다 — `FOR UPDATE SKIP LOCKED` 미사용 (v2.0 — 구현 확정).** 위 pseudocode의 `SELECT ... FOR UPDATE SKIP LOCKED`는 다중 워커 형태이고, **단일 인스턴스 가정 하에서는 잠금이 불필요**하므로 실제 구현(`IfDeliveryDao.pickPending`/`pickReadyEvents`)은 잠금 없는 평범한 조회다(게이트도 위 항대로 잠금 무관 조회여야 한다). 이는 침묵한 이탈이 아니라 **문서화된 트레이드오프**다 — 운영 실수로 릴레이 인스턴스가 2개 뜨면 ① 같은 전달 레코드를 둘이 집어 **중복 전송**(수신측 `eventUuid` 멱등으로 흡수됨)과 ② 게이트가 인스턴스 간 조율을 못 해 **순서 위반** 가능성이 생긴다. 스케일아웃 시 **aggId 해시 파티셔닝**(같은 aggId → 한 워커)이 전제이며, 그 전까지 단일 인스턴스가 정합성의 근거다.
- **서명은 전송 바이트 그대로.** 직렬화를 한 번만 하고 그 바이트를 서명·전송한다(서명용 재직렬화는 바이트가 달라질 수 있다). 서명 입력 = `timestamp + "." + body`, 헤더 `X-InsuHR-Timestamp`. 수신측은 타임스탬프 스큐(정책값) 밖이면 거절 — 여기까지가 리플레이 방어다.
- **백오프는 `NEXT_RETRY_AT` 컬럼으로 영속화.** 인메모리 대기면 재기동 시 백오프가 리셋된다. 컬럼으로 두면 Phase 7 `outboxRetryJob`이 같은 필드(`NEXT_RETRY_AT <= 현재`)를 읽어 자연히 이어진다.
- **워터마크·기간 비교는 DB 시계로.** 컬럼이 UTC 규약(6.2)이니 비교도 `SYS_EXTRACT_UTC(SYSTIMESTAMP) - INTERVAL '5' SECOND`처럼 DB 시계 기준으로 한다. 앱 시계와 섞으면 지연 창이 흔들린다.
- **기록 실패 = 업무 롤백.** recorder가 호출자 트랜잭션에서 쓰므로, Outbox/ChangeLog INSERT가 실패하면 업무 변경도 함께 롤백돼야 한다(기록 없으면 변경 없음 — 10.1.1 접근로그와 같은 방향). 트랜잭셔널 아웃박스의 존재 이유이며, recorder 실패를 주입해 업무 행이 롤백됨을 단언하는 테스트로 못박는다.
- **Pull·스냅샷 API 통제**: `sync.export` 권한 + 대량 접근로그(10.2). 사외로 전수 데이터가 나가는 경로다.

### 9.3 표준 이벤트 카탈로그

| eventType | 발생 시점 |
|---|---|
| `org.created` / `org.updated` / `org.closed` | 조직 신설/변경/폐지 |
| `person.updated` | 인물 신상 변경 (연락처 등, 식별정보 원문은 미포함) |
| `employee.hired` / `employee.appointed` / `employee.resigned` | 입사/발령확정/퇴직 |
| `agent.candidate.registered` | 후보 등록 |
| `agent.appointed` | 위촉(ACTIVE 전이) |
| `agent.status.changed` | 모든 상태 전이 (SUSPENDED 포함) |
| `agent.terminated` | 해촉 |
| `agent.transferred` | 소속 이동 |
| `agent.license.changed` / `agent.education.completed` / `agent.guarantee.changed` / `agent.sanction.imposed` | 자격/교육/보증/제재 변경 |
| `agent.eligibility.changed` | 모집자격 판정 결과 변화 |

**페이로드 표준 스키마**

```json
{
  "eventUuid": "8f0c...",
  "eventType": "agent.terminated",
  "occurredAt": "2026-08-31T18:00:00+09:00",
  "aggregate": { "type": "AGENT", "id": 1024, "businessKey": "A202600124" },
  "data": {
    "agentCd": "A202600124", "personName": "김*수",
    "orgCd": "B1203", "channelCd": "FC",
    "statusCd": "TERMINATED", "terminateDt": "2026-08-31", "terminateRsnCd": "SELF"
  },
  "schemaVersion": 1
}
```

원칙: 페이로드에는 **마스킹된 이름과 업무키만** 포함. 주민번호·계좌 등 민감 원문은 절대 이벤트에 싣지 않고, 필요한 시스템이 Pull API + 복호화 권한으로 조회한다.

### 9.4 변경분 Pull API 규격

```
GET /api/v1/sync/changes?aggType=AGENT&cursor=182734&size=500
→ { "items": [ { "seqNo": 182735, "aggType": "AGENT", "aggId": 1024,
                 "changeType": "U", "changedAt": "...", "snapshot": { ...현재 전체 상태... } } ],
    "nextCursor": 183001, "hasMore": true }
```

- 수신측은 마지막 처리 `seqNo`만 저장하면 되는 **커서 재개형**. 어떤 시점에 장애가 나도 그 지점부터 재수신 가능.
- `snapshot`은 변경 후 전체 상태(state-carried transfer)라 수신측이 순서 꼬임 없이 upsert 가능.
- **커서의 시퀀스 갭 함정과 워터마크 지연 (v1.6 — Phase 6 진입 결정).** Oracle 시퀀스는 **채번 순서 ≠ 커밋 순서**다. 긴 트랜잭션이 낮은 `SEQ_NO`를 늦게 커밋하면, 커서가 이미 그 지점을 지나간 뒤라 그 행은 **영구 유실**된다(수신측은 다시 조회하지 않는다). 포트폴리오 수준 해법은 **워터마크 지연**: `/sync/changes`는 `CHANGED_AT < 현재시각 - N초`(정책값 `SYNC_WATERMARK_SECONDS`, 기본 5)인 행만 반환한다. N초 안에 커밋되지 못한 트랜잭션은 없다고 가정하는 것이며, 이 함정을 알고 막았다는 것 자체가 설계의 요점이다.

### 9.5 일배치 스냅샷 파일 레이아웃

- 형식: UTF-8 CSV(헤더 포함) + 트레일러 라인(`#COUNT=nnnn`) + 파일별 SHA-256 체크섬 파일
- 파일명: `INSUHR_{ENTITY}_{FULL|DELTA}_{yyyyMMdd}.csv` (예: `INSUHR_AGENT_FULL_20260717.csv`)
- AGENT FULL 컬럼: `AGENT_CD, PERSON_NM_MASKED, ORG_CD, CHANNEL_CD, STATUS_CD, FIRST_APPOINT_DT, LAST_APPOINT_DT, TERMINATE_DT, RECRUIT_ELIG_YN, LIFE_LIC_YN, NONLIFE_LIC_YN, VARIABLE_LIC_YN, CONT_EDU_DUE_DT, GRNT_END_DT`
- 전달: 로컬 `outbox-files/` 디렉터리 생성까지 구현(운영에서는 SFTP/오브젝트 스토리지로 교체 가능하도록 `FileDeliveryPort` 인터페이스 분리)

### 9.6 코드 매핑

타 시스템이 다른 코드체계를 쓰는 경우를 위해 `TB_CD`의 `ATTR1~3`을 외부 코드 매핑 슬롯으로 사용 (예: ATTR1=수수료시스템 코드). 매핑 변환은 relay의 구독자별 변환기(선택 기능)로 처리.

---

## 10. 보안 및 개인정보보호 설계

### 10.1 인증/인가

- 로그인: BCrypt 검증 → JWT Access(30분) + Refresh(14일, DB 저장·회전). 비밀번호 정책: 최소 10자, 90일 변경, 최근 3개 재사용 금지, 5회 실패 잠금 (정책값).
- **Refresh 토큰 저장·회전 (v1.2 확정)**
  - 토큰 **원문을 저장하지 않는다**. SHA-256 해시(`TOKEN_HASH`)만 저장한다 — DB가 유출돼도 그것만으로 세션을 탈취할 수 없어야 한다. 토큰이 256비트 난수라 pepper는 불필요하다(경우의 수가 좁은 주민번호 해시와 다르다, 6.8).
  - Refresh 토큰은 JWT가 아니라 **불투명 난수**다. 서버가 DB로 상태를 관리하므로 자체 서술적일 이유가 없고, 짧을수록 유출 표면이 작다.
  - 회전: 쓰인 토큰은 즉시 폐기하고 새로 발급한다. **이미 폐기된 토큰이 다시 오면 그 계정의 모든 토큰을 무효화한다** — 정상 클라이언트는 폐기된 토큰을 다시 쓸 이유가 없으므로, 재사용은 탈취를 시사한다. 누가 진짜인지 서버는 알 수 없으니 둘 다 끊고 재로그인시킨다.

#### 10.1.1 트랜잭션 경계 규약 (v1.2 — Phase 1 실증)

**"쓰고 나서 예외를 던지는" 경로는 전부 롤백 함정을 밟는다.** 예외가 트랜잭션을 롤백시켜 방금 쓴 것이 사라진다. `saveAndFlush`는 해결책이 아니다 — flush는 커밋이 아니다. Phase 1에서 이 함정을 두 번(로그인 실패 카운트, 토큰 재사용 무효화) 밟았다.

기록의 성격에 따라 방향이 **정반대**이므로 매번 아래 질문에 답하고 결정한다: **"응답이 실패해도 이 기록은 남아야 하는가?"**

| 기록 | 답 | 트랜잭션 | 이유 |
|---|---|---|---|
| 로그인 실패 카운트 | 남아야 함 | **REQUIRES_NEW** | 롤백에서 살아남지 못하면 계정이 영원히 안 잠긴다 |
| 토큰 재사용 감지 시 전체 무효화 | 남아야 함 | **REQUIRES_NEW** | 롤백되면 탈취된 토큰이 계속 살아있다 |
| 인물 INSERT 시도 (5.2) | — | **REQUIRES_NEW** | 다른 이유다 — 제약 위반이 트랜잭션을 오염시켜 <b>복구 조회조차 막기</b> 때문이다 (v1.3) |
| 개인정보 접근로그 (10.2) | 같이 실패해야 함 | **같은 트랜잭션** | 기록 없으면 열람도 없다 — 로그가 실패하면 응답도 실패해야 한다 |
| 기준정보 변경 + 이력 + Outbox (9.2) | 같이 실패해야 함 | **같은 트랜잭션** | 유실/유령 이벤트 방지 |

**커넥션 풀 주의**: `REQUIRES_NEW`는 바깥 트랜잭션의 커넥션을 쥔 채 두 번째 커넥션을 꺼낸다. 동시성이 몰리는 엔드포인트(로그인)에서 모든 스레드가 첫 커넥션을 잡고 두 번째를 기다리는 **자기 고갈(pool starvation)**이 가능하다. 그래서 `login()`에는 `@Transactional`을 걸지 않는다 — 인증 검증은 읽기뿐이고, 쓰기가 필요한 경로(실패 기록 / 토큰 발급)가 각자 트랜잭션을 연다. 발급은 기본 전파(REQUIRED)라 `refresh()`처럼 이미 트랜잭션 안에서 부르면 합류해 커넥션이 하나로 유지된다.

**상태를 바꾸는 도메인 서비스는 자기 트랜잭션 경계를 선언한다 (v1.5 — Phase 3 실증)**

- **엔티티를 로드해 변경하는(더티 체킹에 맡기는) 도메인 서비스 메서드는 스스로 `@Transactional`(REQUIRED)을 선언한다.** "호출부가 트랜잭션을 깔아줄 것"이라는 가정을 금지한다.
- **왜**: Phase 3의 `AppointmentApplyService.recalculate()`가 이 함정을 밟았다 — `Emp.applySnapshot()`이 변경만 하고 save를 부르지 않는데 메서드에 트랜잭션이 없어, 온라인 확정 경로(상위 트랜잭션 있음)에서는 우연히 flush됐지만 **배치·테스트가 직접 부르면 변경이 유실**됐다. "온라인에서만 우연히 동작"하는 코드는 Phase 7에서 배치가 도메인 서비스를 직접 부르기 시작하면 전부 시험대에 오른다.
- REQUIRED이므로 상위 트랜잭션이 있으면 합류하고 없으면 개시한다. Spring Batch의 청크 트랜잭션 안에서 불려도 자연히 합류하므로 Phase 7과 충돌하지 않는다 — 가정을 없애는 것이 비용 없이 양쪽을 다 만족시킨다.

- **미구현 (Phase 1 시점)**: "최근 3개 재사용 금지"와 "90일 변경 강제"는 비밀번호 변경 엔드포인트가 7.2에 없어 보류했다. 변경 API(`PUT /auth/password`, 7.2 백로그)를 추가할 때 비밀번호 이력 테이블(`TB_USER_PWD_HIST`)과 함께 구현한다. 정책값(`PWD_REUSE_BLOCK_CNT`, `PWD_EXPIRE_DAYS`)은 이미 시드돼 있다.
- 인가: `TB_ROLE_PERM`의 `{리소스}.{행위}` 권한을 JWT 클레임에 싣고 메서드 보안(`@PreAuthorize("hasAuthority('agent.write')")`)으로 검사.
- 기본 역할: `HR_ADMIN`, `SALES_ADMIN`(설계사 관리), `BRANCH_MANAGER`(소속 조직 한정), `SUPPORT_STAFF`(조회 위주), `SELF`(본인), `SYSTEM`(연계), `IT_ADMIN`.
- **행 수준 접근 통제**: BRANCH_MANAGER/SUPPORT_STAFF는 본인 소속 조직 트리 하위 데이터만 조회 가능 — 공통 `OrgScopeFilter`가 쿼리 조건에 조직 ID 목록을 강제 주입.

### 10.2 개인정보 통제 매트릭스

| 행위 | 필요 권한 | 추가 통제 |
|---|---|---|
| 목록/상세 조회 (마스킹) | `*.read` | — |
| 주민번호/계좌 복호화 | `person.rrn.decrypt` 등 | 사유 입력 필수 + TB_PRIVACY_ACCESS_LOG 기록. **POST + 사유 본문**(7.1 — GET이면 사유가 URL로 샌다) |
| 대량 내보내기(파일/스냅샷 API) | `sync.export` | 시스템 계정 한정, 전 건 접근로그 |
| 파기 | 배치 전용 | 파기 대장(감사로그) 기록 |

마스킹 규칙(공통 유틸): 이름 `김*수`, 주민번호 `900101-1******`, 휴대폰 `010-****-1234`, 계좌 뒤 4자리만.

**접근로그는 열람과 같은 트랜잭션 (v1.2)**

복호화는 읽기라 유일한 쓰기가 로그 INSERT다. **로그 INSERT가 실패하면 응답도 실패해야 한다 — 기록 없으면 열람 없음.** 10.1.1의 로그인 실패 기록(REQUIRES_NEW로 롤백에서 살아남아야 함)과 **정반대 방향**이므로 혼동하지 말 것. 판단 기준은 "응답이 실패해도 이 기록이 남아야 하는가"이며, 접근로그의 답은 "아니오"다.

**마스킹 표시값은 쓰기 시점에 저장한다 (v1.2)**

목록 응답의 마스킹 값(`010-****-1234`)을 만들려고 암호문을 복호화하면 **목록 20건 = 복호화 20회**가 매 페이지마다 발생한다. 마스킹은 원문이 필요 없는 표시 문자열이므로, 쓰기 시점에 계산해 별도 평문 컬럼(`*_MASKED`)에 저장하고 목록은 그 컬럼만 읽는다.

- 대상: `TB_PERSON.MOBILE_MASKED`(+`PERSON_NM`은 원래 평문), `TB_AGENT_CONTRACT.ACCOUNT_MASKED`(Phase 5) 등 목록에 노출되는 암호화 컬럼 전부.
- 원문 변경 시 마스킹 컬럼도 함께 갱신해야 한다 — 엔티티의 setter가 두 값을 한 번에 바꾸도록 강제한다(따로 두면 어긋난다).
- 마스킹 컬럼은 개인정보가 아니므로 파기 대상에서 제외되지 않는다 — `privacyPurgeJob`(8장)은 이 컬럼도 함께 지운다.

### 10.3 키 관리

- AES 키·HMAC pepper는 환경변수/외부 시크릿(운영 가정: KMS/Vault)에서 주입, 소스·DB에 저장 금지.
- 키 버전 필드를 암호문 앞에 프리픽스(`v1:...`)로 저장해 키 회전 가능 구조.

### 10.4 감사

- 모든 쓰기 API: AOP가 before/after를 `TB_AUDIT_LOG`에 JSON으로 기록.
- 모든 요청: traceId(MDC) 로깅, 응답에 포함.

---

## 11. 비기능 요구사항

| 항목 | 기준 (포트폴리오 가정) |
|---|---|
| 규모 가정 | 임직원 5천 명, 설계사 5만 명(해촉 포함 누적 20만), 조직 3천 개, 일 이벤트 5만 건 |
| API 응답 | 단건 조회 p95 < 300ms, 목록 p95 < 800ms |
| 배치 | eligibilityRefreshJob 5만 건 30분 이내 (chunk 500, 파티셔닝 여지) |
| 가용성 | api/batch/relay 프로세스 분리로 배치 부하가 온라인에 영향 없도록 |
| 관측성 | Spring Boot Actuator(health/metrics/prometheus), 구조화(JSON) 로그 |
| 트랜잭션 | 기준정보 변경 + Outbox 기록은 단일 로컬 트랜잭션 (분산 트랜잭션 불사용) |

---

## 12. 테스트 전략

| 레벨 | 도구 | 대상 |
|---|---|---|
| 단위 | JUnit 5 + AssertJ | 상태머신 전이표 전체, 모집자격 판정 규칙(경계일자 포함), NEXT_DUE_DT 계산, 마스킹/암호화 유틸 |
| 리포지토리/SQL | Testcontainers `gvenzl/oracle-free` + Flyway | JPA 매핑, JdbcClient 쿼리, 키셋 페이징 |
| API 통합 | RestAssured + Testcontainers | 인증 포함 주요 유스케이스, 422 요건검증 응답 |
| 배치 | Spring Batch Test | 잡 단위 시나리오 (만료 처리, 재실행 멱등성) |
| 연계 | WireMock(웹훅 수신 목) | Outbox → relay 전송, 서명 검증, 재시도/멱등 |
| 시나리오 | 통합 시나리오 테스트 | 부록 B 위촉 E2E를 코드로 재현 |

필수 시나리오 테스트 목록:
1. 요건 미충족 위촉 시도 → 422 + 사유 배열 — **1a/1b로 분할 (v1.5)**
   - **1a (Phase 4)**: 상태머신이 `RecruitmentRequirementChecker`(SPI)로부터 미충족 판정을 받으면 위촉 전이를 거부하고 **422 + 사유 배열**로 성형하는지. Phase 5 테이블 없이, 미충족을 반환하는 checker를 주입해 상태머신의 계약을 검증한다. happy path는 Phase 4 스텁(통과)으로 CANDIDATE→PENDING_ASSOC가 성립함을 함께 본다
   - **1b (Phase 5)**: 실제 판매자격·등록교육·재정보증 데이터가 `RecruitEligibilityService`에서 미충족 판정을 내는지(경계 포함). 5.4 판정 규칙표 검증에 붙는다
   - **분할 이유**: 위촉 요건의 입력(자격·교육·보증)은 Phase 5 소관이라, 요건 규칙을 Phase 4로 당기면 Phase 5 테이블이 Phase 4에 끌려 들어온다. 반대로 상태머신의 422 성형은 Phase 4의 책임이다 — 6a/6b 분할과 같은 논리(위험이 있는 층에서 그 층만 검증)
2. 보수교육 기한 도과 배치 → SUSPENDED 전이 + 이벤트 발행 확인
3. 재정보증 갱신 등록 → eligibility 재판정으로 ACTIVE 복귀
4. 해촉 → 재위촉 냉각기간 검사 (금지사유 해촉이면 영구 거부)
5. Outbox 이벤트 전송 실패 → 재시도 → 성공 시 SEND_LOG 2건, 수신측 멱등 처리
6. 미래일자 발령 확정 → 발령일 배치 후 스냅샷 반영 — **6a/6b로 분할 (v1.4)**
   - **6a (Phase 3, 배치 없이)**: 반영 **규칙** 검증. `AppointmentApplyService`를 주입된 `Clock`으로 굴려 ① 발령일=오늘은 반영·내일은 미반영(날짜 경계) ② 같은 날 다중 발령은 `APPOINT_ID` 큰 쪽이 승리 ③ 반영된 발령은 취소 불가(409), 정정 발령으로만 되돌림 ④ 재계산을 반복 호출해도 결과 동일(멱등 — 5.5의 함수 정의가 성립하는지)
   - **6b (Phase 7, 배치 래퍼)**: `futureAppointApplyJob`이 대상 추출·청크 처리·재실행 멱등·Outbox 발행까지 포함해 6a의 서비스를 올바로 감쌌는지
   - **분할 이유**: 이 시나리오의 위험은 배치 배관이 아니라 반영 규칙 자체(날짜 경계, 동일일자 순서, 취소 상호작용)에 있다. 규칙 검증을 Phase 7까지 미루면 Phase 3의 완료 판단 근거가 사라지고, 반대로 잡 전체를 Phase 3으로 당기면 Spring Batch 메타테이블과 `insuhr-batch` 배선이 Phase 3에 끌려 들어와 범위가 흐려진다.
7. 복호화 API 호출 → 접근로그 생성 검증
8. 동일 주민번호로 직원+설계사 이중 역할 생성 → PERSON 1건 유지

---

## 13. Claude Code 구현 가이드

### 13.1 CLAUDE.md에 넣을 핵심 규칙 (프로젝트 루트에 생성)

```markdown
# InsuHR 구현 규칙
- 이 저장소는 insuhr-design-spec.md(본 설계서)를 단일 사양으로 따른다. 충돌 시 설계서 우선.
- Java 21, Spring Boot 4.1.x, Gradle Kotlin DSL 멀티모듈 (common/domain/api/batch/relay)
- DB 변경은 반드시 Flyway 마이그레이션 파일로만 (엔티티 ddl-auto 금지, validate만 허용)
- 비즈니스 규칙은 insuhr-domain에만. Controller에 규칙 금지.
- 기준정보 변경 서비스는 같은 트랜잭션에서 ChangeLog + Outbox를 기록한다 (공통 컴포넌트 IntegrationRecorder 사용)
- 개인식별정보: 응답 기본 마스킹, 복호화는 전용 엔드포인트 + 접근로그
- 모든 공개 API는 통합 테스트 1개 이상 동반. 테스트 없는 PR 금지.
- 커밋: Conventional Commits (feat/fix/test/refactor/docs/chore)
- 법령 의존 수치(교육주기, 보증금액, 보존기간)는 TB_POLICY_CONFIG에서 읽는다. 하드코딩 금지.
```

### 13.2 Phase별 작업 (각 Phase는 독립 실행 가능 상태로 종료)

**Phase 0 — 부트스트랩**
- 멀티모듈 골격, docker-compose, Flyway `V1__init.sql`(빈 스키마 확인용), Actuator, 공통 응답/예외/에러코드, BaseEntity+Auditing, Spotless
- 완료 기준: `docker compose up` 후 api 기동, `/actuator/health` UP, 통합 테스트에서 Oracle 컨테이너 기동 확인

**Phase 1 — 공통 기반**
- 공통코드(TB_CD_GRP/TB_CD) + 부록 A 시드 데이터(Flyway `V2`), 정책값(TB_POLICY_CONFIG + 시드는 별도 `V3`), 계정/역할/JWT 인증, 권한 어노테이션, 마스킹·암호화 유틸(+키버전), 접근로그
- 완료 기준: 로그인→권한별 403 검증 테스트, 암·복호화/마스킹 단위테스트 통과
- **시드 마이그레이션 분리 (v1.1)**: 공통코드(`V2`)와 정책값(`V3`)은 파일을 나눈다. 정책값은 환경별로 값이 달라지므로(로컬 가정값 vs 운영 실값) 나중에 repeatable migration이나 환경별 시드로 갈라낼 여지를 남긴다.
- **암호화 유틸 배치 (v1.1)**: 4.2는 "암호화 유틸"을 insuhr-common에 두라고 하지만, common은 프레임워크 무관(의존성 0) 모듈이다. 기준은 **JDK 내장 JCA만으로 되는가**다.
  - AES-256-GCM, SHA-256+pepper, 마스킹 → JDK JCA만 필요 → **common**
  - BCrypt → `spring-security-crypto` 필요 → **common 금지**. domain의 auth 패키지(또는 api)에 둔다.

**Phase 2 — 조직·인물**
- TB_ORG(+HIST), 조직 트리 API(시점 조회), TB_PERSON(+ADDR), 중복검사, 복호화 엔드포인트
- 완료 기준: 조직 개편 시 이력·Outbox 스텁 기록, RRN_HASH 유니크 동작 테스트
- **진입 전 확정된 결정 (v1.2)**
  - 이력 행은 전체 스냅샷 (6.6) — 시점 조회를 diff 재생이 아니라 "조직별 최신 이력 1건" 쿼리로 푼다
  - 중복 방어선은 유니크 제약 (5.2) — 검사-후-삽입 금지, ORA-00001 → 역할 추가 전환. 동시성 테스트 필수
  - 접근로그는 열람과 같은 트랜잭션 (10.2) — 10.1.1의 반대 방향
  - 마스킹 표시값은 쓰기 시점에 별도 컬럼으로 저장 (10.2) — 목록마다 복호화하지 않는다
  - Phase 1이 FK 없이 남긴 `TB_USER.PERSON_ID` / `TB_PRIVACY_ACCESS_LOG.TARGET_PERSON_ID`에 FK 추가 (후자의 전제는 6.5 참조)
- **`AuditorAware` 전환 주의**: SecurityContext의 로그인 ID를 쓰도록 바꾸되, **배치·릴레이에는 SecurityContext가 없다**. 잡 이름 또는 `SYSTEM-{모듈명}` 폴백을 반드시 남긴다 — 안 그러면 Phase 7에서 배치가 감사컬럼 NOT NULL 제약에 걸려 넘어진다.

**Phase 3 — 임직원**
- TB_EMP + 발령(기안/확정/취소/미래발령), 인사기록카드 6종, 휴가
- 완료 기준: **시나리오 6a**(반영 규칙 — 12장) 통과, 발령 확정 시 스냅샷·이벤트 정합. 6b(배치 래퍼)는 Phase 7
- **난이도가 고르지 않다**: 테이블 수는 많지만(인사기록카드 6종) 대부분 단순 CRUD다. 실질 위험은 발령 상태·반영 규칙에 몰려 있으므로 테스트도 거기에 집중한다.
- **진입 전 확정된 결정 (v1.4)**
  - **스냅샷은 재계산 함수** (5.5) — 증분 적용 금지. 멱등과 동일일자 순서가 정의에서 따라 나온다
  - **취소 의미론** (5.5) — 반영된 발령(`APPOINT_DT <= 오늘`)은 취소 불가 409, 정정 발령으로만 되돌린다
  - **사번은 시퀀스 채번, 의미 없는 번호** (6.4) — MAX+1 금지
  - **`Clock`을 빈으로 주입한다** — 도메인이 `LocalDate.now()`를 직접 부르면 "발령일이 오늘인 건은 반영, 내일 건은 미반영" 같은 날짜 경계 테스트를 쓸 수 없다(시스템 날짜를 바꿔야 하므로). 지금 도입하면 Phase 5의 모집자격 판정(경계일자)과 Phase 7 배치 테스트가 전부 이 위에서 굴러간다. 나중에 넣으면 그때까지 쌓인 도메인 코드를 전부 고쳐야 한다
  - **`OrgService.close()`의 소속인원 검사는 절반만 갚는다** — TB_EMP 검사는 이번에 채우되 TB_AGENT 검사는 Phase 4까지 남는다. 남은 절반은 산문 메모가 아니라 **`@Disabled("Phase 4: TB_AGENT 검사")` 테스트를 지금 작성**해 남긴다. 실행 가능한 형태의 빚이 문서 메모보다 잊기 어렵다

**Phase 4 — 설계사 코어**
- TB_AGENT + 상태머신(AgentLifecycleService), 후보등록/위촉/정지/해촉/재위촉/이동, 위촉계약, 계보 조회
- 완료 기준: **전이표 5×5 전 케이스 단위테스트**, **시나리오 1a**(위촉 422 성형)·**4**(재위촉 냉각기간·금지사유) 통과. 1b는 Phase 5
- **진입 전 확정된 결정 (v1.5)** — 근거는 5.3 "구현 규약"에, 여기엔 목록만
  - **전이표가 코드의 단일 원천** — enum 기반 맵, 단위테스트가 5×5 전 행렬 대조
  - **전이는 원자적** — `TB_AGENT.VERSION` 낙관적 잠금, 동시 전이 충돌 시 409, 8스레드 테스트
  - **IntegrationRecorder를 전이 지점에 지금부터 배선** — no-op이라도 모든 전이가 호출해야 Phase 6 교체가 서비스 무수정으로 된다. 전이 테스트에 "recorder가 정확히 1회, 올바른 eventType으로 호출됨" 단언을 넣어 배선 누락을 잡는다. 전이가 상태머신(도메인)의 단일 관문이므로 recorder 호출도 거기 둔다 — "1 전이 = 1 이력 + 1 이벤트"가 규약이 아니라 구조로 보장된다
  - **위촉 요건검증은 SPI(`RecruitmentRequirementChecker`)로 분리** — Phase 4 스텁 통과, Phase 5 실판정 교체. 시나리오 1a/1b 분할의 근거
  - **resume은 판정 게이트 없이 수동** — `@Disabled("Phase 5: eligibility 게이트")` 테스트로 남긴다
  - **재위촉 스냅샷 의미론** — 마스터=현재 상태, 과거는 이력에만. `TERMINATE_DT`/`RSN`→NULL, `RECRUIT_ELIG_YN`='N', `FIRST_APPOINT_DT` 보존
  - **계보 순환 방어** — 도입자 지정 시 조상 체인 검사 + 조회 쿼리 `NOCYCLE` 이중 방어
  - **AGENT_CD 시퀀스 채번**(`SEQ_AGENT_CD`, `A00000001`), **`OrgService.close()`의 TB_AGENT 검사** 추가 + `@Disabled` 제거

**Phase 5 — 자격·교육·보증·제재 + 모집자격**
- LICENSE/ASSOC_REG/CROSS_SELL/EDU(+POLICY)/GUARANTEE/SANCTION/MISSELL, RecruitEligibilityService
- 완료 기준: 판정 규칙표(5.4) 전 항목 테스트(경계 포함), 시나리오 3·**1b** 통과, **`AlwaysSatisfiedRequirementChecker` 소스 삭제**, resume 판정 게이트 `@Disabled` 해제
- **진입 전 확정된 결정 (v1.6)** — 근거는 5.4 "구현 규약"에, 여기엔 목록만
  - **판정과 집행 분리** — `evaluate(agentId, asOfDate)`는 순수 함수(전이 없음), 전이는 `AgentEligibilityReconciler`가 관문으로. Phase 7 배치가 판정 함수 재사용
  - **보수교육 null 기저선** — 무이력이면 `LAST_APPOINT_DT` + 주기. 무이력/기한내/도과 3케이스 테스트
  - **경계는 inclusive** — 보수교육/보증 `>= 오늘`(당일 유효), 제재 `START<=오늘<=END`(양끝 포함, END null=무기한). 앵커 Clock 경계 테스트
  - **종합 YN 정의** — 공통 게이트(상태·보수교육·보증·제재) 통과 AND 모집가능 종목 ≥ 1. 변액만 없어도 Y
  - **복수 보증은 합산** 기준으로 `MIN_GRNT_AMT` 비교
  - **`RECRUIT_ELIG_YN`은 마지막 계산 결과** — 실시간·배치 둘 다 갱신, 종합 판정이 실제로 바뀔 때만 `agent.eligibility.changed` 발행
  - **재위촉 요건 신선도** — `REG_EDU_REUSE_ON_REAPPOINT` 정책값(기본 Y)
  - **스텁 삭제가 완료 기준인 이유**: `NoOpIntegrationRecorder`는 기능이 빠질 뿐이지만 `AlwaysSatisfiedRequirementChecker`는 **통제를 끄는** 스텁이다 — 살아남으면 무자격 위촉이 통과한다. 주입 교체가 아니라 **소스 제거**로 컴파일 대상에서 사라져야 회귀가 불가능하다

**Phase 6 — 연계**
- IntegrationRecorder(ChangeLog+Outbox), insuhr-relay(웹훅+서명+재시도), Pull API, 구독자 관리
- 완료 기준: WireMock 수신 검증, 시나리오 5 통과, 동일 aggId 순서 보장(실패→보류→재시도→전송), **구독자 격리**(죽은 구독자가 남을 안 막음 + 비활성 구독자 미전송분 SKIPPED로 Outbox 요약 수렴), **기록 실패 시 업무 롤백**, 페이로드 민감정보 부재 테스트
- ⚠️ 페이로드 직렬화는 Jackson 3(`tools.jackson`) 기준 — 9.2 직렬화 항목 참조
- **진입 전 확정된 결정 (v1.6)** — 근거는 9.1·9.2·9.4에, 여기엔 목록만
  - **Jackson 3 `JsonMapper`**를 domain의 recorder 실구현에서 쓴다(common은 여전히 0의존성). jjwt-gson과 격리 공존. 페이로드는 9.3 스키마 + `schemaVersion=1`
  - **페이로드 민감정보 부재를 테스트로 강제** — 모든 eventType 페이로드에 RRN·계좌·연락처 원문이 없음을 단언(9.3, 10장 통제의 일부)
  - **순서 게이트** — 같은 `aggType+aggId`에 미전송 선행이 있으면 후행 보류(9.2). 실패→보류→재시도→전송 WireMock 테스트
  - **Pull 커서 워터마크 지연** — `SYNC_WATERMARK_SECONDS`(기본 5) 이내 행은 반환 안 함(9.4 시퀀스 갭 함정)
  - **at-least-once + eventUuid 멱등**, 서명에 타임스탬프 포함(9.2)
  - **`NoOpIntegrationRecorder` 소스 삭제**, 기존 "recorder 1회 호출" 단언은 ChangeLog+Outbox 행 존재 단언으로 승격
  - **relay 첫 기동**: validate 전용 Flyway(4.2), `SYSTEM-relay` AuditorAware 폴백(13.2 Phase 2), SECRET_ENC AES 키 환경변수 주입
  - **Kafka는 포트 뒤로 미뤄 Phase 8 선택 과제**(v1.6 조정). 이번 Phase는 `EventPublisher` 포트 + 웹훅 구현만 — 위험은 채널 수가 아니라 순서·멱등·유실 의미론에 있다

**Phase 7 — 배치**
- 8장의 잡 10종 (우선순위: eligibilityRefresh → guaranteeExpiry → continuingEduNotice → futureAppointApply → hrSnapshotFile → 나머지)
- 완료 기준: 시나리오 2 + **6b**(배치 래퍼) 통과, 잡 재실행 멱등성 테스트, 스냅샷 파일 체크섬 검증
- `futureAppointApplyJob`은 **Phase 3의 `AppointmentApplyService`를 감싸기만 한다** (5.5) — 반영 규칙을 배치에 다시 구현하면 온라인 확정 경로와 배치 경로가 갈라진다
- **진행 상태 (v2.1 — 완료):** §8 로스터 잡 전부(9잡) + V14·V15 그린. 1차(v2.0) 3잡(`eligibilityRefreshJob`·`futureAppointApplyJob`·`hrSnapshotFileJob`) + 후속(v2.1) 6잡(`guaranteeExpiry`·`continuingEduNotice`·`licenseValidity`·`dataQuality`·`annualLeaveGrant`·`outboxDlqSweep`) — 완료 기준 4항 + 후속 기준(V15 + 6잡 + **각 잡 dedup 키 재실행 멱등 테스트** + **알림 조건부 발행** `notice.created`) 전부 통과, 전체 빌드 161개 그린. 구현 결정은 §8 "Phase 7 후속 구현 완료" 참조. **`phase-7` 태그 조건 충족.** (`privacyPurgeJob`은 익명화·`person.anonymized`로 Phase 8, 아래.)

**Phase 8 — 마감**
- privacyPurgeJob, Kafka 발행(relay `EventPublisher` kafka 프로파일), 계좌 복호화 엔드포인트(§7.2 백로그), OpenAPI 문서 정리, README(아키텍처 다이어그램, 실행법, 시나리오 데모 스크립트), 부하 스모크(선택)
- `privacyPurgeJob`의 대상은 **두 종류**다 — 보존기간 경과 인물 + **역할 없는 인물**(8장, 5.2 v1.4). 후자는 REQUIRES_NEW 결정에서 파생된 것이라 놓치기 쉽다
- **`dataQualityJob`은 Phase 8이 아니라 Phase 7 후속으로 편입**(v2.0) — 출력처 `TB_DQ_FINDING`이 V15에서 생기므로 나머지 배치 잡과 함께 묶는다

### 13.3 구현 순서 원칙

1. 각 Phase에서 Flyway → 엔티티 → 도메인 서비스 → 애플리케이션 서비스 → 컨트롤러 → 테스트 순.
2. 이벤트 발행이 필요한 서비스는 Phase 6 이전에는 `IntegrationRecorder` 인터페이스의 no-op 구현을 주입해 두고, Phase 6에서 실제 구현으로 교체 (서비스 코드 수정 없이).
3. 매 Phase 종료 시 `./gradlew build` 전체 그린 + README의 실행 절차 갱신.

---

## 부록 A: 공통코드 초기 데이터

| GRP_CD | 코드 값 |
|---|---|
| ORG_TYPE | HQ_DEPT(본사부서), REGION(지역단), BRANCH(지점), OFFICE(영업소) |
| EMP_TYPE | REGULAR(정규직), CONTRACT(계약직), SALES_SUPPORT(영업지원스탭) |
| EMP_STATUS | ACTIVE(재직), ON_LEAVE(휴직), RESIGNED(퇴직) |
| APPOINT_TYPE | HIRE(입사), PROMOTION(승진), TRANSFER(전보), CONCURRENT(겸직), LEAVE(휴직), RETURN(복직), DISPATCH(파견), RESIGN(퇴직) |
| CHANNEL | FC(전속설계사), TC(텔레마케팅), GA(대리점소속), BANCA(방카), DM(다이렉트) |
| AGENT_STATUS | CANDIDATE, PENDING_ASSOC, ACTIVE, SUSPENDED, TERMINATED |
| TERM_RSN | SELF(자진), COMPANY(회사해촉), DISCIPLINE(징계해촉·재위촉금지), TRANSFER_OUT(타사이동) |
| LICENSE_TYPE | LIFE(생명보험), NONLIFE(손해보험), THIRD(제3보험), VARIABLE(변액보험) |
| LICENSE_STATUS | VALID, SUSPENDED, REVOKED |
| EDU_TYPE | REG(등록교육), CONTINUING(보수교육), VARIABLE(변액자격교육), COMPLIANCE(완전판매교육) |
| GRNT_TYPE | SURETY_INS(보증보험), DEPOSIT(공탁), PERSONAL(인보증) |
| GRNT_STATUS | ACTIVE, EXPIRED, CANCELED |
| SANCTION_ORG | FSS(금융당국), ASSOC(협회), INTERNAL(사내) |
| SANCTION_TYPE | RECRUIT_STOP(모집정지), WARNING(경고), FINE(과태료), DISMISS(해촉) |
| ASSOC | LIFE_ASSOC(생명보험협회), NONLIFE_ASSOC(손해보험협회) |
| GENDER | M, F |
| JOB_GRADE / JOB_TITLE | 사원~부장 / 팀장, 지점장, 영업소장 등 (자유 확장) |
| CONTRACT_TYPE (v1.5) | FC_STD(전속표준위촉), TC_STD(텔레마케팅위촉), GA_CONSIGN(대리점위탁), BANCA_STD(방카표준위촉) |
| BANK (v1.5) | 표준 은행코드 3자리 — 004(국민), 088(신한), 020(우리), 011(농협) 등 (포트폴리오용 소수 시드) |
| DOC_STATUS (v1.4) | DRAFT(기안), CONFIRMED(확정), CANCELED(취소) — 발령 등 결재 문서 상태 |

정책값 초기 데이터(TB_POLICY_CONFIG): `CONT_EDU_CYCLE_MONTHS=24`, `REG_EDU_MIN_HOURS=…(정책)`, `MIN_GRNT_AMT=…(사규 가정값, 복수 보증은 합산 비교 — 5.4 v1.6)`, `REAPPOINT_COOLDOWN_MONTHS=6`, `REG_EDU_REUSE_ON_REAPPOINT=Y(재위촉 시 과거 등록교육 재사용 — v1.6 가정값)`, `PRIVACY_RETENTION_YEARS=…(관계법령 확인 후 설정)`, `PWD_EXPIRE_DAYS=90`
※ 실제 법정 주기·금액·보존기간은 구현/운영 시점의 보험업법령·개인정보보호법령·사규를 확인해 설정값으로 입력한다.

## 부록 B: 핵심 시나리오 — 설계사 위촉 End-to-End

1. 영업소장이 후보 등록: `POST /agents/candidates` (주민번호 → 기존 인물 검사 → PERSON 재사용/생성, AGENT=CANDIDATE)
2. 자격 등록: 생보 판매자격 `POST /agents/{id}/licenses` (VALID)
3. 등록교육 이수 등록: `POST /agents/{id}/educations` (EDU_TYPE=REG)
4. 재정보증 등록: `POST /agents/{id}/guarantees` (보증보험, 기간 1년)
5. 위촉 실행: `POST /agents/{id}/appoint` → 요건검증 PASS → 상태 PENDING_ASSOC, 위촉계약 생성, 이벤트 `agent.status.changed`
6. 협회 등록번호 수신 입력: `POST /agents/{id}/assoc-registrations` → 상태 ACTIVE, 이벤트 `agent.appointed` → 영업시스템이 웹훅으로 수신하여 모집 가능 처리
7. 2년 후 보수교육 미이수 → `eligibilityRefreshJob`이 SUSPENDED 전이, `agent.eligibility.changed` 발행 → 영업시스템 신계약 차단
8. 교육 이수 등록 → 실시간 재판정 ACTIVE 복귀
9. 자진 해촉: `POST /agents/{id}/terminate` → TERMINATED, 협회 말소 이력, `agent.terminated` → 수수료시스템은 Pull API로 최종 상태 대사
10. 8개월 후 재위촉: `POST /agents/{id}/reappoint` → 냉각기간 6개월 경과·금지사유 아님 → CANDIDATE부터 재진행 (AGENT_ID·이력 유지)

---

## 개정 이력

최신 버전이 위에 온다.

| 버전 | 일자 | 내용 |
|---|---|---|
| 2.1 | 2026-07-18 | **Phase 7 후속 완료 — V15 + 남은 6잡** — ① **V15 출력 테이블**(8, V15): `TB_NOTICE_QUEUE`(`UQ(NOTICE_TYPE,TARGET_ID,DUE_DT,MILESTONE)`) + `TB_DQ_FINDING`(`UQ(RULE_CD,TARGET_ID,FOUND_DT)`) — 멱등이 유니크 키에서 나온다. `NoticeQueueDao`/`DqFindingDao`는 팬아웃의 `INSERT…SELECT…WHERE NOT EXISTS` 단문을 재사용(rollback-only 함정 회피, UQ는 백스톱) ② **`guaranteeExpiryJob` 순서 무관 확인**(8): 모집자격 판정이 `STATUS_CD`가 아니라 `FinGuarantee.isActiveOn`의 기간 술어(`END_DT>=asOf`)를 보므로, 물질화 경계(`END_DT<targetDate`)가 판정과 대칭 — `eligibilityRefreshJob`과 순서 무관, 하루 죽어도 refresh가 모집 통제 보장. 물질화는 도메인 `GuaranteeExpiryService`가 **온라인과 같은 규약**(엔티티 변경→`reconcileAsOf`)으로 소유 → "만료 시 재판정 트리거"가 별도 코드 없이 성립 ③ **알림 마일스톤 부등식**(8): `DUE_DT<=targetDate+M`(등식 아님)이라 다운타임 뒤 놓친 마일스톤이 늦게라도 발화, dedup이 중복 차단. 조건부 `notice.created`(행 생성 시에만) ④ **`annualLeaveGrantJob` 회계연도 일괄로 택일**(8): 입사기념일 개별 기산 병기를 정리, 부여일수는 근속 정책값(`ANNUAL_LEAVE_*`, V15 시드), 멱등=`UQ_LEAVE_GRANT` ⑤ **licenseValidity↔dataQuality 경계**(8): 잡은 둘로 유지하되 "룰→FINDING" 골격(`DqRule`/`DqFindingDao`/`DqSweepTasklet`) 공유. licenseValidity=살아있는 설계사의 자격 상태 모순(REVOKED 자격·DEREGISTERED 협회), dataQuality=구조적 결손(폐지 조직 재직자·협회 전무). DQ 잡은 관측만(이벤트 없음) ⑥ **`outboxDlqSweepJob` 관측만**(8): FAILED 집계 + 정체 탐지를 `TB_DQ_FINDING`에, 재전송 없음 — 알림(행동 촉구) vs 발견(운영) 용도 구분 확정 ⑦ 전체 빌드 **161개 그린**(154→+7 잡별 멱등/조건부 발행 테스트), **`phase-7` 태그 부여** |
| 2.0 | 2026-07-18 | **Phase 6 클로즈아웃 2건 + Phase 7 진입 결정** — ① **릴레이 픽업은 락 없는 `SELECT`, `FOR UPDATE SKIP LOCKED` 미사용**(9.2): 단일 인스턴스 가정 하 잠금 불필요, 실수로 2번째 인스턴스가 뜨면 중복 전송(eventUuid 멱등 흡수)·순서 위반 가능성이라는 **문서화된 트레이드오프**로 명시(침묵한 이탈 아님), 스케일아웃은 aggId 파티셔닝 전제 ② **ORA-18716 회피 설정을 구조로 강제**(3.0): 세 application.yml에 복제돼 드리프트로 돌아올 형태였다(1.9 버그가 그 증거) → domain에 `jpa-common.yml`을 두고 api·batch·relay가 `spring.config.import`로 끌어와 "JPA 전 모듈 공통"을 문서가 아니라 구조로 만든다 ③ **Spring Batch 6 / Boot 4 이행 실측**(3.0): 메이저 3연속(Security 7·Jackson 3·Batch 6). 메타 스키마 자동생성 제거(→Flyway V14 소유), `@EnableBatchProcessing` 붙이면 autoconfig backoff(붙이지 않음), 빌더 팩토리 제거(`new JobBuilder/StepBuilder`), `item`/`repeat`→`batch.infrastructure.*` 재배치, 잡 파라미터는 비옵션 인자 ④ **배치 메타테이블 Flyway 소유 V14**(8): `schema-oracle.sql`을 V14로 편입 ⑤ **잡 파라미터 규약**(8): `targetDate`(비옵션 식별 파라미터)를 `asOf`로 도메인에 주입(잡은 시스템 날짜 안 읽음) — 앵커 Clock이 배치까지 관통, 멱등성 = "같은 targetDate 2회=같은 결과". 재실행은 호출자 지정 유니크 `run.id`로 허용하며 **`RunIdIncrementer`는 쓰지 않는다**(Batch 6에서 인크리멘터가 있으면 `start`가 업무 파라미터를 버림 — 3.0/8 실측 정정) ⑥ **`eligibilityRefreshJob` 역할 재정의**(8): 실시간 reconcile이 서 있으니 이 잡은 스냅샷 갱신기가 아니라 **쓰기 없이 날짜 경계가 넘어가는 전이(보수교육 도과·보증 만료)의 유일한 포착자** — Reader는 ID만 페이징, Processor가 기존 reconciler 호출, REQUIRED로 청크 트랜잭션 합류. 시나리오 2 완결 ⑦ **`outboxRetryJob`→`outboxDlqSweepJob` 재정의**(8): 백오프 재시도는 릴레이가 `NEXT_RETRY_AT`로 소유하므로 잡은 재전송하지 않고 **정체 감시(한도 소진 FAILED 집계·오래 머문 상태 탐지)만** — 이중 전송 경합 방지 ⑧ **`privacyPurgeJob`(Phase 8) 대상·이벤트 확정**(8): 보존기간 경과 + 역할 없는 인물 두 종류, 익명화는 `person.anonymized` 발행(업무키만, 민감정보 부재 규칙 준수) ⑨ **Phase 7 구현 실측 — Batch 6 런타임 API**(3.0): Reader/Writer 무인자 생성자 제거(빌더 우선), `.chunk(int,tx)` deprecated→`.chunk(int).transactionManager()`+`.skipListener()`, `@Value("#{jobParameters['x']}")` 지연바인딩 `null`→`#{stepExecution}`로 읽기, 인크리멘터+업무 파라미터 비호환 ⑩ **크립토/Jackson도 구조로 강제**(3.0): 세 yml에 복제된 `insuhr.crypto`를 domain의 `crypto-common.yml`로 모아 import(jpa-common.yml과 같은 원칙), `OutboxIntegrationRecorder`가 요구하는 `ObjectMapper` 빈은 domain이 `spring-boot-jackson`을 `api`로 물어 web 없는 batch에도 공급. Phase 7 잡 3종(eligibilityRefresh·futureAppointApply·hrSnapshotFile) + 테스트 그린(154개), 시나리오 2·6b·멱등·스냅샷 체크섬 통과 ⑪ **restart 미사용 명시**(8): 매 실행이 새 JobInstance라 실패 인스턴스 restart 의미론을 의도적으로 안 쓴다 — 재계산 멱등(5.5)이 안전의 전제 ⑫ **§6.3 누락 보강 + V15 설계**(8): §8이 참조하던 "알림 대기 테이블"이 6.3 목록에 없던 누락을 정정 — 범용 `TB_NOTICE_QUEUE`(`UQ(NOTICE_TYPE,TARGET_ID,DUE_DT,MILESTONE)`로 멱등)·`TB_DQ_FINDING` 2종으로 채우고 `INSERT…WHERE NOT EXISTS`+조건부 `notice.created` 발행(futureAppoint 패턴 재사용). `dataQualityJob`을 Phase 8→Phase 7 후속으로 이동, Phase 8은 privacyPurge+Kafka+계좌복호화+마감으로 정리. **`phase-7` 태그는 남은 6잡(V15 선행)까지 보류** |
| 1.9 | 2026-07-18 | **Phase 6 구현 완료 반영** — ① **연계 엔드포인트 권한 분리**(7.2): `/sync/*`는 외부 시스템 계정(`sync.read`/`sync.export`, SYSTEM), `/admin/subscribers`·`/admin/outbox/resend`는 연계 운영자(`integration.admin`, IT_ADMIN). 데이터 수신 주체와 연계 관리 주체가 달라 권한을 나눈다(V13 시드) ② **relay 2단계 폴러 구현 실증**(9.2): 팬아웃(JdbcClient `INSERT..SELECT..WHERE NOT EXISTS`, `READY→FANNED_OUT`/대상없음 `SENT`) + 전달(순서 게이트 (구독자,aggId), 종결집합 {SENT,SKIPPED}, HMAC 서명 `timestamp+"."+body`, 백오프 `NEXT_RETRY_AT` 영속). WireMock 시나리오 5 / 동일 aggId 순서 / 구독자 격리+비활성 SKIPPED 수렴 그린 ③ **ORA-18716 회피 설정은 JPA 전 모듈 공통**(3.0): `preferred_instant_jdbc_type=TIMESTAMP`+`jdbc.time_zone=UTC`가 api뿐 아니라 batch·relay에도 필요 — 누락 시 감사컬럼 Instant 읽기에서 실패(릴레이 테스트로 실증, batch도 선반영). 테스트 150개 그린 |
| 1.8 | 2026-07-18 | **릴레이 세션 진입 — 팬아웃 구현 의미론 4건 + JdbcClient 쓰기 예외** — ① **JdbcClient 팬아웃 쓰기 예외**(4.3): 전달 레코드 생성은 JPA가 아니라 `INSERT ... SELECT ... WHERE NOT EXISTS` 한 문장 — `UQ(EVENT_ID,SUBSCRIBER_ID)` 위반의 rollback-only 오염(Phase 2 함정) 회피, UQ는 크래시 백스톱으로만, 필터가 SQL 한 방이라 성능도 우위 ② **Outbox 소비 표시 `READY→FANNED_OUT`**(9.2, 6.4·V13): 팬아웃은 READY만 집어 처리 후 FANNED_OUT 전이, UQ는 백스톱. Outbox 상태 집합에 FANNED_OUT 추가 ③ **구독자 0명 = 즉시 SENT**(9.2): 전달 레코드 0개면 "전부 종결" 요약이 공허참 → 명시적 READY→SENT(대상 없음)로 종결, 유령 상태 방지 ④ **게이트 종결 집합 {SENT, SKIPPED}**(9.2): 선행 {PENDING, FAILED}면 후행 보류, FAILED도 순서상 후행을 막음, SKIPPED는 안 막음(비활성 종결과 정합) ⑤ **요약 갱신 = 종결 시 형제 검사**(9.2): 단일 릴레이 인스턴스 가정 아래 락 없이 성립, 정합성이 그 가정에 의존함을 명시 ⑥ **전달 신규=PENDING+`NEXT_RETRY_AT` 기본 생성시각**(9.2, V13): 픽업이 `PENDING AND NEXT_RETRY_AT<=현재` 단일 술어로 `IX_DELIVERY_PICKUP` 탑승, NULL 분기 제거 |
| 1.7 | 2026-07-18 | **Phase 6 진입 — 릴레이 팬아웃 파생 결정 (`TB_IF_DELIVERY` 신설에서 따라 나온 3건)** — ① **팬아웃 시점 = 릴레이 픽업 + 2단계 분리**(9.2): 전달 레코드는 recorder가 아니라 릴레이 픽업 시점의 활성 구독자×`TOPIC_FILTER`로 생성. 릴레이 ①팬아웃(Outbox를 `EVENT_ID` 순 소비→전달 레코드 생성) ②전달(구독자별 자기 레코드 순서 전송) 2단계. 순서 게이트가 "같은 (구독자, aggId) 선행 전달 레코드 미전송이면 후행 보류"로 단순화(Outbox 조인 불요), 신규 구독자 비소급 수신이 정의에서 공짜로 나옴 → 9.1 스냅샷+워터마크 온보딩과 정합 ② **비활성 구독자 종결 = SKIPPED**(9.2): 미전송분 남긴 채 `USE_YN='N'`이면 Outbox 요약이 수렴 못 함 → 비활성화 시 미전송(PENDING/FAILED) 레코드를 SKIPPED로 종결, 요약 규칙 "전 레코드 SENT|SKIPPED면 SENT". 구독자 격리 테스트에 케이스 추가(13.2) ③ **폴러 인덱스 2종을 V13에 선반영**(6.7, V13): 전달 픽업 `(SUBSCRIBER_ID, STATUS_CD, NEXT_RETRY_AT)` + 순서 게이트 `(SUBSCRIBER_ID, AGG_TYPE, AGG_ID, DELIVERY_ID)` — 폴링 루프 최다 실행 SQL. `TB_IF_DELIVERY` 표(`SYSTEM_CD`→`SUBSCRIBER_ID` FK, `AGG_TYPE/AGG_ID` 비정규화, `UQ(EVENT_ID,SUBSCRIBER_ID)` 팬아웃 멱등)를 미태그 V13에 확정. ※ V13 체크섬이 바뀌므로 구 V13이 적용된 로컬 도커 볼륨은 릴레이 세션 전 재생성/`flyway repair` 필요(Testcontainers는 무관) |
| 1.6 | 2026-07-18 | **Phase 5 진입 결정 (모집자격 판정)** — ① **판정과 집행 분리**(5.4): `evaluate(agentId, asOfDate)`는 순수 함수(전이 없음), 전이는 `AgentEligibilityReconciler`가 상태머신 관문으로. Phase 7 배치가 판정 함수 재사용, `asOfDate`로 앵커 Clock 테스트 적용 ② **보수교육 null 기저선**(5.4): 무이력이면 기한 = `LAST_APPOINT_DT` + 주기 — null을 통과/미충족 어느 쪽으로 읽어도 버그. 3케이스 테스트 ③ **경계 inclusive 통일**(5.4): 보수교육·보증 `>=오늘`, 제재 `START<=오늘<=END`(END null=무기한) ④ **종합 `RECRUIT_ELIG_YN` 정의**(5.4): 공통 게이트 통과 AND 모집가능 종목≥1 — 변액만 없어도 Y ⑤ **복수 보증 합산**(5.4) 기준 `MIN_GRNT_AMT` 비교 ⑥ **`RECRUIT_ELIG_YN` 의미 승격**(5.4, 6.4): "배치 스냅샷"→"마지막 계산 결과", 실시간·배치 둘 다 갱신, 종합 판정이 바뀔 때만 `agent.eligibility.changed` 발행 ⑦ **재위촉 요건 신선도**(5.4, 부록 A): `REG_EDU_REUSE_ON_REAPPOINT` 정책값(기본 Y) ⑧ **Phase 5 완료 기준에 스텁 소스 삭제 명시**(13.2): `AlwaysSatisfiedRequirementChecker`는 통제를 끄는 스텁이라 주입 교체가 아니라 소스 제거로 컴파일 대상에서 없애야 회귀 불가 — 시나리오 1b + resume 게이트 `@Disabled` 해제 포함 ⑨ **Phase 5 리뷰 — 활성화 워크플로 불변식**(5.4): `registerAssociation`은 자격 삽입→전이→reconcile 순서로, 워크플로 종료 시 스냅샷 YN=evaluate() 결과("낡은 N 창구" 제거, 보증 만료 시 즉시 SUSPENDED가 정답). 자격 쓰기만 reconcile 유발·전이는 유발 안 함(재진입 방지) ⑩ **Phase 6 진입 결정**(9.1·9.2·9.4·13.2): at-least-once+eventUuid 멱등, 서명에 타임스탬프(리플레이 방지), 순서 게이트(같은 aggId 미전송 선행 있으면 후행 보류), Pull 커서 워터마크 지연(`SYNC_WATERMARK_SECONDS` 기본 5 — 시퀀스 갭 함정), 신규 구독자는 스냅샷+워터마크 커서(백필 아님), Jackson 3 `JsonMapper`, 페이로드 민감정보 부재 테스트, `NoOpIntegrationRecorder` 소스 삭제, Kafka는 Phase 8로 미룸 |
| 1.5 | 2026-07-18 | **Phase 3 실증 반영 + Phase 4 진입 결정** — ① **`Clock` 파생 규칙**(6.2): 주입 clock 하나에서 저장 시각은 `Instant.now(clock)`(존 무관, UTC 적재 규약 유지), 업무 날짜는 `LocalDate.now(clock)`(KST), `LocalDateTime.now(clock)`은 금지. 앵커 테스트(`2026-08-01T00:05+09:00`)가 규약을 지킨다 ② **트랜잭션 경계 원칙**(10.1.1): 상태를 바꾸는 도메인 서비스는 자기 `@Transactional`(REQUIRED)을 선언한다 — `recalculate()`가 "온라인에서만 우연히 flush"되던 함정. Phase 7 배치 청크 트랜잭션과도 REQUIRED로 무충돌 ③ **마이그레이션 불변성 정책**(4.2): `phase-*` 태그에 든 파일은 불변, 이후 변경은 ALTER 추가(체크섬 불일치·validate 실패 방지) ④ **전이표가 코드의 단일 원천**(5.3): enum 맵 + 5×5 전 행렬 테스트 ⑤ **전이는 원자적**(5.3, 6.4): `TB_AGENT.VERSION` 낙관적 잠금 → 동시 전이 409, 8스레드 테스트 ⑥ **위촉 요건검증 SPI 분리**(5.3): `RecruitmentRequirementChecker` 스텁(Phase 4)→실판정(Phase 5), IntegrationRecorder와 같은 교체 패턴. **시나리오 1을 1a(Phase 4 422 성형)/1b(Phase 5 실판정)로 분할**(12장) ⑦ **재위촉 스냅샷 의미론**(5.3): 마스터=현재, 과거는 이력에만 — `TERMINATE_DT`/`RSN`→NULL, `RECRUIT_ELIG_YN`='N', `FIRST_APPOINT_DT` 보존 ⑧ **resume 판정 게이트는 Phase 5**(5.3): Phase 4는 수동, `@Disabled` 테스트로 빚 표시 ⑨ **계보 순환 방어**(5.3): 도입자 지정 시 조상 체인 검사 + `NOCYCLE` ⑩ **AGENT_CD 시퀀스 채번**(6.4), **인물당 설계사 0..1 유니크 인덱스**(6.4 `UX_AGENT_PERSON`), **`OrgService.close()`의 TB_AGENT 검사** 완성 + `@Disabled` 제거 |
| 1.4 | 2026-07-17 | **Phase 3 진입 결정 확정 + 개인정보 후속** — ① **발령 스냅샷은 증분 적용이 아니라 재계산**(5.5): 스냅샷을 "기준일 D 이하 CONFIRMED 발령 중 `APPOINT_DT DESC, APPOINT_ID DESC` 첫 행"이라는 함수로 정의하면 배치 멱등성(8장 공통 규칙)과 동일일자 다중 발령의 결정성이 정의에서 따라 나오고, `APPLIED_YN` 같은 두 번째 진실이 필요 없어진다 ② **취소 의미론**(5.5): 반영된 발령은 취소 불가(409), 정정 발령으로만 되돌린다 — 반영된 과거를 지우면 이력·스냅샷이 어긋나고 이미 나간 이벤트의 원인만 사라진다 ③ **시나리오 6을 6a(반영 규칙 — Phase 3)/6b(배치 래퍼 — Phase 7)로 분할**(12장, 13.2): 위험은 배치 배관이 아니라 반영 규칙에 있다 ④ **사번 채번**(6.4): `SEQ_EMP_NO` 기반 무의미 번호 `E00000001`, MAX+1 금지(동시 입사 충돌), 연도 프리픽스 금지(소급·재입사 시 `HIRE_DT`와 어긋나고 사번에서 근속을 추정하는 코드를 부른다) ⑤ **`Clock` 빈 주입**(13.2 Phase 3): 날짜 경계 테스트의 전제이며 Phase 5·7이 그 위에 얹힌다 ⑥ **역할 없는 인물도 파기 대상**(5.2, 8장): v1.3의 "재사용 가능 상태"는 정합성 관점에서만 참이다 — 보유 목적이 없는 개인정보이므로 유예기간 후 `privacyPurgeJob`이 익명화한다 ⑦ **민감정보 복호화는 POST + 사유 본문을 일반 규칙으로 승격**(7.1, 7.2, 10.2): GET이면 열람 사유가 URL로 새어 통제 밖 로그에 남는다. Phase 5 계좌 복호화도 같은 규칙 ⑧ 개정 이력의 중복 `1.1` 행 2건을 하나로 병합(아래 주석) |
| 1.3 | 2026-07-17 | **Phase 2 실증 반영** — ① **제약 위반 복구는 독립 트랜잭션 필수**(5.2, 10.1.1): JPA는 제약 위반 시 트랜잭션을 rollback-only로 만들어 같은 트랜잭션 내 복구 조회조차 막는다(동시 등록 8건 중 7건 실패로 확인). 인물 INSERT는 REQUIRES_NEW + `saveAndFlush` ② 그 결과 인물 행은 호출부와 무관하게 커밋된다 — 역할 없는 인물은 손상이 아니라 재사용 가능 상태(주민번호 기준 멱등) ③ `_YN` 컬럼 매핑: `YnConverter` + `@JdbcTypeCode(SqlTypes.CHAR)`(3.0) ④ v1.2에서 확정한 "이력 = 전체 스냅샷" 전제가 성립함을 확인 — 시점 조회가 쿼리 하나로 풀리고 폐지 조직 필터링에 분기가 불필요(6.6) |
| 1.2 | 2026-07-17 | **Phase 1 리뷰 반영 + Phase 2 진입 결정 확정** — ① **시각 규약**: DDL 기본값을 `SYS_EXTRACT_UTC(SYSTIMESTAMP)`로 교체하고 "모든 TIMESTAMP는 UTC 적재, 표시 변환은 앱 책임"을 6.2에 규약화(V5 마이그레이션 + `TimestampConventionTest`). `SYSTIMESTAMP`는 DB 호스트 타임존을 따라 앱(UTC)과 어긋나는데 로컬 컨테이너가 UTC라 증상이 안 보인다 ② **트랜잭션 경계 규약(10.1.1)**: "쓰고 나서 예외를 던지는" 경로의 롤백 함정과 REQUIRES_NEW의 커넥션 풀 자기 고갈. `login()`의 바깥 트랜잭션 제거 ③ Refresh 토큰 해시 저장·재사용 감지 시 전체 무효화 명문화(10.1) ④ 접근로그는 열람과 같은 트랜잭션(10.2 — 10.1.1의 반대 방향) ⑤ 마스킹 표시값 쓰기 시점 저장(10.2) ⑥ 이력 행은 전체 스냅샷 전제 확정(6.6) — 시점 조회를 diff 재생으로 풀지 않기 위함 ⑦ 중복 방어선은 유니크 제약(5.2), 동시성 테스트 필수 ⑧ `TARGET_PERSON_ID` FK의 전제 명시(6.5) ⑨ `PUT /auth/password` 백로그(7.2), `AuditorAware` 배치 폴백(13.2) |
| 1.1 | 2026-07-17 | **Phase 0·1 실증 반영** (v1.4에서 중복 행 병합 — 본문의 `(v1.1)` 표기는 모두 이 행을 가리킨다) — **Boot 4 모듈화 4건**: ① Flyway 자동설정 모듈 `spring-boot-flyway` 필수(누락 시 마이그레이션 무증상 스킵) ② Testcontainers 2.x 아티팩트명(`testcontainers-oracle-free`) ③ `TestRestTemplate` 제거 → `RestTestClient`/`RestClient` ④ `@EntityScan` 패키지 이동. 부수: Jackson 3 기본화(9.2·13), MapStruct 1.6.3 고정, 3.0 신설, 스키마 소유·migrate 주체·batch/relay validate 전용(4.2), 암호화 유틸 배치 기준·시드 분리(13.2 Phase 1). **Phase 1 실증**: ⑤ Security 7.1 DSL 변경(`authorizeRequests` 제거, `AccessDeniedHandler` 패키지 이동) ⑥ Hibernate 7 `Instant` 매핑 → ORA-18716 회피 설정(3.0) ⑦ Testcontainers 싱글턴 패턴 필요(클래스 단위 생명주기 → ORA-17008) ⑧ jjwt 직렬화 모듈은 `jjwt-gson`(3.0) ⑨ `TB_AUTH_REFRESH_TOKEN`·`TB_USER` 잠금 컬럼 추가(6.3/6.5 — 10.1 요건인데 목록에 없었음) ⑩ 로그인 실패 카운트의 독립 트랜잭션 요건과 비밀번호 이력 미구현 명시(10.1) |
| 1.0 | 2026-07-17 | 최초 작성 |

**개정 원칙**: 본 설계서가 단일 사양이다. 구현 중 설계서와 현실이 어긋나면 코드나 CLAUDE.md에만 우회 기록을 남기지 말고 **이 문서를 개정**하고 위 표에 남긴다. 그렇지 않으면 다음 작업자가 낡은 표기를 근거로 정정을 되돌린다.

---

*본 설계서는 포트폴리오 목적의 가상 시스템 설계이며, 법령 관련 수치(교육 주기, 보증 금액, 개인정보 보존기간 등)는 구현·운영 시 실제 법령과 사규 확인 후 정책값으로 확정해야 한다.*
