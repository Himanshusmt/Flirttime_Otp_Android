---
kind: configuration_system
name: Gradle Version Catalog + Android Build Config as Configuration System
category: configuration_system
scope:
    - '**'
source_files:
    - gradle/libs.versions.toml
    - settings.gradle.kts
    - build.gradle.kts
    - app/build.gradle.kts
    - gradle.properties
    - local.properties
---

## Overview

This Android Gradle project has no runtime configuration framework (no `.env`, no YAML/JSON config files, no feature-flag library). Runtime configuration is provided through two mechanisms:

1. **Build-time configuration** via Gradle (version catalog, build scripts, properties).
2. **App-level constants** injected at build time into `BuildConfig` and exposed at runtime.

## Key Files and Layers

### Build-time configuration

- `gradle/libs.versions.toml` — Centralized [Version Catalog](https://docs.gradle.org/current/userguide/platform.html) declaring `[versions]` (AGP 9.3.3, Kotlin 2.3.20, Koin 4.0.0, Retrofit 2.11.0, OkHttp 4.12.0, Coroutines 1.9.0, Lifecycle 2.8.7, Navigation 2.8.5, Lottie 6.6.7, Fresco 3.6.0, ImageCropper 4.7.0, DataStore 1.1.2, SDP/SSP 1.1.1) and mapping them to `[libraries]` entries referenced via `libs.<name>`.
- `settings.gradle.kts` — Declares plugin management repositories (Google, Maven Central, Gradle Plugin Portal), applies the `org.gradle.toolchains.foojay-resolver-convention` plugin for JDK resolution, and sets `dependencyResolutionManagement.repositoriesMode = FAIL_ON_PROJECT_REPOS`, which enforces that subprojects cannot declare their own repositories.
- `build.gradle.kts` (root) — Applies the Android application/library plugins via `alias(libs.plugins.android.application)` / `alias(libs.plugins.android.library)` with `apply false`; no shared `subprojects {}` block exists.
- `app/build.gradle.kts` — Module-level build script: declares `namespace = "com.flirttime"`, `compileSdk = 36`, `minSdk = 24`, `targetSdk = 36`, `applicationId = "com.flirttime.test"`, `versionCode = 1`, `versionName = "1.0"`. Defines `buildTypes.release { isMinifyEnabled = false }` with ProGuard rules from `proguard-rules.pro`. Enables `viewBinding` and `buildConfig` features. Java/Kotlin source/target compatibility set to `JavaVersion.VERSION_17`.
- `gradle.properties` — Sets `org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8`, enables `org.gradle.configuration-cache=true`, and sets `kotlin.code.style=official`.
- `local.properties` — Local-only SDK path (`sdk.dir=/Users/.../Android/sdk`); header explicitly states it must not be checked into VCS.

### Runtime configuration

- `app/build.gradle.kts` line 20 injects a single runtime constant via `buildConfigField("String", "BASE_URL", "\"https://flirttimeapi.walit.in/\"")`. This is consumed at runtime by the networking layer (e.g., `ApiService.kt`).
- Android resource files under `app/src/main/res/values/` (`strings.xml`, `colors.xml`, `themes.xml`, `dimens.xml`) serve as the app's localized/static configuration surface.
- No `.env` files, no environment-variable loading, no secrets manager integration, and no feature-flag toggling mechanism were found.

## Architecture and Conventions

- **Centralized dependency versions**: All third-party versions live in `gradle/libs.versions.toml`; modules reference them via `libs.<library-name>` rather than inline version strings. This is enforced by the convention of using `version.ref = <key>` in the catalog.
- **Plugin aliasing**: Plugins are applied exclusively through `alias(libs.plugins.*)` defined in the catalog; no hard-coded plugin coordinates appear in `app/build.gradle.kts`.
- **Single-module layout**: Despite being a Gradle root, only `:app` is included (`include(":app")` in `settings.gradle.kts`); there are no feature modules yet.
- **Repository policy**: `repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` in `settings.gradle.kts` prevents any subproject from declaring additional repositories, centralizing all artifact sources at the root.
- **BuildConfig over env vars**: Application endpoints and other deploy-time values are baked into `BuildConfig` via `buildConfigField` rather than read from environment variables at runtime.
- **Local-only secrets**: `local.properties` is the only place for machine-specific configuration (SDK path), and its header documents the VCS exclusion rule.

## Constraints Enforced by the Build

- Subprojects cannot add new Maven/Google repositories (`FAIL_ON_PROJECT_REPOS` in `settings.gradle.kts`).
- Dependency versions must go through the version catalog (observed pattern; not lint-enforced but consistent across all `implementation(...)` lines in `app/build.gradle.kts`).
- The app compiles against Java 17 source/target compatibility (`compileOptions` in `app/build.gradle.kts`).
- `BASE_URL` is the only configurable runtime string exposed via `BuildConfig`; adding new runtime constants requires editing `app/build.gradle.kts`'s `defaultConfig` block.