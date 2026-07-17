# InsuHR — 보험사 통합 인사관리시스템 설계서

> **문서 목적**: Claude Code에서 이 문서만으로 시스템을 단계적으로 구현할 수 있도록 작성한 구현용 설계서 (포트폴리오 프로젝트)
> **버전**: 1.1 / 작성일: 2026-07-17 / 최종 개정: 2026-07-17 ([개정 이력](#개정-이력))
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

### 5.4 모집자격(Eligibility) 판정 규칙

`RecruitEligibilityService.evaluate(agentId)` 는 아래 항목을 모두 검사해 종합 판정과 사유 목록을 반환한다. API 조회 시 실시간 계산 + 일배치로 `TB_AGENT.RECRUIT_ELIG_YN` 스냅샷 갱신의 이중 구조.

| 검사 항목 | PASS 조건 | 실패 시 효과 |
|---|---|---|
| 위촉상태 | STATUS = ACTIVE | 모집 불가 |
| 판매자격 | 해당 종목 자격 상태 = VALID | 해당 종목 모집 불가 |
| 협회등록 | 해당 종목 협회 등록 유효(말소 아님) | 해당 종목 모집 불가 |
| 보수교육 | 다음 이수기한(NEXT_DUE_DATE) ≥ 오늘 | 전 종목 모집 정지 대상 |
| 변액자격 | 변액상품에 한해 변액 판매자격 VALID | 변액상품만 모집 불가 |
| 재정보증 | 유효기간 내 + 최소보증금액(정책값) 이상 | 신계약 모집 정지 대상 |
| 제재 | 진행 중인 모집정지 제재 없음 | 제재 범위에 따라 정지 |

판정 결과는 종목별로 산출한다: `{생보: OK, 손보: OK(교차), 변액: BLOCKED(자격없음)}`

### 5.5 임직원 발령 모델

- 발령(`TB_EMP_APPOINT`)은 **이벤트 소싱형 이력**: 발령유형(입사/승진/전보/겸직/휴직/복직/파견/퇴직) + 발령일 + 발령 후 소속/직급/직책.
- `TB_EMP`의 현재 소속/직급/직책 컬럼은 최신 발령을 반영한 **비정규화 스냅샷**이며, 발령 확정 트랜잭션에서 함께 갱신한다.
- 발령은 기안(DRAFT) → 확정(CONFIRMED) → (필요시) 취소(CANCELED)의 문서 상태를 가지며, 확정 시에만 스냅샷 갱신과 Outbox 발행이 일어난다. 미래일자 발령은 확정 상태로 저장 후 발령일 새벽 배치가 스냅샷에 반영한다.

---

## 6. 데이터베이스 설계 (Oracle)

### 6.1 명명 규칙

- 테이블: `TB_{도메인}_{명사}` 대문자 스네이크. 예) `TB_AGENT_LICENSE`
- PK: 단일 NUMBER 대리키 `{엔티티}_ID`, 시퀀스 `SQ_{테이블명}` 또는 `GENERATED AS IDENTITY` (본 프로젝트는 **IDENTITY** 사용)
- 업무키(자연키)는 UNIQUE 제약으로 별도 유지: `EMP_NO`, `AGENT_CD` 등
- 컬럼 접미사: `_CD`(공통코드값), `_YN`(CHAR(1) 'Y'/'N'), `_DT`(DATE, 일자), `_AT`(TIMESTAMP, 일시), `_AMT`(금액), `_ENC`(암호문), `_HASH`(검색용 해시)
- 인덱스: `IX_{테이블약어}_{컬럼}`, FK: `FK_{자식}_{부모}`

### 6.2 공통(감사) 컬럼 — 모든 업무 테이블에 포함

```sql
CREATED_AT   TIMESTAMP    DEFAULT SYSTIMESTAMP NOT NULL,
CREATED_BY   VARCHAR2(50) NOT NULL,
UPDATED_AT   TIMESTAMP,
UPDATED_BY   VARCHAR2(50)
```

JPA에서는 `@MappedSuperclass BaseEntity` + Auditing으로 자동 세팅.

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
CREATE INDEX IX_EMP_PERSON ON TB_EMP(PERSON_ID);
CREATE INDEX IX_EMP_ORG ON TB_EMP(ORG_ID);

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
    RECRUIT_ELIG_YN   CHAR(1) DEFAULT 'N' NOT NULL, -- 모집자격 배치 스냅샷
    ELIG_CHECKED_AT   TIMESTAMP,
    CREATED_AT TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    CONSTRAINT UQ_AGENT_CD UNIQUE (AGENT_CD),
    CONSTRAINT FK_AGENT_PERSON FOREIGN KEY (PERSON_ID) REFERENCES TB_PERSON(PERSON_ID),
    CONSTRAINT FK_AGENT_ORG FOREIGN KEY (ORG_ID) REFERENCES TB_ORG(ORG_ID),
    CONSTRAINT FK_AGENT_RECRUITER FOREIGN KEY (RECRUITER_AGENT_ID) REFERENCES TB_AGENT(AGENT_ID)
);
CREATE INDEX IX_AGENT_PERSON ON TB_AGENT(PERSON_ID);
CREATE INDEX IX_AGENT_ORG_STATUS ON TB_AGENT(ORG_ID, AGENT_STATUS_CD);

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
| TB_PRIVACY_ACCESS_LOG | USER_ID, TARGET_PERSON_ID, ACCESS_TYPE_CD(VIEW/DECRYPT/EXPORT), ACCESS_AT, MENU_OR_API, CLIENT_IP, PURPOSE_TXT |
| TB_AUDIT_LOG | TABLE_NM, PK_VAL, ACTION_CD(C/U/D), BEFORE_JSON, AFTER_JSON, ACTED_AT, ACTED_BY |

