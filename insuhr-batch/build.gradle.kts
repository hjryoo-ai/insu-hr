plugins {
    alias(libs.plugins.spring.boot)
}

// 실행 모듈 2: 배치 서버 (설계서 8장) — Phase 7 잡 구현
dependencies {
    implementation(project(":insuhr-domain"))
    implementation("org.springframework.boot:spring-boot-starter-batch")

    // 통합 테스트: 실제 Oracle + Flyway migrate + Spring Batch Test(JobOperatorTestUtils 등).
    // Flyway 자동설정/드라이버는 domain에서 런타임 경로로 딸려온다.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-oracle-free")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.springframework.batch:spring-batch-test")
}
