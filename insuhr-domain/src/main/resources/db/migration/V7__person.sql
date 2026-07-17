-- V7__person.sql — 인물 마스터 (설계서 5.2, 6.4, 6.5, 6.8)
--
-- 인물은 하나, 역할은 여럿(설계서 1.4). 주민등록번호 기준으로 유일하며 임직원(TB_EMP)·설계사(TB_AGENT)가
-- 각각 0..1로 붙는다. 주민번호 원문은 이 테이블에만 있고 타 테이블은 PERSON_ID 대리키만 참조한다.

CREATE TABLE TB_PERSON (
    PERSON_ID      NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    PERSON_NM      VARCHAR2(100) NOT NULL,
    RRN_ENC        VARCHAR2(512) NOT NULL,         -- AES-256-GCM 암호문. 'v1:'처럼 키버전 프리픽스 포함 (10.3)
    RRN_HASH       VARCHAR2(64)  NOT NULL,         -- SHA-256(주민번호+pepper). 동일인 검사/검색용 (6.8)
    BIRTH_DT       DATE          NOT NULL,
    GENDER_CD      VARCHAR2(10)  NOT NULL,         -- CD:GENDER
    MOBILE_ENC     VARCHAR2(256),
    -- 목록 표시용 마스킹 값 (설계서 10.2 v1.2).
    -- 이 컬럼이 없으면 목록 20건마다 복호화 20회가 발생한다. 마스킹은 원문이 필요 없는
    -- 표시 문자열이므로 쓰기 시점에 계산해 저장하고 목록은 이 컬럼만 읽는다.
    MOBILE_MASKED  VARCHAR2(20),
    EMAIL          VARCHAR2(100),
    NATIONALITY_CD VARCHAR2(10)  DEFAULT 'KR' NOT NULL,
    CREATED_AT TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    -- 중복 인물의 실제 방어선 (설계서 5.2 v1.2).
    -- POST /persons/check-duplicate 는 UX용이고, 동시 등록 레이스를 막는 것은 이 제약이다.
    CONSTRAINT UQ_PERSON_RRN UNIQUE (RRN_HASH)
);

COMMENT ON TABLE TB_PERSON IS '인물 마스터. 주민번호 기준 유일 (설계서 5.2)';
COMMENT ON COLUMN TB_PERSON.RRN_ENC IS 'AES-256-GCM 암호문. 복호화는 person.rrn.decrypt 권한 + 접근로그 필수 (10.2)';
COMMENT ON COLUMN TB_PERSON.RRN_HASH IS 'SHA-256+pepper. 암호문은 IV 때문에 등치 검색이 불가해 별도로 둔다 (6.8)';
COMMENT ON COLUMN TB_PERSON.MOBILE_MASKED IS '목록 표시용 마스킹 값. 원문 변경 시 함께 갱신 (10.2)';

CREATE TABLE TB_PERSON_ADDR (
    ADDR_ID       NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    PERSON_ID     NUMBER        NOT NULL,
    ADDR_TYPE_CD  VARCHAR2(10)  NOT NULL,          -- CD:ADDR_TYPE (HOME/WORK)
    ZIP_CD        VARCHAR2(10),
    ADDR_ENC      VARCHAR2(1024) NOT NULL,         -- AES-256-GCM
    VALID_FROM_DT DATE          NOT NULL,
    VALID_TO_DT   DATE          DEFAULT DATE '9999-12-31' NOT NULL,
    CREATED_AT TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    CONSTRAINT FK_PERSON_ADDR_PERSON FOREIGN KEY (PERSON_ID) REFERENCES TB_PERSON(PERSON_ID),
    CONSTRAINT CK_PERSON_ADDR_PERIOD CHECK (VALID_FROM_DT <= VALID_TO_DT)
);

COMMENT ON TABLE TB_PERSON_ADDR IS '주소 이력 (유효기간형 — 설계서 6.6)';

CREATE INDEX IX_PERSON_ADDR_PERSON ON TB_PERSON_ADDR(PERSON_ID, VALID_TO_DT);

-- ── Phase 1이 FK 없이 남긴 참조를 연결한다 ────────────────────────────────
-- TB_PERSON이 없어 컬럼만 만들어 뒀던 것들 (V4 주석 참조).

ALTER TABLE TB_USER ADD CONSTRAINT FK_USER_PERSON
    FOREIGN KEY (PERSON_ID) REFERENCES TB_PERSON(PERSON_ID);

-- 이 FK는 "PERSON 행이 물리 삭제되지 않는다"는 전제에 의존한다 (설계서 6.5 v1.2).
-- privacyPurgeJob(8장)이 삭제가 아니라 익명화(행 유지)이기 때문에 성립한다.
-- 파기 방식을 하드 삭제로 바꾸면 이 FK가 파기를 막는다.
ALTER TABLE TB_PRIVACY_ACCESS_LOG ADD CONSTRAINT FK_PRIVACY_LOG_PERSON
    FOREIGN KEY (TARGET_PERSON_ID) REFERENCES TB_PERSON(PERSON_ID);

-- ── 공통코드 ──────────────────────────────────────────────────────────────

INSERT INTO TB_CD_GRP (GRP_CD, GRP_NM, DESC_TXT, CREATED_BY)
VALUES ('ADDR_TYPE', '주소유형', NULL, 'SYSTEM');

INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('ADDR_TYPE', 'HOME', '자택', 1, 'SYSTEM');
INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('ADDR_TYPE', 'WORK', '직장', 2, 'SYSTEM');
