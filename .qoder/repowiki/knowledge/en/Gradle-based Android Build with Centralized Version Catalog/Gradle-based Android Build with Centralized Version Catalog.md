---
kind: build_system
name: Gradle-based Android Build with Centralized Version Catalog
category: build_system
scope:
    - '**'
source_files:
    - settings.gradle.kts
    - build.gradle.kts
    - gradle/libs.versions.toml
    - app/build.gradle.kts
    - gradle.properties
---

## System / Approach

The project is an Android application built entirely with **Gradle** (Kotlin DSL). There are no Makefiles, Dockerfiles, shell build scripts, or CI pipelines in the repository. The build is a single-module Gradle project (`:app`) configured via `settings.gradle.kts`, `build.gradle.kts`, and `gradle.properties`, with all third-party versions centralized in the Gradle version catalog at `gradle/libs.versions.toml`.

## Key Files

- `settings.gradle.kts` — root settings; declares plugin management repositories (Google, Maven Central, Gradle Plugin Portal), enables `org.gradle.toolchains.foojay-resolver-convention` for JVM toolchain resolution, centralizes dependency repositories, sets `repositoriesMode = FAIL_ON_PROJECT_REPOS`, and includes only the `:app` module.
- `build.gradle.kts` (root) — applies the Android application and library plugins via `alias(libs.plugins.android.application)` and `alias(libs.plugins.android.library)`, both with `apply false` so they are not applied to the root itself.
- `gradle/libs.versions.toml` — the single source of truth for every version and library coordinate used by the app (AGP 9.3.3, Kotlin 2.3.20, Koin 4.0.0, Retrofit 2.11.0, OkHttp 4.12.0, Coroutines 1.9.0, Lifecycle 2.8.7, Navigation 2.8.5, Lottie 6.6.7, Fresco 3.6.0, ImageCropper 4.7.0, SDP/SSP 1.1.1, DataStore 1.1.2).
- `app/build.gradle.kts` — the only module build script; configures namespace, SDK levels, ViewBinding + BuildConfig, release minification/proguard, Java 17 compatibility, and pulls every dependency through the catalog.
- `gradle.properties` — sets `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`, enables `org.gradle.configuration-cache=true`, and pins `kotlin.code.style=official`.
- `gradle/gradle-daemon-jvm.properties` — present alongside the wrapper; used by the foojay toolchain resolver.

## Architecture and Conventions

- **Single-module layout**: despite the presence of the `android-library` plugin alias, there is only one module (`:app`). No feature modules are declared in `settings.gradle.kts`.
- **Centralized versioning**: every external dependency version lives in `[versions]` of `libs.versions.toml`; libraries reference them via `version.ref = ...`. This is the only place where AGP, Kotlin, and third-party versions are specified.
- **Plugin aliases**: plugins are referenced through `alias(libs.plugins.android.application)` rather than direct `id(...)` strings, keeping plugin IDs and versions in the catalog.
- **Repository policy**: `dependencyResolutionManagement.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` enforces that modules cannot declare their own repositories — all repos must be declared centrally in `settings.gradle.kts`.
- **SDK & language targets**: compile/target SDK 36, minSdk 24, Java 17 source/target compatibility, Kotlin 2.3.20.
- **Build features**: ViewBinding and BuildConfig are enabled; `BASE_URL` is injected as a `String` BuildConfig field pointing at `https://flirttimeapi.walit.in/`.
- **Release build type**: defined but `isMinifyEnabled = false`; ProGuard rules are referenced from `proguard-rules.pro` (not shown in tree) plus the default optimize file.
- **Test wiring**: `testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"`; unit tests use JUnit 4, instrumented tests use `androidx.junit` + Espresso.
- **JVM toolchain**: resolved automatically via the `foojay-resolver-convention` Gradle plugin (v1.0.0) rather than a pinned JDK path.

## Conventions and Constraints

- All dependency versions are centralized in `gradle/libs.versions.toml` and consumed via `libs.<name>` references in `app/build.gradle.kts` — no inline version strings appear in the module build script.
- Modules are forbidden from declaring their own dependency repositories; attempting to do so will fail because `FAIL_ON_PROJECT_REPOS` is set in `settings.gradle.kts`.
- The project uses the Gradle configuration cache (`org.gradle.configuration-cache=true`), so any non-deterministic configuration code would break builds.
- The Android Gradle Plugin and Kotlin compiler are pinned to specific versions (AGP 9.3.3, Kotlin 2.3.20) in the version catalog.
- Only one Gradle build variant is defined: `release` (with minification disabled). No `debug` customization exists beyond the defaults.
- There is no CI pipeline, Dockerfile, signing configuration, or automated artifact publishing visible in the repository.