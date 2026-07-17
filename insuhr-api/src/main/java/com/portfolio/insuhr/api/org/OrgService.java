package com.portfolio.insuhr.api.org;

import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.agent.AgentRepository;
import com.portfolio.insuhr.domain.agent.AgentStatus;
import com.portfolio.insuhr.domain.emp.EmpRepository;
import com.portfolio.insuhr.domain.emp.EmpStatus;
import com.portfolio.insuhr.domain.integration.IntegrationEvent;
import com.portfolio.insuhr.domain.integration.IntegrationRecorder;
import com.portfolio.insuhr.domain.org.Org;
import com.portfolio.insuhr.domain.org.OrgChangeType;
import com.portfolio.insuhr.domain.org.OrgErrorCode;
import com.portfolio.insuhr.domain.org.OrgHist;
import com.portfolio.insuhr.domain.org.OrgHistRepository;
import com.portfolio.insuhr.domain.org.OrgQueryDao;
import com.portfolio.insuhr.domain.org.OrgRepository;
import com.portfolio.insuhr.domain.org.OrgType;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * 조직 유스케이스 (설계서 7.2 ORG).
 *
 * <p><b>기준정보 변경 = 업무테이블 + 이력 + Outbox, 한 트랜잭션</b>(설계서 4.3, 9.2). 조직은 타 시스템의 기준정보이므로 변경이 유실되거나 반대로
 * 반영 안 된 이벤트가 나가면 안 된다. {@link IntegrationRecorder}는 Phase 6 이전에는 no-op이며, 그때 서비스 코드 수정 없이 실제 발행으로
 * 바뀐다(설계서 13.3).
 */
@Service
public class OrgService {

  private final OrgRepository orgRepository;
  private final OrgHistRepository orgHistRepository;
  private final OrgQueryDao orgQueryDao;
  private final EmpRepository empRepository;
  private final AgentRepository agentRepository;
  private final IntegrationRecorder integrationRecorder;
  private final ObjectMapper objectMapper;

  public OrgService(
      OrgRepository orgRepository,
      OrgHistRepository orgHistRepository,
      OrgQueryDao orgQueryDao,
      EmpRepository empRepository,
      AgentRepository agentRepository,
      IntegrationRecorder integrationRecorder,
      ObjectMapper objectMapper) {
    this.orgRepository = orgRepository;
    this.orgHistRepository = orgHistRepository;
    this.orgQueryDao = orgQueryDao;
    this.empRepository = empRepository;
    this.agentRepository = agentRepository;
    this.integrationRecorder = integrationRecorder;
    this.objectMapper = objectMapper;
  }

  /** 조직 신설 (설계서 7.2 {@code POST /orgs}). */
  @Transactional
  public Long create(
      String orgCd,
      String orgNm,
      OrgType orgType,
      String upOrgCd,
      int sortOrd,
      LocalDate validFromDt) {
    if (orgRepository.existsByOrgCd(orgCd)) {
      throw new BusinessException(OrgErrorCode.DUPLICATE_CODE, "이미 사용 중인 조직코드입니다: " + orgCd);
    }
    Org parent = upOrgCd == null ? null : requireOrg(upOrgCd);

    Org org = orgRepository.save(Org.create(orgCd, orgNm, orgType, parent, sortOrd, validFromDt));
    // ID가 있어야 스냅샷이 완성되므로 save 이후에 이력을 남긴다.
    recordChange(org, OrgChangeType.CREATE, null, validFromDt, null, "org.created");
    return org.getId();
  }

  /** 명칭 변경 / 상위 이관 (설계서 7.2 {@code PUT /orgs/{orgId}}). */
  @Transactional
  public void update(
      String orgCd, String newName, String newUpOrgCd, LocalDate effectiveDt, String reason) {
    Org org = requireOrg(orgCd);
    Map<String, Object> before = org.toSnapshot();

    OrgChangeType changeType = OrgChangeType.RENAME;

    if (newName != null && !newName.equals(org.getOrgNm())) {
      org.rename(newName);
    }

    if (newUpOrgCd != null) {
      Org newParent = requireOrg(newUpOrgCd);
      guardAgainstCycle(org, newParent);
      org.moveTo(newParent);
      relevelSubTree(org);
      changeType = OrgChangeType.MOVE;
    }

    recordChange(org, changeType, before, effectiveDt, reason, "org.updated");
  }

