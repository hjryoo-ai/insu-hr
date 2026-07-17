package com.portfolio.insuhr.common.error;

/**
 * 에러 상세 1건 (설계서 7.1 / 7.3).
 *
 * <p>위촉 요건 미충족(422)처럼 "왜 안 되는지"를 여러 건 돌려줘야 하는 응답의 단위다. 모집자격 판정 실패는 예외 하나로 뭉개지 않고 이 목록으로 표현한다.
 *
 * @param field 사유가 걸린 항목. 예: {@code license}, {@code finGuarantee}
 * @param reason 기계가 분기할 수 있는 사유 코드. 예: {@code NO_VALID_LICENSE}
 * @param message 사람이 읽는 설명. 없으면 null
 */
public record ErrorDetail(String field, String reason, String message) {

  public static ErrorDetail of(String field, String reason) {
    return new ErrorDetail(field, reason, null);
  }

  public static ErrorDetail of(String field, String reason, String message) {
    return new ErrorDetail(field, reason, message);
  }
}
