-- V8__employee.sql — 임직원 + 발령 (설계서 5.5, 6.4, 6.5)
--
-- TB_EMP는 마스터가 아니라 '발령의 결과'다. 설계서 5.5(v1.4)가 정의하는 함수의 물질화된 값이다:
--
--   스냅샷(기준일 D) = CONFIRMED 이고 APPOINT_DT <= D 인 발령 중
--                      APPOINT_DT DESC, APPOINT_ID DESC 로 정렬한 첫 행
--
-- 그래서 이 스키마의 진실은 TB_EMP가 아니라 TB_EMP_APPOINT에 있다.

CREATE TABLE TB_EMP (
    EMP_ID        NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    PERSON_ID     NUMBER        NOT NULL,
    EMP_NO        VARCHAR2(10)  NOT NULL,          -- 사번. SEQ_EMP_NO 기반 무의미 번호 (설계서 6.4)
    EMP_TYPE_CD   VARCHAR2(30)  NOT NULL,          -- CD:EMP_TYPE (REGULAR/CONTRACT/SALES_SUPPORT)
    ORG_ID        NUMBER        NOT NULL,          -- 현재 소속 (발령 스냅샷)
    JOB_GRADE_CD  VARCHAR2(30),                    -- 직급 (발령 스냅샷)
    JOB_TITLE_CD  VARCHAR2(30),                    -- 직책 (발령 스냅샷). NULL = 보임 직책 없음
    HIRE_DT       DATE          NOT NULL,
    RESIGN_DT     DATE,
    EMP_STATUS_CD VARCHAR2(30)  NOT NULL,          -- CD:EMP_STATUS (ACTIVE/ON_LEAVE/RESIGNED)
    CREATED_AT TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    CONSTRAINT UQ_EMP_NO UNIQUE (EMP_NO),
    CONSTRAINT FK_EMP_PERSON FOREIGN KEY (PERSON_ID) REFERENCES TB_PERSON(PERSON_ID),
    CONSTRAINT FK_EMP_ORG FOREIGN KEY (ORG_ID) REFERENCES TB_ORG(ORG_ID)
);

COMMENT ON TABLE TB_EMP IS '임직원 마스터. 소속/직급/직책/재직상태는 발령의 비정규화 스냅샷 (설계서 5.5)';
COMMENT ON COLUMN TB_EMP.EMP_NO IS '사번. 의미 없는 시퀀스 번호 — 연도를 넣지 않는다 (설계서 6.4)';
COMMENT ON COLUMN TB_EMP.RESIGN_DT IS '퇴직일. 스냅샷 발령이 RESIGN일 때 그 발령일, 아니면 NULL (재입사 시 다시 NULL)';

CREATE INDEX IX_EMP_ORG ON TB_EMP(ORG_ID);

-- 인물 1명당 임직원 역할은 0..1 (설계서 5.2). 유니크 인덱스가 이 카디널리티의 방어선이다 —
-- 인물 중복차단과 같은 이유로, 검사-후-삽입에 기대지 않는다. 이 인덱스가 PERSON_ID 조회 경로도
-- 겸하므로 별도 IX_EMP_PERSON은 두지 않는다(ORA-01408: 같은 컬럼에 인덱스 중복 금지).
CREATE UNIQUE INDEX UX_EMP_PERSON ON TB_EMP(PERSON_ID);

-- 사번 채번 (설계서 6.4 v1.4).
-- MAX(EMP_NO)+1 은 두 요청이 같은 최대값을 읽는 창이 있어 동시 입사에서 UQ_EMP_NO를 깬다.
-- 시퀀스는 트랜잭션 밖에서 원자적으로 증가하므로 그 창이 없다. 롤백 시 번호 갭이 생기지만
-- 사번이 무의미 번호라 문제되지 않는다 (갭을 없애려는 순간 MAX+1 문제로 돌아간다).
CREATE SEQUENCE SEQ_EMP_NO START WITH 1 INCREMENT BY 1 NOCYCLE;

