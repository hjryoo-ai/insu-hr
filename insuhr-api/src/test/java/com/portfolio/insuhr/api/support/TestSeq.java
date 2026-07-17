package com.portfolio.insuhr.api.support;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * JVM 전역 유니크 시퀀스 (테스트용).
 *
 * <p>통합 테스트는 Oracle 컨테이너를 <b>클래스 간에 공유</b>하므로(설계서 12장, 싱글턴 컨테이너 패턴) 데이터가 누적된다. 각 테스트 클래스가 로컬 카운터로
 * {@code 900101-0000001} 같은 주민번호나 {@code O1} 같은 조직코드를 만들면 클래스 사이에서 값이 겹쳐 유니크 제약을 깬다.
 *
 * <p>이 클래스의 단일 정적 카운터로 만들면 JVM 안의 모든 테스트가 서로 다른 값을 받는다. 주민번호 생년월일 앞자리는 {@code 850101}로 둬서 자체 카운터를
 * 쓰는 {@code PersonIntegrationTest}({@code 900101})와도 겹치지 않는다.
 */
public final class TestSeq {

  private static final AtomicInteger COUNTER = new AtomicInteger(1);

  private TestSeq() {}

  public static int next() {
    return COUNTER.getAndIncrement();
  }

  /** 전역 유니크 주민번호. */
  public static String rrn() {
    return "850101-" + String.format("%07d", next());
  }

  /** 전역 유니크 조직코드 (10자 이내). */
  public static String orgCd() {
    return "T" + next();
  }
}
