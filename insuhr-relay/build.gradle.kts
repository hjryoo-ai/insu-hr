plugins {
    alias(libs.plugins.spring.boot)
}

// 실행 모듈 3: Outbox 릴레이 (설계서 9.2) — 웹훅/서명/재시도/순서게이트 (Phase 6)
dependencies {
    implementation(project(":insuhr-domain"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Kafka 퍼블리셔는 kafka 프로파일에서만 활성(@Profile). 버전은 Boot 4.1 BOM 관리.
    implementation("org.springframework.kafka:spring-kafka")

    // 통합 테스트: 실제 Oracle + Flyway migrate(테스트는 validate 플래그를 안 켜 migrate로 스키마 구성)
    // + WireMock(웹훅 수신 목) + Testcontainers Kafka(kafka 프로파일 테스트).
    // Flyway 자동설정/드라이버는 domain에서 런타임 경로로 딸려온다.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-oracle-free")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-kafka")
    // 셰이드된 standalone — Jetty/Jackson2를 relocate해 Boot 4 기본(Jackson 3)과 충돌하지 않는다.
    testImplementation("org.wiremock:wiremock-standalone:3.13.1")
}
