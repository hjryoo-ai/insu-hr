// 도메인 계층: JPA 엔티티 + 도메인 서비스 + 리포지토리 + Flyway 마이그레이션(스키마 소유)
// 모든 비즈니스 규칙이 사는 곳 (설계서 4.3)
dependencies {
    api(project(":insuhr-common"))
    api("org.springframework.boot:spring-boot-starter-data-jpa")

    // OutboxIntegrationRecorder(domain)가 Jackson 3 ObjectMapper 빈을 요구한다. Boot 4는 자동설정을
    // 기술별 모듈로 쪼갰으므로(설계서 3.0) ObjectMapper 빈은 spring-boot-jackson이 만든다 — api/relay는
    // web 스타터로 이걸 물지만 batch는 web이 없어 빈이 없었다. 필요가 시작되는 domain이 api로 물어
    // 모든 실행 모듈(4번째가 생겨도)이 자동으로 빈을 갖게 한다(jpa-common.yml과 같은 "구조로 강제").
    api("org.springframework.boot:spring-boot-jackson")

    // BCrypt(PasswordEncoder)는 spring-security-crypto를 요구하므로 insuhr-common(의존성 0)에
    // 둘 수 없다. 설계서 13.2 Phase 1의 배치 기준 — JDK 내장 JCA로 되는 것만 common에.
    //
    // JWT(jjwt)는 여기 두지 않는다. 토큰 발급은 HTTP 인증 관심사라 api 모듈에 있고,
    // domain에 두면 배치/릴레이까지 jjwt를 끌고 가게 된다.
    api("org.springframework.security:spring-security-crypto")

    // 스키마는 domain이 소유한다. api/batch/relay 어느 실행 모듈에서든 동일 마이그레이션을 본다.
    //
    // Boot 4는 자동설정을 기술별 모듈로 쪼갰다. flyway-core만 넣으면 FlywayAutoConfiguration이
    // 딸려오지 않아 마이그레이션이 조용히 스킵된다(예외도 안 난다) — spring-boot-flyway가 필수.
    // runtimeOnly가 아닌 implementation인 이유: FlywayValidateOnlyConfig가 FlywayMigrationStrategy를
    // 컴파일 시점에 참조한다. api 옵션이 아니므로 상위 모듈 컴파일 경로로는 새지 않는다.
    implementation("org.springframework.boot:spring-boot-flyway")
    runtimeOnly("org.flywaydb:flyway-database-oracle")
    runtimeOnly("com.oracle.database.jdbc:ojdbc17")
}
