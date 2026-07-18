package com.portfolio.insuhr.api.agent;

import com.portfolio.insuhr.common.crypto.AesGcmCipher;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.common.privacy.Masker;
import com.portfolio.insuhr.domain.agent.Agent;
import com.portfolio.insuhr.domain.agent.AgentContract;
import com.portfolio.insuhr.domain.agent.AgentContractRepository;
import com.portfolio.insuhr.domain.agent.AgentErrorCode;
import com.portfolio.insuhr.domain.agent.AgentRepository;
import com.portfolio.insuhr.domain.audit.PrivacyAccessLog;
import com.portfolio.insuhr.domain.audit.PrivacyAccessLogRepository;
import com.portfolio.insuhr.domain.audit.PrivacyAccessType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 지급계좌 복호화 유스케이스 (설계서 7.2 백로그, Phase 8).
 *
 * <p>{@code PersonService.decryptRrn}과 <b>같은 규약</b>이다: {@code @Transactional} 한 트랜잭션에서 <b>접근로그를 먼저
 * 남기고</b> 복호화한다 — "기록 없으면 열람 없음"(10.2). 로그 INSERT가 실패하면 전체가 롤백된다(로그인 실패 카운터의 REQUIRES_NEW와 정반대 방향).
 *
 * <p>접근로그는 <b>인물</b>을 대상으로 하므로({@code TARGET_PERSON_ID} FK, 6.5) 계약이 아니라 설계사의 {@code PERSON_ID}로
 * 기록한다. 응답에는 원문과 <b>즉석 계산한 마스킹</b>을 함께 담는다 — 수신측 UI가 확인 표시용으로 재마스킹하다 실수로 원문을 로그에 흘리는 걸 줄인다 (설계서
 * 10.2 v2.2).
 */
@Service
public class AgentAccountService {

  private final AgentRepository agentRepository;
  private final AgentContractRepository contractRepository;
  private final PrivacyAccessLogRepository accessLogRepository;
  private final AesGcmCipher cipher;

  public AgentAccountService(
      AgentRepository agentRepository,
      AgentContractRepository contractRepository,
      PrivacyAccessLogRepository accessLogRepository,
      AesGcmCipher cipher) {
    this.agentRepository = agentRepository;
    this.contractRepository = contractRepository;
    this.accessLogRepository = accessLogRepository;
    this.cipher = cipher;
  }

  /** 설계사의 현재(최신) 위촉계약 지급계좌를 복호화하고 접근로그를 남긴다. */
  @Transactional
  public AccountDecryptResult decryptAccount(
      Long agentId, Long actorUserId, String purpose, String api, String clientIp) {
    if (!StringUtils.hasText(purpose)) {
      throw new BusinessException(AgentErrorCode.PURPOSE_REQUIRED);
    }
    Agent agent =
        agentRepository
            .findById(agentId)
            .orElseThrow(
                () ->
                    new BusinessException(AgentErrorCode.NOT_FOUND, "설계사를 찾을 수 없습니다: " + agentId));

    AgentContract contract =
        contractRepository.findByAgentIdOrderByValidFromDtDesc(agentId).stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException(AgentErrorCode.ACCOUNT_NOT_FOUND));
    if (contract.getAccountEnc() == null) {
      throw new BusinessException(AgentErrorCode.ACCOUNT_NOT_FOUND);
    }

    // 기록 먼저 — 로그가 안 남으면 열람도 없다(같은 트랜잭션).
    accessLogRepository.save(
        PrivacyAccessLog.of(
            actorUserId, agent.getPersonId(), PrivacyAccessType.DECRYPT, api, clientIp, purpose));

    String account = contract.decryptAccount(cipher);
    return new AccountDecryptResult(
        account, Masker.account(account), contract.getBankCd(), contract.getAccountHolderNm());
  }

  /** 원문 + 즉석 마스킹 + 참고용 은행/예금주(평문). */
  public record AccountDecryptResult(
      String account, String accountMasked, String bankCd, String accountHolderNm) {}
}
