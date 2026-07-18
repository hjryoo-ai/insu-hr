plugins {
    alias(libs.plugins.spring.boot)
}

// 실행 모듈 3: Outbox 릴레이 (설계서 9.2) — 웹훅/서명/재시도/순서게이트 (Phase 6)
dependencies {
    implementation(project(":insuhr-domain"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // 통합 테스트: 실제 Oracle + Flyway migrate(테스트는 validate 플래그를 안 켜 migrate로 스키마 구성)
    // + WireMock(웹훅 수신 목). Flyway 자동설정/드라이버는 domain에서 런타임 경로로 딸려온다.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-oracle-free")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    // 셰이드된 standalone — Jetty/Jackson2를 relocate해 Boot 4 기본(Jackson 3)과 충돌하지 않는다.
    testImplementation("org.wiremock:wiremock-standalone:3.13.1")
}
