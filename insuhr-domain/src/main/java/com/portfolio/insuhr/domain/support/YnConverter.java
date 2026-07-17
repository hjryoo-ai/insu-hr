package com.portfolio.insuhr.domain.support;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * {@code _YN} 컬럼(CHAR(1) 'Y'/'N') ↔ boolean 변환 (설계서 6.1).
 *
 * <p>도메인 코드에 {@code "Y".equals(useYn)} 같은 문자열 비교가 퍼지는 것을 막는다. 오타(`"y"`, `"YES"`)를 컴파일러가 잡아주지 못하는
 * 자리를 boolean으로 바꾸는 것이 목적이다.
 *
 * <p><b>CHAR vs VARCHAR2 주의</b>: 설계서 6.1의 `_YN`은 CHAR(1)인데, Hibernate는 String 속성을 VARCHAR2로 기대해
 * {@code ddl-auto=validate}가 "wrong column type" 으로 기동을 막는다. 그래서 이 컨버터를 쓰는 필드에는
 * {@code @JdbcTypeCode(SqlTypes.CHAR)}를 함께 붙여야 한다 — {@link com.portfolio.insuhr.domain.org.Org} 참조.
 */
@Converter
public class YnConverter implements AttributeConverter<Boolean, String> {

  private static final String YES = "Y";
  private static final String NO = "N";

  @Override
  public String convertToDatabaseColumn(Boolean attribute) {
    return Boolean.TRUE.equals(attribute) ? YES : NO;
  }

  @Override
  public Boolean convertToEntityAttribute(String dbData) {
    // CHAR(1) 이라 공백 패딩이 붙어 올 수 있다.
    return dbData != null && YES.equalsIgnoreCase(dbData.trim());
  }
}