### 6.6 이력 관리 전략

| 유형 | 대상 | 방식 |
|---|---|---|
| 유효기간형 | 조직, 위촉계약, 주소 | `VALID_FROM_DT ~ VALID_TO_DT` 컬럼. 현재행 = TO가 9999-12-31 |
| 이벤트형 | 발령, 위촉상태 전이, 협회 등록/말소 | 사건 1건 = 1행 append-only, 수정 대신 취소행 추가 |
| 감사형 | PERSON, AGENT, EMP 등 마스터 | Application 계층 AOP로 before/after JSON을 `TB_AUDIT_LOG`에 기록 (트리거 미사용 — 테스트 용이성) |

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

### 7.2 엔드포인트 목록

**인증 (AUT)**

| Method | Path | 설명 |
|---|---|---|
| POST | /auth/login | 로그인 → Access(30분)/Refresh(14일) 발급 |
| POST | /auth/refresh | 토큰 재발급 |
| POST | /auth/system-token | 시스템 계정 토큰 발급 (연계용) |

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
| GET | /persons/{personId}/rrn | 주민번호 복호화 조회 — `person.rrn.decrypt` 권한 + 사유 입력 필수, 접근로그 기록 |
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
| GET | /agents/{agentId}/genealogy | 도입 계보 트리 (재귀: CONNECT BY 또는 재귀 CTE) |

**연계 (IFC)** — 시스템 계정 전용

| Method | Path | 설명 |
|---|---|---|
| GET | /sync/changes?aggType=&cursor=&size= | 변경분 Pull (TB_IF_CHANGE_LOG 키셋 커서) |
| GET | /sync/snapshot/agents?asOfDate= · /employees · /orgs | 전체 스냅샷 조회 (대량, 스트리밍) |
| GET/POST/PUT | /admin/subscribers | 구독 시스템 관리 |
| POST | /admin/outbox/{eventId}/resend | 이벤트 수동 재전송 |

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

