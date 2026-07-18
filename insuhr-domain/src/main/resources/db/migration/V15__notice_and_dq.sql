-- V15__notice_and_dq.sql — 알림 대기 큐 + 정합성 점검 리포트 (설계서 8 v2.0, Phase 7 후속)
--
-- §8이 참조하던 "알림 대기 테이블"이 6.3 목록에 없던 누락을 범용 큐 2종으로 보강한다.
-- 잡별 테이블을 만들지 않고 공용 큐로 두어 멱등이 유니크 키에서 공짜로 나오게 한다(설계서 8 v2.0).
--   · TB_NOTICE_QUEUE — 기한 도래 알림 적재처(continuingEduNotice/guaranteeExpiry). 발송은 외부 몫.
--   · TB_DQ_FINDING   — 정합성 점검 리포트(licenseValidity/dataQuality/outboxDlqSweep).

-- ── 알림 대기 큐 ──────────────────────────────────────────────────────────
CREATE TABLE TB_NOTICE_QUEUE (
    NOTICE_ID      NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    NOTICE_TYPE_CD VARCHAR2(30)  NOT NULL,          -- CD:NOTICE_TYPE (CONT_EDU_DUE/GUARANTEE_EXPIRY)
    TARGET_TYPE_CD VARCHAR2(30)  NOT NULL,          -- 알림 대상 종류 (AGENT/GUARANTEE)
    TARGET_ID      NUMBER        NOT NULL,          -- 대상 업무키
    DUE_DT         DATE          NOT NULL,          -- 기한(보수교육 NEXT_DUE_DT / 보증 END_DT)
    MILESTONE_CD   VARCHAR2(10)  NOT NULL,          -- CD:NOTICE_MILESTONE (D60/D30/D7/D0)
    PAYLOAD        CLOB          NOT NULL,          -- 마스킹·업무키만 (민감정보 금지, 설계서 9.3)
    STATUS_CD      VARCHAR2(20)  DEFAULT 'PENDING' NOT NULL,  -- PENDING→SENT (발송은 외부)
    CREATED_AT TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    -- 멱등의 핵심: 같은 (유형,대상,기한,마일스톤) 알림은 한 번만. 잡 재실행·복구 지연에도
    -- INSERT…WHERE NOT EXISTS가 중복을 안 만들고 이 UQ가 크래시 백스톱이 된다(설계서 8 v2.0).
    CONSTRAINT UQ_NOTICE_QUEUE UNIQUE (NOTICE_TYPE_CD, TARGET_ID, DUE_DT, MILESTONE_CD),
    CONSTRAINT CK_NOTICE_PAYLOAD_JSON CHECK (PAYLOAD IS JSON),
    CONSTRAINT CK_NOTICE_STATUS CHECK (STATUS_CD IN ('PENDING', 'SENT'))
);

COMMENT ON TABLE TB_NOTICE_QUEUE IS '기한 도래 알림 적재처(발송은 외부). UQ가 재실행 멱등 보장 (설계서 8 v2.0)';

-- 발송 대기 조회(외부 발송기)용. UQ 선두 컬럼과 겹치지 않는 접근 경로.
CREATE INDEX IX_NOTICE_STATUS ON TB_NOTICE_QUEUE(STATUS_CD, DUE_DT);

-- ── 정합성 점검 리포트 ────────────────────────────────────────────────────
CREATE TABLE TB_DQ_FINDING (
    FINDING_ID     NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    RULE_CD        VARCHAR2(40)  NOT NULL,          -- 점검 룰 코드 (LICENSE_REVOKED_ACTIVE 등)
    TARGET_TYPE_CD VARCHAR2(30)  NOT NULL,          -- AGENT/EMP/OUTBOX/DELIVERY
    TARGET_ID      NUMBER        NOT NULL,
    DETAIL         CLOB          NOT NULL,          -- 발견 상세 (JSON, 민감정보 금지)
    FOUND_DT       DATE          NOT NULL,          -- 점검 기준일(잡 targetDate)
    CREATED_AT TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    -- 같은 룰·대상·일자 발견은 하루 한 행. 10분 주기 outboxDlqSweep 재실행도 이 UQ로 무해하다(설계서 8 v2.0).
    CONSTRAINT UQ_DQ_FINDING UNIQUE (RULE_CD, TARGET_ID, FOUND_DT),
    CONSTRAINT CK_DQ_DETAIL_JSON CHECK (DETAIL IS JSON)
);

COMMENT ON TABLE TB_DQ_FINDING IS 'licenseValidity·dataQuality·outboxDlqSweep 정합성 리포트. UQ(룰,대상,일자)로 재실행 멱등 (설계서 8 v2.0)';

CREATE INDEX IX_DQ_FINDING_RULE ON TB_DQ_FINDING(RULE_CD, FOUND_DT);

-- ── 공통코드 시드 ────────────────────────────────────────────────────────
INSERT INTO TB_CD_GRP (GRP_CD, GRP_NM, DESC_TXT, CREATED_BY) VALUES ('NOTICE_TYPE',      '알림유형',     NULL, 'SYSTEM');
INSERT INTO TB_CD_GRP (GRP_CD, GRP_NM, DESC_TXT, CREATED_BY) VALUES ('NOTICE_MILESTONE', '알림마일스톤', NULL, 'SYSTEM');

INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('NOTICE_TYPE', 'CONT_EDU_DUE',     '보수교육 이수기한', 1, 'SYSTEM');
INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('NOTICE_TYPE', 'GUARANTEE_EXPIRY', '재정보증 만료',     2, 'SYSTEM');

INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('NOTICE_MILESTONE', 'D60', '기한 60일 전', 1, 'SYSTEM');
INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('NOTICE_MILESTONE', 'D30', '기한 30일 전', 2, 'SYSTEM');
INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('NOTICE_MILESTONE', 'D7',  '기한 7일 전',  3, 'SYSTEM');
INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('NOTICE_MILESTONE', 'D0',  '기한 당일',    4, 'SYSTEM');

-- ── 정책값 시드 (⚠️ 전부 포트폴리오용 가정값 — 실제 사규·법령 확인 필요, 설계서 1.4) ──────────────
-- 연차 부여 모델은 §8에서 회계연도 일괄로 확정(입사기념일 개별 기산 아님). annualLeaveGrantJob이 참조.
INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('ANNUAL_LEAVE_BASE_DAYS', '15', 'INT', '연차 기본 부여일수(근속 1년↑). ⚠️ 가정값 — 근로기준법 확인 필요', 'SYSTEM');

INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('ANNUAL_LEAVE_MAX_DAYS', '25', 'INT', '연차 최대 부여일수 상한. ⚠️ 가정값', 'SYSTEM');

INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('ANNUAL_LEAVE_BONUS_PER_YEARS', '2', 'INT', '가산 연차 1일당 필요 근속연수(예: 2년마다 +1일). ⚠️ 가정값', 'SYSTEM');

-- outboxDlqSweepJob 정체 감시 임계(일 단위 판정에 참고). READY/FANNED_OUT/PENDING 지체 리포트.
INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('OUTBOX_STALL_MINUTES', '60', 'INT', 'Outbox/Delivery 정체 감시 임계(분). 참고용 — 잡은 일 경계로 지체를 본다', 'SYSTEM');
