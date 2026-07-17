package com.portfolio.insuhr.domain.person;

import java.time.LocalDate;

/**
 * 인물 등록 입력 (평문).
 *
 * <p>암호화 전 원문을 담으므로 <b>로그에 찍지 말 것</b>. record의 기본 toString()이 전 필드를 노출하므로 재정의한다.
 *
 * @param rrn 주민등록번호 원문. TB_PERSON에서 암호문+해시로 나뉘어 저장된다
 */
public record NewPerson(
    String personNm,
    String rrn,
    LocalDate birthDt,
    Gender gender,
    String mobile,
    String email,
    String nationalityCd) {

  @Override
  public String toString() {
    // 주민번호·휴대폰이 로그로 새는 것을 막는다 (설계서 10.2).
    return "NewPerson[personNm=" + personNm + ", rrn=***, mobile=***]";
  }
}
