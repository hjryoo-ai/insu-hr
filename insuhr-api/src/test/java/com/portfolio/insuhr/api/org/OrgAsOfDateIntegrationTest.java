package com.portfolio.insuhr.api.org;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.portfolio.insuhr.api.support.AbstractIntegrationTest;
import com.portfolio.insuhr.common.exception.BusinessException;
import com.portfolio.insuhr.domain.org.OrgType;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 조직 시점 조회 (설계서 7.2 {@code GET /orgs/tree?asOfDate=}, 6.6 v1.2).
 *
 * <p>이력 행이 전체 스냅샷을 담는다는 전제가 실제로 성립하는지 확인한다 — 이게 깨지면 시점 조회 전체가 무너진다.
 */
class OrgAsOfDateIntegrationTest extends AbstractIntegrationTest {

  private static final LocalDate D2020 = LocalDate.of(2020, 1, 1);
  private static final LocalDate D2023 = LocalDate.of(2023, 1, 1);
  private static final LocalDate D2026 = LocalDate.of(2026, 1, 1);

  @Autowired OrgService orgService;
  @Autowired JdbcClient jdbcClient;

  /** 테스트마다 조직코드를 겹치지 않게 한다 — 컨테이너를 클래스 간에 공유하므로 데이터가 누적된다. */
  private String uniq(String prefix) {
    return prefix + (System.nanoTime() % 100000);
  }

  @Test
  @DisplayName("조직 신설 시 이력과 Outbox 스텁이 같은 트랜잭션에 기록된다")
  void createRecordsHistoryAndOutboxStub() {
    String cd = uniq("H");
    orgService.create(cd, "본사 인사팀", OrgType.HQ_DEPT, null, 1, D2020);

    // 완료 기준(설계서 13.2): 조직 개편 시 이력 기록
    Integer histCount =
        jdbcClient
            .sql(
                """
                SELECT COUNT(*) FROM TB_ORG_HIST h
                  JOIN TB_ORG o ON o.ORG_ID = h.ORG_ID
                 WHERE o.ORG_CD = :cd AND h.CHANGE_TYPE_CD = 'CREATE'
                """)
            .param("cd", cd)
            .query(Integer.class)
            .single();
    assertThat(histCount).isEqualTo(1);

    // 이력의 AFTER_JSON은 diff가 아니라 전체 스냅샷이어야 한다 (설계서 6.6 v1.2).
    // 이 전제가 깨지면 아래 시점 조회 테스트들이 전부 성립하지 않는다.
    String afterJson =
        jdbcClient
            .sql(
                """
                SELECT h.AFTER_JSON FROM TB_ORG_HIST h
                  JOIN TB_ORG o ON o.ORG_ID = h.ORG_ID
                 WHERE o.ORG_CD = :cd
                """)
            .param("cd", cd)
            .query(String.class)
            .single();
    assertThat(afterJson)
        .contains("\"orgCd\":\"" + cd + "\"")
        .contains("\"orgNm\":\"본사 인사팀\"")
        .contains("\"orgTypeCd\":\"HQ_DEPT\"")
        .contains("\"orgLvl\":1")
        .contains("\"useYn\":\"Y\"");
  }

  @Test
  @DisplayName("기준일이 신설 이전이면 그 조직은 조직도에 없다")
  void orgIsAbsentBeforeItsCreation() {
    String cd = uniq("F");
    orgService.create(cd, "미래 지점", OrgType.BRANCH, null, 1, D2026);

    assertThat(orgCodesAsOf(D2023)).doesNotContain(cd);
    assertThat(orgCodesAsOf(D2026)).contains(cd);
  }

  @Test
  @DisplayName("명칭을 바꿔도 과거 기준일에는 옛 이름이 나온다")
  void asOfDateReturnsHistoricalName() {
    String cd = uniq("R");
    orgService.create(cd, "옛이름 지점", OrgType.BRANCH, null, 1, D2020);
    orgService.update(cd, "새이름 지점", null, D2023, "명칭 변경");

    // 시점 조회의 핵심 — 이력의 전체 스냅샷에서 그 시점 상태를 복원한다
    assertThat(orgNameAsOf(cd, LocalDate.of(2022, 12, 31))).isEqualTo("옛이름 지점");
    assertThat(orgNameAsOf(cd, D2023)).isEqualTo("새이름 지점");
    assertThat(orgNameAsOf(cd, D2026)).isEqualTo("새이름 지점");
  }

