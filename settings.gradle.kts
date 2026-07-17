rootProject.name = "insuhr"

// 설계서 4.2 — 의존 방향: api / batch / relay → domain → common (역방향 금지)
include(
    "insuhr-common",
    "insuhr-domain",
    "insuhr-api",
    "insuhr-batch",
    "insuhr-relay",
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}