모든 잡은 `insuhr-batch` 모듈. 실행: `java -jar insuhr-batch.jar --job.name={잡명} [--targetDate=yyyy-MM-dd]`
공통 규칙: 재실행 안전(멱등), chunk 기반(기본 500), 실패 시 skip 로그 + 잡 실패 처리, 잡 결과는 Spring Batch 메타테이블 + 운영 로그.

| Job | 주기 | 처리 내용 |
|---|---|---|
| `eligibilityRefreshJob` | 매일 05:00 | 전 ACTIVE/SUSPENDED 설계사의 모집자격 재판정 → `RECRUIT_ELIG_YN` 스냅샷 갱신. ACTIVE인데 판정 FAIL이면 SUSPENDED 자동 전이 + 사유 기록 + Outbox 발행. SUSPENDED인데 사유 해소되면 ACTIVE 복귀 |
| `continuingEduNoticeJob` | 매일 06:00 | 보수교육 이수기한 D-60/D-30/D-7 대상 추출 → 알림 대기 테이블 적재(알림 발송 자체는 외부 시스템 몫, 이벤트 발행) |
| `guaranteeExpiryJob` | 매일 06:10 | 재정보증 만료 도래(D-30 알림 / D-0 EXPIRED 처리). 만료 처리 시 eligibility 재판정 트리거 |
| `licenseValidityJob` | 매일 06:20 | 자격 정지/말소 반영, 협회 말소 상태와 위촉상태 정합성 점검 리포트 |
| `futureAppointApplyJob` | 매일 00:10 | 발령일이 도래한 CONFIRMED 발령을 TB_EMP 스냅샷에 반영 + Outbox 발행 |
| `annualLeaveGrantJob` | 매년 01-01 / 입사기념일 | 연차 부여 (근속 기준 정책값 참조) |
| `hrSnapshotFileJob` | 매일 04:00 | 조직/임직원/설계사 전체 및 증분 스냅샷 파일 생성 (9.5 레이아웃) → TB_IF_FILE_LOG 기록 |
| `outboxRetryJob` | 10분 주기 | FAILED 이벤트 재전송 (지수 백오프, 최대 재시도 후 DLQ 상태) — relay 프로세스 보조 |
| `privacyPurgeJob` | 매월 1일 | 해촉/퇴직 후 보존기간(정책값) 경과 인물의 개인정보 익명화 처리(암호화 컬럼 NULL + 이름 마스킹 치환), 파기 대장 기록 |
| `dataQualityJob` | 매일 07:00 | 정합성 점검: 소속조직 폐지 상태인 재직자, ACTIVE인데 협회등록 없는 설계사 등 → 리포트 적재 |

배치 Reader는 `JdbcPagingItemReader` 또는 `JdbcCursorItemReader`(JdbcClient SQL 재사용) 사용, JPA Reader 지양(대량 처리 성능).

---

## 9. 대외 연계·동기화 설계

### 9.1 3계층 동기화 전략

| 계층 | 방식 | 지연 | 대상 시스템 예 | 용도 |
|---|---|---|---|---|
| ① 이벤트 Push | Transactional Outbox → Relay → Webhook(기본) / Kafka(프로파일) | 초~분 | 영업시스템, 그룹웨어/SSO | 상태 변경 즉시 전파 (위촉/해촉/발령/조직개편) |
| ② 변경분 Pull | `GET /sync/changes` 커서 API | 수 분 (폴링) | 수수료시스템, 급여시스템 | 수신측 주도 동기화, 유실 복구 |
| ③ 배치 파일 | 일 1회 전체+증분 파일 | 1일 | DW, 레거시(파일 인터페이스만 가능한 시스템) | 전수 대사(reconciliation), 초기적재 |

