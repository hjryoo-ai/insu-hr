package com.portfolio.insuhr.common.crypto;

/**
 * 암·복호화 실패.
 *
 * <p>메시지에 평문이나 키를 절대 담지 않는다 — 예외는 로그로 흘러가고 로그는 개인정보 통제 밖에 있다.
 */
public class CryptoException extends RuntimeException {

  public CryptoException(String message) {
    super(message);
  }

  public CryptoException(String message, Throwable cause) {
    super(message, cause);
  }
}
