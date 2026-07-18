-- V16__privacy_purge.sql — 개인정보 파기 대장 + 파기 익명화 전제 스키마 (설계서 8, 10.2, Phase 8)
--
-- privacyPurgeJob은 인물을 물리 삭제하지 않고 익명화한다(행 유지 — 6.5의 TARGET_PERSON_ID FK 전제).
-- 익명화 = 암호화 컬럼 NULL + RRN 해시 NULL + 이름 마스킹. 그 "NULL 치환"이 스키마상 가능하려면
-- RRN_ENC/RRN_HASH의 NOT NULL을 풀어야 한다(V7는 phase-2 태그라 불변 → 여기서 ALTER).

-- ── 파기 대장 ─────────────────────────────────────────────────────────────
-- §8의 "파기 대장 기록"에 테이블이 없던 누락(6.3 목록 부재, V15의 알림 큐와 같은 계보)을 보강한다.
CREATE TABLE TB_PRIVACY_PURGE_LEDGER (
    LEDGER_ID      NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    PERSON_ID      NUMBER        NOT NULL,          -- 익명화된 인물(행은 유지되므로 FK 성립)
    PURGE_TYPE_CD  VARCHAR2(20)  NOT NULL,          -- CD:PURGE_TYPE (ROLE_ENDED/ORPHAN)
    ELIGIBLE_BASIS CLOB          NOT NULL,          -- 파기 근거 스냅샷 JSON(기준일·정책값). 민감정보 없음
    PURGED_AT      TIMESTAMP     DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL,
    CREATED_AT TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    -- 한 인물은 한 번만 파기된다. 재실행은 대상 술어(RRN_HASH IS NOT NULL)에서 자연 배제되지만
    -- 이 UQ가 크래시 백스톱이다(V15 dedup 철학과 동형). 재등록은 새 PERSON_ID라 충돌하지 않는다.
    CONSTRAINT UQ_PURGE_LEDGER_PERSON UNIQUE (PERSON_ID),
    CONSTRAINT FK_PURGE_LEDGER_PERSON FOREIGN KEY (PERSON_ID) REFERENCES TB_PERSON(PERSON_ID),
    CONSTRAINT CK_PURGE_BASIS_JSON CHECK (ELIGIBLE_BASIS IS JSON),
    CONSTRAINT CK_PURGE_TYPE CHECK (PURGE_TYPE_CD IN ('ROLE_ENDED', 'ORPHAN'))
);

COMMENT ON TABLE TB_PRIVACY_PURGE_LEDGER IS '개인정보 파기 대장. 익명화(행 유지) + 근거 스냅샷 (설계서 8 Phase 8)';

CREATE INDEX IX_PURGE_LEDGER_AT ON TB_PRIVACY_PURGE_LEDGER(PURGED_AT);

-- ── RRN 컬럼 NOT NULL 완화 (파기 익명화 전제) ──────────────────────────────
-- ⚠️ NULL 허용은 오직 "파기 산출물"로서만 정당하다. 신규 인물 생성은 RRN 필수라는 불변식이
-- 스키마가 아니라 코드(Person.register 가드)로 이관된다(설계서 6.4 Phase 8). Oracle의 단일컬럼
-- UNIQUE 인덱스는 전체 NULL 행을 색인하지 않으므로 UQ_PERSON_RRN(RRN_HASH)이 다중 NULL을 허용해
-- 파기된 인물이 여럿이어도 제약이 깨지지 않고, 해시를 지웠으므로 재등록은 새 인물이 된다.
ALTER TABLE TB_PERSON MODIFY (RRN_ENC NULL, RRN_HASH NULL);

-- ── 공통코드 시드 ────────────────────────────────────────────────────────
INSERT INTO TB_CD_GRP (GRP_CD, GRP_NM, DESC_TXT, CREATED_BY) VALUES ('PURGE_TYPE', '파기유형', NULL, 'SYSTEM');
INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('PURGE_TYPE', 'ROLE_ENDED', '역할 종료 후 보존기간 경과', 1, 'SYSTEM');
INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('PURGE_TYPE', 'ORPHAN',     '역할 미생성(무역할) 인물',   2, 'SYSTEM');

-- ── 정책값 시드 (⚠️ 포트폴리오용 가정값 — 실제 관계법령 확인 필요, 설계서 1.4) ────────────────
-- 역할 없는 인물의 유예기간(설계서 5.2 v1.4): 등록 절차가 중단됐다 재개되는 정상 케이스를 죽이지 않기 위함.
INSERT INTO TB_POLICY_CONFIG (POLICY_KEY, POLICY_VAL, VAL_TYPE_CD, DESC_TXT, CREATED_BY)
VALUES ('ORPHAN_PERSON_PURGE_DAYS', '30', 'INT', '역할 미생성 인물 파기 유예(일). ⚠️ 가정값 — 개인정보보호법 확인 필요', 'SYSTEM');