세 계층은 동일한 `TB_IF_CHANGE_LOG`/`TB_IF_OUTBOX` 원천에서 파생되므로 상호 검증이 가능하다. 수신 시스템은 ①로 실시간 반영하고 ③으로 일 단위 대사하는 조합을 권장.

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
  - **구현 주의 (v1.1)**: 로그인 실패 카운트는 반드시 **독립 트랜잭션**(`REQUIRES_NEW`)으로 커밋한다. 인증 실패는 예외로 끝나고 그 예외가 트랜잭션을 롤백시키므로, 같은 트랜잭션에서 카운트를 올리면 롤백과 함께 사라져 **계정이 영원히 잠기지 않는다**. `saveAndFlush`는 해결책이 아니다(flush ≠ commit).
  - **미구현 (Phase 1 시점)**: "최근 3개 재사용 금지"와 "90일 변경 강제"는 비밀번호 변경 엔드포인트가 7.2에 없어 보류했다. 변경 API를 추가할 때 비밀번호 이력 테이블(`TB_USER_PWD_HIST`)과 함께 구현한다. 정책값(`PWD_REUSE_BLOCK_CNT`, `PWD_EXPIRE_DAYS`)은 이미 시드돼 있다.
- 인가: `TB_ROLE_PERM`의 `{리소스}.{행위}` 권한을 JWT 클레임에 싣고 메서드 보안(`@PreAuthorize("hasAuthority('agent.write')")`)으로 검사.
- 기본 역할: `HR_ADMIN`, `SALES_ADMIN`(설계사 관리), `BRANCH_MANAGER`(소속 조직 한정), `SUPPORT_STAFF`(조회 위주), `SELF`(본인), `SYSTEM`(연계), `IT_ADMIN`.
- **행 수준 접근 통제**: BRANCH_MANAGER/SUPPORT_STAFF는 본인 소속 조직 트리 하위 데이터만 조회 가능 — 공통 `OrgScopeFilter`가 쿼리 조건에 조직 ID 목록을 강제 주입.

### 10.2 개인정보 통제 매트릭스

| 행위 | 필요 권한 | 추가 통제 |
|---|---|---|
| 목록/상세 조회 (마스킹) | `*.read` | — |
| 주민번호/계좌 복호화 | `person.rrn.decrypt` 등 | 사유 입력 필수 + TB_PRIVACY_ACCESS_LOG 기록 |
| 대량 내보내기(파일/스냅샷 API) | `sync.export` | 시스템 계정 한정, 전 건 접근로그 |
| 파기 | 배치 전용 | 파기 대장(감사로그) 기록 |

마스킹 규칙(공통 유틸): 이름 `김*수`, 주민번호 `900101-1******`, 휴대폰 `010-****-1234`, 계좌 뒤 4자리만.

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
1. 요건 미충족 위촉 시도 → 422 + 사유 배열
2. 보수교육 기한 도과 배치 → SUSPENDED 전이 + 이벤트 발행 확인
3. 재정보증 갱신 등록 → eligibility 재판정으로 ACTIVE 복귀
4. 해촉 → 재위촉 냉각기간 검사 (금지사유 해촉이면 영구 거부)
5. Outbox 이벤트 전송 실패 → 재시도 → 성공 시 SEND_LOG 2건, 수신측 멱등 처리
6. 미래일자 발령 확정 → 발령일 배치 후 스냅샷 반영
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

**Phase 3 — 임직원**
- TB_EMP + 발령(기안/확정/취소/미래발령), 인사기록카드 6종, 휴가
- 완료 기준: 시나리오 테스트 6번(미래발령) 통과, 발령 확정 시 스냅샷·이벤트 정합

**Phase 4 — 설계사 코어**
- TB_AGENT + 상태머신(AgentLifecycleService), 후보등록/위촉/정지/해촉/재위촉/이동, 위촉계약, 계보 조회
- 완료 기준: 전이표 전 케이스 단위테스트, 시나리오 1·4 통과

**Phase 5 — 자격·교육·보증·제재 + 모집자격**
- LICENSE/ASSOC_REG/CROSS_SELL/EDU(+POLICY)/GUARANTEE/SANCTION/MISSELL, RecruitEligibilityService
- 완료 기준: 판정 규칙표(5.4) 전 항목 테스트, 시나리오 3 통과

