// 도메인 계층: JPA 엔티티 + 도메인 서비스 + 리포지토리 + Flyway 마이그레이션(스키마 소유)
// 모든 비즈니스 규칙이 사는 곳 (설계서 4.3)
dependencies {
    api(project(":insuhr-common"))
    api("org.springframework.boot:spring-boot-starter-data-jpa")

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
