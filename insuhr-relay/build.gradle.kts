plugins {
    alias(libs.plugins.spring.boot)
}

// 실행 모듈 3: Outbox 릴레이 (설계서 9.2) — Phase 6에서 웹훅/서명/재시도 구현
dependencies {
    implementation(project(":insuhr-domain"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
}
