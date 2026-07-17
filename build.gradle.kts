plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spotless) apply false
}

allprojects {
    group = "com.portfolio.insuhr"
    version = "0.0.1-SNAPSHOT"
}

// `libs` 타입세이프 접근자는 루트 스크립트 스코프에서만 해석된다.
// subprojects {} 안에서는 서브프로젝트를 대상으로 조회하다 실패하므로 값을 먼저 캡처한다.
val springBootVersion = libs.versions.springBoot.get()
val googleJavaFormatVersion = libs.versions.googleJavaFormat.get()

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<JavaPluginExtension> {
        toolchain { languageVersion.set(JavaLanguageVersion.of(21)) }
    }

    dependencies {
        // Boot BOM을 전 모듈에 깔아 라이브러리 버전을 한 곳에서 관리한다.
        // 실행 모듈만 boot 플러그인을 추가로 적용한다(아래 각 모듈 빌드 파일).
        // 애노테이션 프로세서 경로는 implementation의 BOM을 물려받지 않는다.
        // Lombok 버전이 해석되도록 프로세서 설정에도 각각 깔아준다.
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
        "annotationProcessor"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
        "testAnnotationProcessor"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))

        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")

        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        // boot 플러그인이 붙지 않는 라이브러리 모듈(common/domain)은 런처가 딸려오지 않는다.
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.compilerArgs.add("-parameters")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("passed", "skipped", "failed")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            googleJavaFormat(googleJavaFormatVersion)
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
        }
    }
}