**Phase 6 — 연계**
- IntegrationRecorder(ChangeLog+Outbox), insuhr-relay(웹훅+서명+재시도, kafka 프로파일), Pull API, 구독자 관리
- 완료 기준: WireMock 수신 검증, 시나리오 5 통과, 동일 aggId 순서 보장 테스트
- ⚠️ 페이로드 직렬화는 Jackson 3(`tools.jackson`) 기준 — 9.2 직렬화 항목 참조

**Phase 7 — 배치**
- 8장의 잡 10종 (우선순위: eligibilityRefresh → guaranteeExpiry → continuingEduNotice → futureAppointApply → hrSnapshotFile → 나머지)
- 완료 기준: 시나리오 2 통과, 잡 재실행 멱등성 테스트, 스냅샷 파일 체크섬 검증

**Phase 8 — 마감**
- privacyPurgeJob, dataQualityJob, OpenAPI 문서 정리, README(아키텍처 다이어그램, 실행법, 시나리오 데모 스크립트), 부하 스모크(선택)

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

정책값 초기 데이터(TB_POLICY_CONFIG): `CONT_EDU_CYCLE_MONTHS=24`, `REG_EDU_MIN_HOURS=…(정책)`, `MIN_GRNT_AMT=…(사규 가정값)`, `REAPPOINT_COOLDOWN_MONTHS=6`, `PRIVACY_RETENTION_YEARS=…(관계법령 확인 후 설정)`, `PWD_EXPIRE_DAYS=90`
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

| 버전 | 일자 | 내용 |
|---|---|---|
| 1.0 | 2026-07-17 | 최초 작성 |
| 1.1 | 2026-07-17 | **Boot 4 모듈화 관련 4건 정정 (Phase 0 실증)** — ① Flyway: 자동설정 모듈 `spring-boot-flyway` 필수 명시(누락 시 마이그레이션 무증상 스킵) ② Testcontainers: 2.x 아티팩트명(`testcontainers-oracle-free`)으로 교체 ③ 테스트 HTTP 클라이언트: `TestRestTemplate` 제거 → `RestTestClient`/`RestClient` ④ `@EntityScan` 패키지 이동. 부수: Jackson 3 기본화를 9.2·13(Phase 6)에 반영, MapStruct 1.6.3 고정, Boot 4 이행 주의 절(3.0) 신설. 아키텍처 결정 추가: 스키마 소유·migrate 주체·batch/relay validate 전용(4.2), 암호화 유틸 배치 기준·시드 분리(13.2 Phase 1) |
| 1.1 | 2026-07-17 | **Phase 1 실증 반영** — ① Security 7.1 DSL 변경(`authorizeRequests` 제거, `AccessDeniedHandler` 패키지 이동) ② Hibernate 7 `Instant` 매핑 → ORA-18716 회피 설정(3.0) ③ Testcontainers 싱글턴 패턴 필요(클래스 단위 생명주기 → ORA-17008) ④ jjwt 직렬화 모듈은 `jjwt-gson`(3.0) ⑤ `TB_AUTH_REFRESH_TOKEN` 추가와 `TB_USER` 잠금 컬럼 추가(6.3/6.5 — 10.1 요건인데 목록에 없었음) ⑥ 로그인 실패 카운트의 독립 트랜잭션 요건과 비밀번호 이력 미구현 명시(10.1) |

**개정 원칙**: 본 설계서가 단일 사양이다. 구현 중 설계서와 현실이 어긋나면 코드나 CLAUDE.md에만 우회 기록을 남기지 말고 **이 문서를 개정**하고 위 표에 남긴다. 그렇지 않으면 다음 작업자가 낡은 표기를 근거로 정정을 되돌린다.

---

*본 설계서는 포트폴리오 목적의 가상 시스템 설계이며, 법령 관련 수치(교육 주기, 보증 금액, 개인정보 보존기간 등)는 구현·운영 시 실제 법령과 사규 확인 후 정책값으로 확정해야 한다.*
