-- V1__init.sql — Phase 0 부트스트랩 (설계서 13.2)
--
-- 이 마이그레이션은 업무 테이블을 만들지 않는다. Flyway 연결·마이그레이션 파이프라인이
-- 동작하는지 확인하는 것이 목적이며, 실제 스키마는 Phase 1 이후 V2~ 에서 쌓는다.
--
-- 규칙(설계서 13.1): 스키마 변경은 오직 이 디렉터리의 마이그레이션 파일로만 한다.
-- 엔티티 ddl-auto 로 스키마를 만들지 않는다(validate 만 허용).

CREATE TABLE TB_SCHEMA_BOOTSTRAP (
    BOOTSTRAP_ID  NUMBER GENERATED AS IDENTITY PRIMARY KEY,
    NOTE_TXT      VARCHAR2(200) NOT NULL,
    CREATED_AT    TIMESTAMP DEFAULT SYSTIMESTAMP NOT NULL,
    CREATED_BY    VARCHAR2(50)  NOT NULL,
    UPDATED_AT    TIMESTAMP,
    UPDATED_BY    VARCHAR2(50)
);

COMMENT ON TABLE TB_SCHEMA_BOOTSTRAP IS 'Phase 0 스키마 부트스트랩 확인용. Phase 1에서 제거 예정';

INSERT INTO TB_SCHEMA_BOOTSTRAP (NOTE_TXT, CREATED_BY)
VALUES ('InsuHR schema initialized', 'SYSTEM');