  /**
   * 조직 폐지 (설계서 7.2 {@code POST /orgs/{orgId}/close}).
   *
   * <p>하위조직이나 소속인원이 있으면 409. 행을 지우지 않고 유효기간을 닫는다 — 과거 발령·위촉 이력이 이 조직을 참조하기 때문이다.
   */
  @Transactional
  public void close(String orgCd, LocalDate closeDate, String reason) {
    Org org = requireOrg(orgCd);
    Map<String, Object> before = org.toSnapshot();

    List<Org> activeChildren = orgRepository.findByUpOrgIdAndUseYnTrue(org.getId());
    if (!activeChildren.isEmpty()) {
      throw new BusinessException(
          OrgErrorCode.HAS_CHILDREN,
          "하위 조직이 " + activeChildren.size() + "개 있어 폐지할 수 없습니다. 조직코드=" + orgCd);
    }

    // 소속 재직자가 있으면 폐지 불가 (설계서 7.2). 퇴직자는 세지 않는다 — 퇴직자의 소속은
    // "마지막 소속"이라는 이력적 사실이라 폐지를 막을 이유가 없다(막으면 사람이 나간 조직을 영원히 못 닫는다).
    if (empRepository.existsByOrgIdAndEmpStatusCdNot(org.getId(), EmpStatus.RESIGNED.name())) {
      throw new BusinessException(
          OrgErrorCode.HAS_MEMBERS, "소속 재직 임직원이 있어 폐지할 수 없습니다. 조직코드=" + orgCd);
    }
    // 소속 현역 설계사도 폐지를 막는다 (설계서 5.3 v1.5로 갚는 나머지 절반). 해촉(TERMINATED)은 세지 않는다 —
    // 재직자/해촉의 관계는 임직원의 재직/퇴직과 대칭이다.
    if (agentRepository.existsByOrgIdAndAgentStatusCdNot(
        org.getId(), AgentStatus.TERMINATED.name())) {
      throw new BusinessException(
          OrgErrorCode.HAS_MEMBERS, "소속 현역 설계사가 있어 폐지할 수 없습니다. 조직코드=" + orgCd);
    }

    org.close(closeDate);
    recordChange(org, OrgChangeType.CLOSE, before, closeDate, reason, "org.closed");
  }

  /** 조직 상세 (설계서 7.2 {@code GET /orgs/{orgId}}). */
  @Transactional(readOnly = true)
  public Org get(String orgCd) {
    return requireOrg(orgCd);
  }

  /**
   * 조직도 트리 (설계서 7.2 {@code GET /orgs/tree?type=&asOfDate=}).
   *
   * @param asOfDate 기준일자. 그 시점의 조직도를 복원한다
   */
  @Transactional(readOnly = true)
  public List<OrgTreeNode> tree(LocalDate asOfDate, OrgType orgType) {
    List<OrgQueryDao.OrgSnapshot> rows =
        orgQueryDao.findTreeAsOf(asOfDate, orgType == null ? null : orgType.name());
    return assemble(rows);
  }

  /** 평면 목록을 트리로 조립한다. 부모가 걸러진 노드(타입 필터 등)는 최상위로 올라온다. */
  private List<OrgTreeNode> assemble(List<OrgQueryDao.OrgSnapshot> rows) {
    Map<Long, OrgTreeNode> byId = new java.util.LinkedHashMap<>();
    for (OrgQueryDao.OrgSnapshot row : rows) {
      byId.put(
          row.orgId(),
          new OrgTreeNode(
              row.orgId(),
              row.orgCd(),
              row.orgNm(),
              row.orgTypeCd(),
              row.orgLvl(),
              new ArrayList<>()));
    }

    List<OrgTreeNode> roots = new ArrayList<>();
    for (OrgQueryDao.OrgSnapshot row : rows) {
      OrgTreeNode node = byId.get(row.orgId());
      OrgTreeNode parent = row.upOrgId() == null ? null : byId.get(row.upOrgId());
      if (parent == null) {
        roots.add(node);
      } else {
        parent.children().add(node);
      }
    }
    return roots;
  }

  private Org requireOrg(String orgCd) {
    return orgRepository
        .findByOrgCd(orgCd)
        .orElseThrow(
            () -> new BusinessException(OrgErrorCode.NOT_FOUND, "조직을 찾을 수 없습니다: " + orgCd));
  }

  /** 자기 하위 트리로 이관하면 순환이 생겨 CONNECT BY가 무한 루프에 빠진다. */
  private void guardAgainstCycle(Org org, Org newParent) {
    if (orgQueryDao.findSubTreeIds(org.getId()).contains(newParent.getId())) {
      throw new BusinessException(
          OrgErrorCode.INVALID_HIERARCHY, "자기 하위 조직으로는 이관할 수 없습니다. 조직코드=" + org.getOrgCd());
    }
  }

  /** 상위가 바뀌면 하위 트리의 계층 깊이가 전부 따라 움직인다. */
  private void relevelSubTree(Org moved) {
    Deque<Org> queue = new ArrayDeque<>();
    queue.add(moved);
    while (!queue.isEmpty()) {
      Org current = queue.poll();
      for (Org child : orgRepository.findByUpOrgId(current.getId())) {
        child.relevel(current.getOrgLvl() + 1);
        queue.add(child);
      }
    }
  }

  /**
   * 이력 + Outbox를 같은 트랜잭션에서 기록한다 (설계서 9.2).
   *
   * <p>스냅샷은 전체 상태다 — 시점 조회가 이 한 건으로 복원되어야 하기 때문이다(설계서 6.6).
   */
  private void recordChange(
      Org org,
      OrgChangeType changeType,
      Map<String, Object> before,
      LocalDate effectiveDt,
      String reason,
      String eventType) {

    Map<String, Object> after = org.toSnapshot();

    orgHistRepository.save(
        OrgHist.of(
            org.getId(),
            changeType,
            before == null ? null : objectMapper.writeValueAsString(before),
            objectMapper.writeValueAsString(after),
            effectiveDt,
            reason));

    integrationRecorder.record(
        changeType == OrgChangeType.CREATE
            ? IntegrationEvent.created(eventType, "ORG", org.getId(), org.getOrgCd(), after)
            : IntegrationEvent.updated(eventType, "ORG", org.getId(), org.getOrgCd(), after));
  }

  /** 조직도 트리 노드. */
  public record OrgTreeNode(
      Long orgId,
      String orgCd,
      String orgNm,
      String orgTypeCd,
      Integer orgLvl,
      List<OrgTreeNode> children) {}
}
