-- V17__account_decrypt_perm.sql — 지급계좌 복호화 권한 (설계서 7.2 백로그, Phase 8)
--
-- 계좌 복호화는 person.rrn.decrypt와 같은 통제(POST + 사유 + 접근로그)를 따른다. 권한은 설계사 채널을
-- 운영하는 SALES_ADMIN에 부여한다(RRN이 HR_ADMIN·SALES_ADMIN 둘 다였던 것과 달리, 지급계좌는 설계사
-- 위촉계약 고유 정보라 영업관리로 한정). 마스킹 컬럼은 두지 않는다 — 계좌는 목록 노출 필드가 아니라
-- 복호화 엔드포인트로만 열리므로(설계서 6.8/10.2 v2.2), 마스킹 컬럼 패턴의 근거(목록 N회 복호화 회피)가 없다.

INSERT INTO TB_ROLE_PERM (ROLE_CD, PERM_CD, CREATED_BY)
VALUES ('SALES_ADMIN', 'agent.account.decrypt', 'SYSTEM');
