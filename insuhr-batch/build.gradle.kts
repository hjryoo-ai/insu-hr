plugins {
    alias(libs.plugins.spring.boot)
}

// 실행 모듈 2: 배치 서버 (설계서 8장) — Phase 7에서 잡 10종 구현
dependencies {
    implementation(project(":insuhr-domain"))
    implementation("org.springframework.boot:spring-boot-starter-batch")
}
