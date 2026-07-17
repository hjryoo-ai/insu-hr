package com.portfolio.insuhr.api.person;

import com.portfolio.insuhr.api.security.AuthenticatedUser;
import com.portfolio.insuhr.api.support.TraceIdProvider;
import com.portfolio.insuhr.common.response.ApiResponse;
import com.portfolio.insuhr.domain.person.Gender;
import com.portfolio.insuhr.domain.person.NewPerson;
import com.portfolio.insuhr.domain.person.Person;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 인물 API (설계서 7.2 PER). */
@RestController
@RequestMapping("/api/v1/persons")
public class PersonController {

  private final PersonService personService;

  public PersonController(PersonService personService) {
    this.personService = personService;
  }

  /**
   * 주민번호 해시 기반 기존 인물 검사.
   *
   * <p>이 API는 <b>UX용</b>이다 — 화면이 "이미 등록된 분입니다"를 미리 보여주기 위한 것이며, 중복의 실제 방어선은 등록 시의 유니크 제약이다(설계서 5.2
   * v1.2). 이 응답을 믿고 곧바로 등록하면 동시 요청에서 중복이 생긴다.
   */
  @PostMapping("/check-duplicate")
  @PreAuthorize("hasAuthority('person.read')")
  public ApiResponse<DuplicateCheckResponse> checkDuplicate(
      @Valid @RequestBody DuplicateCheckRequest request) {
    return ApiResponse.ok(
        new DuplicateCheckResponse(personService.exists(request.rrn())), TraceIdProvider.current());
  }

  /** 인물 등록. 이미 있으면 기존 인물을 돌려준다 (역할만 추가하는 흐름). */
  @PostMapping
  @PreAuthorize("hasAuthority('person.write')")
  public ApiResponse<RegisterResponse> register(@Valid @RequestBody RegisterPersonRequest request) {
    PersonService.Registration result =
        personService.register(
            new NewPerson(
                request.personNm(),
                request.rrn(),
                request.birthDt(),
                request.gender(),
                request.mobile(),
                request.email(),
                request.nationalityCd()));
    return ApiResponse.ok(
        new RegisterResponse(result.personId(), result.created()), TraceIdProvider.current());
  }

  /** 인물 상세. 개인식별정보는 마스킹 값으로 나간다 (설계서 7.1). */
  @GetMapping("/{personId}")
  @PreAuthorize("hasAuthority('person.read')")
  public ApiResponse<PersonResponse> get(@PathVariable Long personId) {
    return ApiResponse.ok(
        PersonResponse.from(personService.get(personId)), TraceIdProvider.current());
  }

  /**
   * 주민번호 복호화 조회 (설계서 7.2).
   *
   * <p>{@code person.rrn.decrypt} 권한 + 사유 입력 필수. 접근로그가 남는다(10.2).
   *
   * <p><b>POST인 이유</b>(설계서 7.1 v1.4): 조회 의미상 GET이 자연스럽지만, GET이면 열람 사유가 쿼리스트링에 실려 URL로 샌다 —
   * 액세스로그·프록시·브라우저 히스토리에 남고 그중 어느 것도 {@code TB_PRIVACY_ACCESS_LOG} 통제를 받지 않는다. 사유를 본문에 담아 이 경로를
   * 닫는다. 민감정보 복호화 계열 전체의 일반 규칙이다.
   */
  @PostMapping("/{personId}/rrn")
  @PreAuthorize("hasAuthority('person.rrn.decrypt')")
  public ApiResponse<RrnResponse> decryptRrn(
      @PathVariable Long personId,
      @Valid @RequestBody DecryptRequest request,
      @AuthenticationPrincipal AuthenticatedUser actor,
      HttpServletRequest httpRequest) {

    String rrn =
        personService.decryptRrn(
            personId,
            actor.userId(),
            request.purpose(),
            "POST /api/v1/persons/{personId}/rrn",
            clientIpOf(httpRequest));

    return ApiResponse.ok(new RrnResponse(personId, rrn), TraceIdProvider.current());
  }

  /** 프록시 뒤에서도 원 클라이언트를 남기려 X-Forwarded-For를 먼저 본다. */
  private String clientIpOf(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    if (forwarded != null && !forwarded.isBlank()) {
      return forwarded.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  public record DuplicateCheckRequest(
      @NotBlank @Pattern(regexp = "\\d{6}-?\\d{7}", message = "주민등록번호 형식이 올바르지 않습니다.")
          String rrn) {}

  public record DuplicateCheckResponse(boolean exists) {}

  public record RegisterPersonRequest(
      @NotBlank @Size(max = 100) String personNm,
      @NotBlank @Pattern(regexp = "\\d{6}-?\\d{7}", message = "주민등록번호 형식이 올바르지 않습니다.") String rrn,
      @NotNull LocalDate birthDt,
      @NotNull Gender gender,
      String mobile,
      @Size(max = 100) String email,
      @Size(max = 10) String nationalityCd) {}

  public record RegisterResponse(Long personId, boolean created) {}

  public record DecryptRequest(
      @NotBlank(message = "개인정보 열람 사유를 입력해야 합니다.") @Size(max = 400) String purpose) {}

  public record RrnResponse(Long personId, String rrn) {}

  /** 상세 응답. 주민번호는 아예 담지 않고, 휴대폰은 저장된 마스킹 값을 그대로 쓴다 (설계서 10.2 v1.2). */
  public record PersonResponse(
      Long personId,
      String personNmMasked,
      LocalDate birthDt,
      String genderCd,
      String mobileMasked,
      String email,
      String nationalityCd) {

    static PersonResponse from(Person person) {
      return new PersonResponse(
          person.getId(),
          person.maskedName(),
          person.getBirthDt(),
          person.getGenderCd(),
          person.getMobileMasked(),
          person.getEmail(),
          person.getNationalityCd());
    }
  }
}
