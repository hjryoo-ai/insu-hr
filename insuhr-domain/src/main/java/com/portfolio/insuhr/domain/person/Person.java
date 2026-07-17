package com.portfolio.insuhr.domain.person;

import com.portfolio.insuhr.common.crypto.AesGcmCipher;
import com.portfolio.insuhr.common.crypto.PepperedHasher;
import com.portfolio.insuhr.common.privacy.Masker;
import com.portfolio.insuhr.domain.support.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

/**
 * 인물 마스터 (설계서 5.2).
 *
 * <p>한 사람은 한 행이다. 직원이었다가 설계사로 위촉돼도 인물 레코드는 하나이며, 역할({@code TB_EMP}/{@code TB_AGENT})만 추가된다.
 *
 * <p><b>암호화 유틸을 메서드 인자로 받는 이유</b>: 원문과 마스킹 표시값이 어긋나지 않게 하려면 두 값이 <b>같은 입력에서 함께</b> 파생돼야 한다(설계서 10.2
 * v1.2). 호출부가 {@code setMobileEnc()}/{@code setMobileMasked()}를 따로 부를 수 있게 두면 언젠가 어긋난다. 그래서 평문과
 * 암호화기를 함께 받아 엔티티 안에서 둘을 한 번에 계산한다.
 */
@Entity
@Table(name = "TB_PERSON")
public class Person extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "PERSON_ID")
  private Long id;

  @Column(name = "PERSON_NM", nullable = false, length = 100)
  private String personNm;

  @Column(name = "RRN_ENC", nullable = false, length = 512)
  private String rrnEnc;

  @Column(name = "RRN_HASH", nullable = false, length = 64)
  private String rrnHash;

  @Column(name = "BIRTH_DT", nullable = false)
  private LocalDate birthDt;

  @Column(name = "GENDER_CD", nullable = false, length = 10)
  private String genderCd;

  @Column(name = "MOBILE_ENC", length = 256)
  private String mobileEnc;

  @Column(name = "MOBILE_MASKED", length = 20)
  private String mobileMasked;

  @Column(name = "EMAIL", length = 100)
  private String email;

  @Column(name = "NATIONALITY_CD", nullable = false, length = 10)
  private String nationalityCd;

  protected Person() {}

  /**
   * 인물 등록.
   *
   * <p>주민번호는 암호문과 해시 두 벌로 저장한다 — 암호문은 IV 때문에 매번 달라 등치 검색이 불가하므로, 동일인 검사는 결정적 해시가 맡는다(설계서 6.8).
   */
  public static Person register(NewPerson command, AesGcmCipher cipher, PepperedHasher rrnHasher) {
    Person person = new Person();
    person.personNm = command.personNm();
    person.rrnEnc = cipher.encrypt(command.rrn());
    person.rrnHash = rrnHasher.hash(command.rrn());
    person.birthDt = command.birthDt();
    person.genderCd = command.gender().name();
    person.email = command.email();
    person.nationalityCd = command.nationalityCd() == null ? "KR" : command.nationalityCd();
    person.changeMobile(command.mobile(), cipher);
    return person;
  }

  /**
   * 휴대폰 변경. 암호문과 마스킹 표시값을 함께 갱신한다.
   *
   * <p>둘을 따로 세팅할 수 있는 통로를 두지 않는 것이 요점이다 — 어긋나면 목록에 남의 번호가 보인다.
   */
  public void changeMobile(String rawMobile, AesGcmCipher cipher) {
    this.mobileEnc = cipher.encrypt(rawMobile);
    this.mobileMasked = Masker.phone(rawMobile);
  }

  public void changeEmail(String email) {
    this.email = email;
  }

  public void changeName(String personNm) {
    this.personNm = personNm;
  }

  /**
   * 주민번호 복호화 (설계서 10.2).
   *
   * <p><b>이 메서드를 직접 부르지 말 것.</b> 복호화는 {@code person.rrn.decrypt} 권한 검사와 접근로그 기록을 동반해야 하며, 그 책임은
   * 애플리케이션 서비스에 있다. 여기서 권한을 검사하지 않는 이유는 도메인이 보안 컨텍스트를 알면 안 되기 때문이다.
   */
  public String decryptRrn(AesGcmCipher cipher) {
    return cipher.decrypt(rrnEnc);
  }

  /** 휴대폰 원문 복호화. 복호화 규칙은 {@link #decryptRrn} 과 같다. */
  public String decryptMobile(AesGcmCipher cipher) {
    return cipher.decrypt(mobileEnc);
  }

  /** 응답에 나가는 마스킹 이름 (설계서 10.2). 이름은 평문 컬럼이라 복호화가 필요 없다. */
  public String maskedName() {
    return Masker.name(personNm);
  }

  public Long getId() {
    return id;
  }

  public String getPersonNm() {
    return personNm;
  }

  public String getRrnHash() {
    return rrnHash;
  }

  public LocalDate getBirthDt() {
    return birthDt;
  }

  public String getGenderCd() {
    return genderCd;
  }

  /** 목록 표시용 마스킹 값. 복호화 없이 그대로 쓴다 (설계서 10.2 v1.2). */
  public String getMobileMasked() {
    return mobileMasked;
  }

  public String getEmail() {
    return email;
  }

  public String getNationalityCd() {
    return nationalityCd;
  }
}