  @Test
  @DisplayName("폐지된 조직은 폐지일 이후 기준일에만 사라진다")
  void closedOrgDisappearsOnlyAfterCloseDate() {
    String cd = uniq("C");
    orgService.create(cd, "폐지될 영업소", OrgType.OFFICE, null, 1, D2020);
    orgService.close(cd, D2023, "통폐합");

    // 폐지 이력의 스냅샷이 useYn='N' 이라 별도 분기 없이 걸러진다
    assertThat(orgCodesAsOf(LocalDate.of(2022, 12, 31))).contains(cd);
    assertThat(orgCodesAsOf(D2026)).doesNotContain(cd);
  }

  @Test
  @DisplayName("하위 조직이 있으면 폐지할 수 없다 (409)")
  void cannotCloseOrgWithActiveChildren() {
    String parent = uniq("P");
    String child = uniq("K");
    orgService.create(parent, "지역단", OrgType.REGION, null, 1, D2020);
    orgService.create(child, "산하 지점", OrgType.BRANCH, parent, 1, D2020);

    assertThatThrownBy(() -> orgService.close(parent, D2026, "폐지 시도"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("하위 조직");
  }

  @Test
  @DisplayName("트리로 조립되고 계층 깊이가 상위에서 파생된다")
  void assemblesTreeWithDerivedLevels() {
    String region = uniq("RG");
    String branch = uniq("BR");
    String office = uniq("OF");
    orgService.create(region, "수도권지역단", OrgType.REGION, null, 1, D2020);
    orgService.create(branch, "강남지점", OrgType.BRANCH, region, 1, D2020);
    orgService.create(office, "역삼영업소", OrgType.OFFICE, branch, 1, D2020);

    OrgService.OrgTreeNode regionNode =
        orgService.tree(D2026, null).stream()
            .filter(n -> n.orgCd().equals(region))
            .findFirst()
            .orElseThrow();

    assertThat(regionNode.orgLvl()).isEqualTo(1);
    assertThat(regionNode.children()).hasSize(1);

    OrgService.OrgTreeNode branchNode = regionNode.children().get(0);
    assertThat(branchNode.orgCd()).isEqualTo(branch);
    assertThat(branchNode.orgLvl()).isEqualTo(2);
    assertThat(branchNode.children().get(0).orgCd()).isEqualTo(office);
    assertThat(branchNode.children().get(0).orgLvl()).isEqualTo(3);
  }

  @Test
  @DisplayName("자기 하위 조직으로는 이관할 수 없다 (순환 방지)")
  void rejectsMoveIntoOwnSubtree() {
    String parent = uniq("CP");
    String child = uniq("CC");
    orgService.create(parent, "상위", OrgType.REGION, null, 1, D2020);
    orgService.create(child, "하위", OrgType.BRANCH, parent, 1, D2020);

    // 순환이 생기면 CONNECT BY가 무한 루프에 빠진다
    assertThatThrownBy(() -> orgService.update(parent, null, child, D2026, "순환 시도"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("하위 조직으로는 이관");
  }

  private List<String> orgCodesAsOf(LocalDate asOf) {
    return flatten(orgService.tree(asOf, null));
  }

  private String orgNameAsOf(String orgCd, LocalDate asOf) {
    return flattenNodes(orgService.tree(asOf, null)).stream()
        .filter(n -> n.orgCd().equals(orgCd))
        .map(OrgService.OrgTreeNode::orgNm)
        .findFirst()
        .orElse(null);
  }

  private List<String> flatten(List<OrgService.OrgTreeNode> nodes) {
    return flattenNodes(nodes).stream().map(OrgService.OrgTreeNode::orgCd).toList();
  }

  private List<OrgService.OrgTreeNode> flattenNodes(List<OrgService.OrgTreeNode> nodes) {
    return nodes.stream()
        .flatMap(
            n ->
                java.util.stream.Stream.concat(
                    java.util.stream.Stream.of(n), flattenNodes(n.children()).stream()))
        .toList();
  }
}