-- 인사발령.
--
-- 각 행은 '발령 후 전체 상태'를 담는다 (설계서 6.6의 "이력 행은 항상 전체 스냅샷" 전제를 따른다).
-- 변경분(delta)이 아니라 전체 상태라서, 스냅샷 조회가 이 표에서 행 하나를 고르는 것으로 끝난다.
--
-- APPLIED_YN 같은 '반영됨' 플래그를 두지 않는다 (설계서 5.5 v1.4). 플래그는 스냅샷과 어긋날 수
-- 있는 두 번째 진실이 된다. 반영 여부는 CONFIRMED AND APPOINT_DT <= 오늘 로 파생된다.
CREATE TABLE TB_EMP_APPOINT (
    APPOINT_ID      NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    EMP_ID          NUMBER       NOT NULL,
    APPOINT_TYPE_CD VARCHAR2(30) NOT NULL,         -- CD:APPOINT_TYPE (HIRE/PROMOTION/TRANSFER/...)
    APPOINT_DT      DATE         NOT NULL,         -- 발령일. 미래면 예약분
    ORG_ID          NUMBER       NOT NULL,         -- 발령 후 소속
    JOB_GRADE_CD    VARCHAR2(30),                  -- 발령 후 직급
    JOB_TITLE_CD    VARCHAR2(30),                  -- 발령 후 직책. NULL = 보임 직책 없음
    EMP_STATUS_CD   VARCHAR2(30) NOT NULL,         -- 발령 후 재직상태. 발령유형에서 파생 (설계서 5.5)
    DOC_STATUS_CD   VARCHAR2(30) NOT NULL,         -- CD:DOC_STATUS (DRAFT/CONFIRMED/CANCELED)
    RSN_TXT         VARCHAR2(400),
    CREATED_AT TIMESTAMP DEFAULT SYS_EXTRACT_UTC(SYSTIMESTAMP) NOT NULL, CREATED_BY VARCHAR2(50) NOT NULL,
    UPDATED_AT TIMESTAMP, UPDATED_BY VARCHAR2(50),
    CONSTRAINT FK_APPOINT_EMP FOREIGN KEY (EMP_ID) REFERENCES TB_EMP(EMP_ID),
    CONSTRAINT FK_APPOINT_ORG FOREIGN KEY (ORG_ID) REFERENCES TB_ORG(ORG_ID),
    CONSTRAINT CK_APPOINT_DOC_STATUS CHECK (DOC_STATUS_CD IN ('DRAFT', 'CONFIRMED', 'CANCELED'))
);

COMMENT ON TABLE TB_EMP_APPOINT IS '인사발령. 각 행은 발령 후 전체 상태 (설계서 5.5, 6.6)';
COMMENT ON COLUMN TB_EMP_APPOINT.EMP_STATUS_CD IS '발령 후 재직상태. 기안 시 발령유형에서 파생하며 사용자 입력이 아니다';
COMMENT ON COLUMN TB_EMP_APPOINT.APPOINT_DT IS '발령일. 스냅샷 함수의 기준 (설계서 5.5)';

-- 스냅샷 함수 "EMP_ID의 CONFIRMED 발령 중 APPOINT_DT <= D 인 최신 1건"의 접근 경로.
CREATE INDEX IX_APPOINT_SNAPSHOT ON TB_EMP_APPOINT(EMP_ID, DOC_STATUS_CD, APPOINT_DT DESC);

-- futureAppointApplyJob(Phase 7)의 대상 추출 "발령일이 도래한 CONFIRMED 발령" 경로.
CREATE INDEX IX_APPOINT_DUE ON TB_EMP_APPOINT(APPOINT_DT, DOC_STATUS_CD);

-- 문서 상태 공통코드 (기안/확정/취소). 발령 외 결재 문서에서도 재사용할 수 있도록 일반 그룹으로 둔다.
INSERT INTO TB_CD_GRP (GRP_CD, GRP_NM, DESC_TXT, CREATED_BY)
VALUES ('DOC_STATUS', '문서상태', '기안/확정/취소 (설계서 5.5)', 'SYSTEM');

INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('DOC_STATUS', 'DRAFT',     '기안', 1, 'SYSTEM');
INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('DOC_STATUS', 'CONFIRMED', '확정', 2, 'SYSTEM');
INSERT INTO TB_CD (GRP_CD, CD, CD_NM, SORT_ORD, CREATED_BY) VALUES ('DOC_STATUS', 'CANCELED',  '취소', 3, 'SYSTEM');

-- JOB_GRADE(STAFF/SENIOR/MANAGER/DEPUTY/GENERAL)와 JOB_TITLE(TEAM_LEADER/BRANCH_MGR/OFFICE_MGR)은
-- 이미 V2가 시드했으므로 여기서 다시 넣지 않는다 (ORA-00001 방지).
