-- V11__agent.sql — 설계사 코어: 마스터 + 위촉상태 전이 이력 + 위촉계약 (설계서 5.3, 6.4, 6.5)
--
-- TB_AGENT가 이 프로젝트의 상태머신 중심이다 (설계서 5.3):
--   CANDIDATE → PENDING_ASSOC → ACTIVE ⇄ SUSPENDED → TERMINATED → (재위촉) CANDIDATE
--
-- 마스터는 '현재 상태'만 담고, 상태가 어떻게 바뀌어 왔는지는 TB_AGENT_APPOINT_HIST(append-only)에
-- 남는다. 재위촉은 같은 AGENT_ID를 재사용하며 현재상태 컬럼을 리셋하되 이력은 보존한다 (설계서 5.3 v1.5).

CREATE TABLE TB_AGENT (
    AGENT_ID           NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    PERSON_ID          NUMBER        NOT NULL,
    AGENT_CD           VARCHAR2(10)  NOT NULL,       -- 설계사코드. SEQ_AGENT_CD 기반 무의미 번호 (업무키)
    CHANNEL_CD         VARCHAR2(30)  NOT NULL,       -- CD:CHANNEL (FC/TC/GA/BANCA/DM)
    ORG_ID             NUMBER        NOT NULL,       -- 소속 영업조직 (지점/영업소)
    AGENT_STATUS_CD    VARCHAR2(30)  NOT NULL,       -- CD:AGENT_STATUS (5.3 상태머신)
    FIRST_APPOINT_DT   DATE,                         -- 최초 위촉일. 재위촉해도 불변
    LAST_APPOINT_DT    DATE,                         -- 최근(재)위촉일
    TERMINATE_DT       DATE,                         -- 해촉일. 재위촉 시 NULL로 리셋(이력엔 남음)
    TERMINATE_RSN_CD   VARCHAR2(30),                 -- CD:TERM_RSN. 재위촉 시 NULL로 리셋
    RECRUITER_AGENT_ID NUMBER,                       -- 도입자 (자기참조). 계보의 부모
    RECRUIT_ELIG_YN    CHAR(1) DEFAULT 'N' NOT NULL, -- 모집자격 배치 스냅샷 (Phase 5 eligibilityRefreshJob이 갱신)
    ELIG_CHECKED_AT    TIMESTAMP,
    VERSION            NUMBER(19) DEFAULT 0 NOT NULL, -- 낙관적 잠금 (설계서 5.3 v1.5 — 동시 전이 방어)
    CREATED_AT TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    CONSTRAINT UQ_AGENT_CD UNIQUE (AGENT_CD),
    CONSTRAINT FK_AGENT_PERSON FOREIGN KEY (PERSON_ID) REFERENCES TB_PERSON(PERSON_ID),
    CONSTRAINT FK_AGENT_ORG FOREIGN KEY (ORG_ID) REFERENCES TB_ORG(ORG_ID),
    CONSTRAINT FK_AGENT_RECRUITER FOREIGN KEY (RECRUITER_AGENT_ID) REFERENCES TB_AGENT(AGENT_ID),
    CONSTRAINT CK_AGENT_STATUS CHECK
        (AGENT_STATUS_CD IN ('CANDIDATE', 'PENDING_ASSOC', 'ACTIVE', 'SUSPENDED', 'TERMINATED'))
);

COMMENT ON TABLE TB_AGENT IS '설계사 마스터. 현재 상태 + 모집자격 캐시. 전이 이력은 TB_AGENT_APPOINT_HIST (설계서 5.3)';
COMMENT ON COLUMN TB_AGENT.AGENT_CD IS '설계사코드. 의미 없는 시퀀스 번호 — 사번과 같은 규칙 (설계서 6.4)';
COMMENT ON COLUMN TB_AGENT.FIRST_APPOINT_DT IS '최초 위촉일. 재위촉해도 바뀌지 않는 불변 사실 (설계서 5.3 v1.5)';
COMMENT ON COLUMN TB_AGENT.TERMINATE_DT IS '해촉일. 재위촉 시 NULL로 리셋 — 과거 해촉은 이력에만 남는다 (설계서 5.3 v1.5)';
COMMENT ON COLUMN TB_AGENT.VERSION IS '낙관적 잠금 버전. 동시 전이 충돌 감지 → 진 쪽 409 (설계서 5.3 v1.5)';

-- 인물 1명당 설계사 역할도 0..1 (설계서 5.2). TB_EMP와 대칭 — 유니크 인덱스가 카디널리티 방어선이자
-- PERSON_ID 조회 경로다. 같은 컬럼에 일반 인덱스를 또 두면 ORA-01408이므로 IX_AGENT_PERSON은 두지 않는다.
CREATE UNIQUE INDEX UX_AGENT_PERSON ON TB_AGENT(PERSON_ID);

-- 현황판/목록의 "조직별·상태별 설계사" 조회 경로 (설계서 6.7).
CREATE INDEX IX_AGENT_ORG_STATUS ON TB_AGENT(ORG_ID, AGENT_STATUS_CD);

-- 계보 조회(CONNECT BY)가 도입자 링크를 타고 내려가는 경로.
CREATE INDEX IX_AGENT_RECRUITER ON TB_AGENT(RECRUITER_AGENT_ID);

-- 설계사코드 채번 (설계서 6.4 v1.5). EMP_NO(SEQ_EMP_NO)와 같은 규칙:
-- MAX+1 금지(동시 위촉에서 UQ_AGENT_CD가 깨진다), 무의미 번호, 롤백 갭 허용.
CREATE SEQUENCE SEQ_AGENT_CD START WITH 1 INCREMENT BY 1 NOCYCLE;

