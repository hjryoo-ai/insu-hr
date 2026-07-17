package com.portfolio.insuhr.domain.agent;

/**
 * 해촉사유 (설계서 부록 A: TERM_RSN, 5.3).
 *
 * <p>각 사유는 <b>재위촉을 영구히 막는지</b>를 함께 안다. 징계해촉(모집질서 문란 등)은 냉각기간이 지나도 재위촉이 거부된다(설계서 5.3 전이표 마지막 행). 이
 * 판정을 여기 두는 이유는 발령유형이 재직상태를 아는 것(§AppointType)과 같다 — 규칙을 데이터가 들고 있게 해서 "징계인데 재위촉 허용" 같은 조합이 애초에 안
 * 나오게 한다.
 *
 * <p>DB 시드({@code TB_CD.TERM_RSN.ATTR1='Y'})가 같은 사실을 담지만, 그건 보고·조회용 메타데이터이고 판정의 원천은 이 enum이다.
 */
public enum TermReason {
  /** 자진 해촉 */
  SELF(false),
  /** 회사 해촉 */
  COMPANY(false),
  /** 징계 해촉 — 재위촉 영구 금지 */
  DISCIPLINE(true),
  /** 타사 이동 */
  TRANSFER_OUT(false);

  private final boolean reappointBlocked;

  TermReason(boolean reappointBlocked) {
    this.reappointBlocked = reappointBlocked;
  }

  /** 이 사유로 해촉되면 재위촉이 냉각기간과 무관하게 영구 거부되는가. */
  public boolean isReappointBlocked() {
    return reappointBlocked;
  }
}
