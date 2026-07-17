plugins {
    alias(libs.plugins.spring.boot)
}

// 실행 모듈 1: REST API 서버 (설계서 4.2)
dependencies {
    implementation(project(":insuhr-domain"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // 운영 코드는 Flyway API를 직접 쓰지 않으므로 domain에서 runtimeOnly로 둔다.
    // 마이그레이션 적용 여부를 단언하는 테스트에서만 컴파일 의존이 필요하다.
    testImplementation("org.flywaydb:flyway-core")

    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    // TC 2.x에서 아티팩트명 앞에 testcontainers- 접두어가 붙었다.
    // (1.x의 oracle-free / junit-jupiter 는 2.x에 존재하지 않는다 — 설계서 3장의 표기는 1.x 기준)
    // 버전은 Boot BOM이 import하는 testcontainers-bom 2.0.5가 관리한다.
    testImplementation("org.testcontainers:testcontainers-oracle-free")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
}