-- 위촉상태 전이 이력 (append-only).
--
-- 상태머신의 모든 전이가 여기 1행씩 남긴다 (설계서 5.3). 재위촉이 현재상태 컬럼을 리셋해도
-- "언제 왜 해촉됐다가 언제 다시 후보가 됐는지"는 이 표에 온전히 남는다 — 마스터=현재, 과거는 여기.
CREATE TABLE TB_AGENT_APPOINT_HIST (
    HIST_ID         NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    AGENT_ID        NUMBER       NOT NULL,
    FROM_STATUS_CD  VARCHAR2(30),                    -- 전이 전 상태. 최초 후보등록이면 NULL
    TO_STATUS_CD    VARCHAR2(30) NOT NULL,           -- 전이 후 상태
    EVENT_DT        DATE         NOT NULL,           -- 전이 업무일
    RSN_CD          VARCHAR2(30),                    -- 사유코드 (해촉:CD:TERM_RSN, 정지 등)
    RSN_TXT         VARCHAR2(400),
    ORG_ID          NUMBER,                          -- 전이 당시 소속 (이동 추적)
    CREATED_AT TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    CONSTRAINT FK_AHIST_AGENT FOREIGN KEY (AGENT_ID) REFERENCES TB_AGENT(AGENT_ID)
);

COMMENT ON TABLE TB_AGENT_APPOINT_HIST IS '위촉상태 전이 이력. 전이 1건 = 1행 append-only (설계서 5.3, 6.6)';

-- "이 설계사의 전이 이력을 시간순으로" 조회 경로.
CREATE INDEX IX_AHIST_AGENT ON TB_AGENT_APPOINT_HIST(AGENT_ID, HIST_ID);

-- 위촉계약. 위촉(appoint) 성공 시 생성된다 (설계서 부록 B 5단계).
--
-- 지급계좌는 암호화 저장(ACCOUNT_ENC, AES-256-GCM, v1: 키버전 프리픽스). 복호화 엔드포인트는
-- Phase 5(수수료 연계 시에만). 유효기간형 이력(6.6) — 현재 계약은 VALID_TO_DT = 9999-12-31.
CREATE TABLE TB_AGENT_CONTRACT (
    CONTRACT_ID       NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    AGENT_ID          NUMBER        NOT NULL,
    CONTRACT_TYPE_CD  VARCHAR2(30)  NOT NULL,        -- CD:CONTRACT_TYPE (위촉계약 유형)
    CONTRACT_DT       DATE          NOT NULL,
    COMM_RULE_VER     VARCHAR2(30),                  -- 수수료규정 버전
    BANK_CD           VARCHAR2(30),                  -- CD:BANK (지급계좌 은행)
    ACCOUNT_ENC       VARCHAR2(256),                 -- 지급계좌 암호문 (v1:...)
    ACCOUNT_HOLDER_NM VARCHAR2(100),
    VALID_FROM_DT     DATE          NOT NULL,
    VALID_TO_DT       DATE          DEFAULT DATE '9999-12-31' NOT NULL,
    CREATED_AT TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    CONSTRAINT FK_CONTRACT_AGENT FOREIGN KEY (AGENT_ID) REFERENCES TB_AGENT(AGENT_ID)
);

COMMENT ON TABLE TB_AGENT_CONTRACT IS '위촉계약. 위촉 성공 시 생성. 지급계좌는 암호화 (설계서 6.5, 6.8)';
COMMENT ON COLUMN TB_AGENT_CONTRACT.ACCOUNT_ENC IS '지급계좌 AES-256-GCM 암호문. 복호화는 Phase 5 수수료 연계 (설계서 6.8)';

CREATE INDEX IX_CONTRACT_AGENT ON TB_AGENT_CONTRACT(AGENT_ID, VALID_FROM_DT);

-- 위촉계약 유형 공통코드 (설계서 부록 A v1.5 추가). 채널별 표준계약을 기본으로 둔다.
INSERT INTO TB_CD_GRP (GRP_CD, GRP_NM, DESC_TXT, CREATED_BY)
VALUES ('CONTRACT_TYPE', '위촉계약유형', '설계사 위촉계약 유형 (설계서 6.5)', 'SYSTEM');

INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('CONTRACT_TYPE', 'FC_STD',    '전속표준위촉',   1, 'SYSTEM');
INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('CONTRACT_TYPE', 'TC_STD',    '텔레마케팅위촉', 2, 'SYSTEM');
INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('CONTRACT_TYPE', 'GA_CONSIGN','대리점위탁',     3, 'SYSTEM');
INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('CONTRACT_TYPE', 'BANCA_STD', '방카표준위촉',   4, 'SYSTEM');

-- 은행 공통코드 (설계서 부록 A v1.5 추가). 표준 은행코드 3자리. 포트폴리오용 소수만 시드.
INSERT INTO TB_CD_GRP (GRP_CD, GRP_NM, DESC_TXT, CREATED_BY)
VALUES ('BANK', '은행', '지급계좌 은행코드 (표준 3자리)', 'SYSTEM');

INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('BANK', '004', '국민은행', 1, 'SYSTEM');
INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('BANK', '088', '신한은행', 2, 'SYSTEM');
INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('BANK', '020', '우리은행', 3, 'SYSTEM');
INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('BANK', '011', '농협은행', 4, 'SYSTEM');

-- CHANNEL / AGENT_STATUS / TERM_RSN 은 이미 V2가 시드했으므로 다시 넣지 않는다 (ORA-00001 방지).
