---
kind: dependency_management
name: Gradle Version Catalog with Centralized Repository Policy
category: dependency_management
scope:
    - '**'
source_files:
    - gradle/libs.versions.toml
    - settings.gradle.kts
    - build.gradle.kts
    - app/build.gradle.kts
---

## Approach

The FlirtTime Android project uses **Gradle's version catalog** (libs.versions.toml) as the single source of truth for all third-party dependency versions and plugin versions. The root `settings.gradle.kts` enforces a centralized repository policy so that no module can declare its own repositories.

## Key Files

- `gradle/libs.versions.toml` — declares `[versions]`, `[libraries]`, and `[plugins]` sections; every dependency is referenced by `version.ref = "..."` from this file.
- `settings.gradle.kts` — centralizes `pluginManagement.repositories` and `dependencyResolutionManagement.repositories`; sets `RepositoriesMode.FAIL_ON_PROJECT_REPOS` to forbid per-module repo declarations.
- `build.gradle.kts` (root) — applies only the Android application/library plugins via `alias(libs.plugins.android.application)` / `alias(libs.plugins.android.library)` with `apply false`.
- `app/build.gradle.kts` — consumes dependencies exclusively through `implementation(libs.<catalog-key>)` references; no inline version strings except for one outlier (`shimmer`).

## Architecture and Conventions

1. **Centralized version catalog** (`gradle/libs.versions.toml`):
   - All versions live under `[versions]` (e.g. `agp = "9.3.3"`, `kotlin = "2.3.20"`, `retrofit = "2.11.0"`, `okhttp = "4.12.0"`, `koin = "4.0.0"`, `lifecycle = "2.8.7"`, `navigation = "2.8.5"`, `fresco = "3.6.0"`, `imageCropper = "4.7.0"`).
   - Libraries are grouped by concern in comments: Navigation Component, Kotlin Coroutines, Lifecycle & ViewModel, Koin Dependency Injection, Retrofit & OkHttp, Scalable dp/sp, DataStore Preferences.
   - Plugins (`android-application`, `android-library`) reuse the `agp` version via `version.ref = "agp"`.

2. **Repository policy enforced at the root**:
   - `pluginManagement.repositories` declares Google, Maven Central, and Gradle Plugin Portal with `includeGroupByRegex` filters for `com\.android.*`, `com\.google.*`, `androidx.*` on Google.
   - `dependencyResolutionManagement.repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)` makes any module-level `repositories { ... }` block fail the build.
   - Only two artifact sources are allowed: `google()` and `mavenCentral()`.

3. **Dependency declaration style**:
   - Every `implementation(...)` in `app/build.gradle.kts` goes through `libs.<key>` (e.g. `libs.retrofit`, `libs.okhttp.logging`, `libs.kotlinx.coroutines.core`, `libs.lottie`, `libs.fresco`).
   - No module defines its own dependency coordinates or versions inline.

4. **Toolchain resolution**:
   - `org.gradle.toolchains.foojay-resolver-convention` version `1.0.0` is applied in `settings.gradle.kts` to auto-resolve JDK toolchains.

## Observed Constraints and Rules

- **Rule**: Modules cannot declare their own `repositories {}` blocks — `RepositoriesMode.FAIL_ON_PROJECT_REPOS` in `settings.gradle.kts` causes the build to fail if they do. This centralizes artifact sourcing to `google()` and `mavenCentral()` only.
- **Rule**: Plugin versions must be declared in the catalog's `[plugins]` section and consumed via `alias(libs.plugins.<id>)`; the root `build.gradle.kts` demonstrates this pattern with `apply false` for shared plugins.
- **Convention**: Library versions are pinned in `[versions]` and referenced via `version.ref = "..."` in `[libraries]`; modules never hardcode version strings when consuming them.
- **Exception observed**: The `shimmer` library (`com.facebook.shimmer:shimmer:0.5.0`) is declared directly with an inline `version = "0.5.0"` rather than going through the `[versions]` table, breaking the otherwise consistent convention.
- **No private registry or vendoring**: There is no `.m2/`, `vendor/`, `ivy/`, or local Maven cache configuration beyond standard Gradle behavior; no `local.properties` contains custom repository credentials, and no `gradle.properties` overrides repositories. Private/internal artifacts would need to be added to the root `repositories` block in `settings.gradle.kts`.
- **Lockfile**: No `gradle.lockfile` is committed; dependency resolution is not locked to exact transitive versions.