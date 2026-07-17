-- V3__policy_config.sql — 정책값 (설계서 6.5, 13.1, 부록 A)
--
-- V2(공통코드)와 파일을 분리한 이유: 공통코드는 코드가 의존하는 도메인 상수라 환경 불변이지만,
-- 정책값은 환경별로 달라진다(로컬 가정값 vs 운영 실값). 나중에 repeatable migration이나
-- 환경별 시드로 갈라낼 여지를 남긴다.
--
-- ⚠️ 아래 시드 값은 전부 포트폴리오용 가정값이다. 실제 법정 주기·금액·보존기간은
--    구현/운영 시점의 보험업법령·개인정보보호법령·사규를 확인해 입력해야 한다(설계서 1.4, 부록 A).

CREATE TABLE TB_POLICY_CONFIG (
    POLICY_KEY    VARCHAR2(50)  NOT NULL,
    POLICY_VAL    VARCHAR2(200) NOT NULL,
    VAL_TYPE_CD   VARCHAR2(10)  NOT NULL,          -- INT / DECIMAL / STRING / BOOLEAN / DATE
    DESC_TXT      VARCHAR2(400),
    VALID_FROM_DT DATE          DEFAULT DATE '1900-01-01' NOT NULL,
    VALID_TO_DT   DATE          DEFAULT DATE '9999-12-31' NOT NULL,
    CREATED_AT    TIMESTAMP     DEFAULT SYSTIMESTAMP NOT NULL,
    CREATED_BY    VARCHAR2(50)  NOT NULL,
    UPDATED_AT    TIMESTAMP,
    UPDATED_BY    VARCHAR2(50),
    -- 같은 키의 이력을 유효기간으로 관리한다(설계서 6.6 유효기간형).
    CONSTRAINT PK_POLICY_CONFIG PRIMARY KEY (POLICY_KEY, VALID_FROM_DT),
    CONSTRAINT CK_POLICY_VAL_TYPE CHECK (VAL_TYPE_CD IN ('INT', 'DECIMAL', 'STRING', 'BOOLEAN', 'DATE')),
    CONSTRAINT CK_POLICY_PERIOD CHECK (VALID_FROM_DT <= VALID_TO_DT)
);

COMMENT ON TABLE TB_POLICY_CONFIG IS '정책값. 법령·사규 의존 수치는 하드코딩 금지하고 여기서 읽는다 (설계서 13.1)';

CREATE INDEX IX_POLICY_VALID ON TB_POLICY_CONFIG(POLICY_KEY, VALID_TO_DT);

-- ── 정책값 초기 데이터 (부록 A) ───────────────────────────────────────────
-- 설계서에 기본값이 명시된 것과, '실제 법령 확인 후 설정'으로 남겨진 것을 구분해 주석에 표시한다.

-- 보수교육 주기. 설계서 2장/부록 A 기본값 24개월
INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('CONT_EDU_CYCLE_MONTHS', '24', 'INT', '보수교육 이수 주기(개월). 설계서 기본값 — 실제 법정 주기 확인 필요', 'SYSTEM');

-- 재위촉 제한(냉각) 기간. 설계서 5.3 기본값 6개월
INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('REAPPOINT_COOLDOWN_MONTHS', '6', 'INT', '해촉 후 재위촉 제한기간(개월). 설계서 5.3 기본값', 'SYSTEM');

-- 등록교육 최소 이수시간. 설계서 부록 A에 '(정책)'으로만 표기됨 → 가정값
INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('REG_EDU_MIN_HOURS', '20', 'DECIMAL', '등록교육 최소 이수시간. ⚠️ 가정값 — 법령 확인 필요', 'SYSTEM');

-- 재정보증 최소 금액. 설계서 부록 A에 '(사규 가정값)'으로 표기됨
INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('MIN_GRNT_AMT', '10000000', 'INT', '재정보증 최소 보증금액(원). ⚠️ 사규 가정값', 'SYSTEM');

-- 개인정보 보존기간. 설계서 부록 A에 '(관계법령 확인 후 설정)'으로 표기됨
INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('PRIVACY_RETENTION_YEARS', '5', 'INT', '해촉/퇴직 후 개인정보 보존기간(년). ⚠️ 가정값 — 관계법령 확인 필요', 'SYSTEM');

-- ── 인증 정책 (설계서 10.1) ──────────────────────────────────────────────
INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('PWD_EXPIRE_DAYS', '90', 'INT', '비밀번호 변경 주기(일)', 'SYSTEM');

INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('PWD_MIN_LENGTH', '10', 'INT', '비밀번호 최소 길이', 'SYSTEM');

INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('PWD_REUSE_BLOCK_CNT', '3', 'INT', '재사용 금지할 최근 비밀번호 개수', 'SYSTEM');

INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('LOGIN_FAIL_LOCK_CNT', '5', 'INT', '로그인 연속 실패 잠금 임계값', 'SYSTEM');

INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('ACCESS_TOKEN_TTL_MINUTES', '30', 'INT', 'Access 토큰 유효시간(분)', 'SYSTEM');

INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('REFRESH_TOKEN_TTL_DAYS', '14', 'INT', 'Refresh 토큰 유효기간(일)', 'SYSTEM');
