-- demo/seed.sql — 데모용 로그인 계정 (설계서 부록 B 실연 스크립트 전제)
--
-- 마이그레이션은 역할·권한만 시드하고 계정(TB_USER)은 만들지 않는다(운영 환경마다 계정 체계가 달라서).
-- /admin/users 생성 엔드포인트도 없으므로, 데모는 이 SQL로 계정을 심고 시작한다. 재실행 가능(먼저 지운다).
--
-- 계정 하나(demo / demo1234!)에 데모에 필요한 역할을 전부 매핑한다:
--   HR_ADMIN(조직·직원·person.rrn.decrypt) + SALES_ADMIN(설계사 전권·agent.account.decrypt)
--   + IT_ADMIN(구독자 관리) + SYSTEM(sync). PWD_HASH는 BCrypt('demo1234!'), 접두어 없음
--   (SecurityConfig가 순수 BCryptPasswordEncoder).
--
-- 실행: docker exec -i insuhr-oracle sqlplus -S insuhr/insuhr@localhost:1521/FREEPDB1 < demo/seed.sql

DELETE FROM TB_USER_ROLE WHERE USER_ID IN (SELECT USER_ID FROM TB_USER WHERE LOGIN_ID = 'demo');
DELETE FROM TB_USER WHERE LOGIN_ID = 'demo';

INSERT INTO TB_USER (LOGIN_ID, PWD_HASH, USER_TYPE_CD, STATUS_CD, CREATED_BY)
VALUES ('demo', '$2a$10$jZjX.fv5tlxrBR4WZ4uzP.POSpmcexRAMswQdHJv9j2uMRzLwzDoq', 'HUMAN', 'ACTIVE', 'DEMO');

INSERT INTO TB_USER_ROLE (USER_ID, ROLE_CD, CREATED_BY)
SELECT u.USER_ID, r.ROLE_CD, 'DEMO'
  FROM TB_USER u
 CROSS JOIN (SELECT 'HR_ADMIN'    AS ROLE_CD FROM DUAL
             UNION ALL SELECT 'SALES_ADMIN' FROM DUAL
             UNION ALL SELECT 'IT_ADMIN'    FROM DUAL
             UNION ALL SELECT 'SYSTEM'      FROM DUAL) r
 WHERE u.LOGIN_ID = 'demo';

COMMIT;

PROMPT demo 계정 생성 완료 — 로그인: demo / demo1234!
