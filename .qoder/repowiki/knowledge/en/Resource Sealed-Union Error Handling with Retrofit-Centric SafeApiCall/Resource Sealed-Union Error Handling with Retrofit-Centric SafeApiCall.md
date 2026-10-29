---
kind: error_handling
name: Resource Sealed-Union Error Handling with Retrofit-Centric SafeApiCall
category: error_handling
scope:
    - '**'
source_files:
    - app/src/main/java/com/flirttime/Resource.kt
    - app/src/main/java/com/flirttime/core/network/SafeApiCall.kt
    - app/src/main/java/com/flirttime/core/network/model/ApiResponse.kt
    - app/src/main/java/com/flirttime/core/database/SessionManager.kt
    - app/src/main/java/com/flirttime/core/image/FlirtTimeCropContract.kt
---

## Approach

The FlirtTime Android app uses a **sealed-class Result wrapper** (`Resource<T>`) as its cross-cutting error-handling abstraction, combined with a **Retrofit-centric `safeApiCall`/`safeRawApiCall` helper** that catches network exceptions and maps them into `Resource.Error`. There is no centralized exception hierarchy (no custom `BaseException`), no global `UncaughtExceptionHandler`, and no middleware layer — errors are handled locally at the call site via `try/catch` inside the helpers.

## Key Files

- `app/src/main/java/com/flirttime/Resource.kt` — defines the sealed union `Resource<T>` with three states: `Success<T>`, `Error(message, code?, cause?, errorBody?)`, and `Loading`.
- `app/src/main/java/com/flirttime/core/network/SafeApiCall.kt` — two suspend wrappers (`safeApiCall`, `safeRawApiCall`) that wrap Retrofit calls and translate exceptions into `Resource.Error`.
- `app/src/main/java/com/flirttime/core/network/model/ApiResponse.kt` — Retrofit response model carrying `success`, `message`, `data`, `error`, `errors` fields; consumed by `safeApiCall` to distinguish server-side failures from transport failures.
- `app/src/main/java/com/flirttime/core/database/SessionManager.kt` — local I/O error normalization: wraps `IOException` in a `catch` block and emits an empty preferences stream instead of propagating.
- `app/src/main/java/com/flirttime/core/image/FlirtTimeCropContract.kt` — throws `IllegalArgumentException` for invalid arguments (input validation).

## Architecture & Conventions

1. **Sealed-union state machine**: `Resource<T>` is the single type returned by repository/data-layer functions. Consumers branch on `is Success / isError / isLoading` or use the convenience properties `isSuccess`, `isLoading`, `isError`, plus `getOrNull()` and `errorOrNull()`. This replaces throwing exceptions from data sources up to the UI.

2. **Network error mapping lives in one place**: `SafeApiCall.kt` centralizes all Retrofit-related exception handling:
   - `HttpException` → extracts HTTP code + parses JSON error body via `parseErrorMessage` (supports both `{ message }` and `{ errors: [...] }` shapes) → `Resource.Error(message, code, cause, errorBody)`.
   - `SocketTimeoutException` → user-facing "Connection timed out" message.
   - `UnknownHostException` → "Unable to reach server" message.
   - `IOException` → generic "Network error" message.
   - Fallback `Exception` → localized message or "An unexpected error occurred".

3. **Server vs transport failure distinction**: `safeApiCall` first checks `response.success`; when false it treats the response's `message`/`error` as a business-level failure and returns `Resource.Error` without wrapping in a try/catch. Transport failures (exceptions) are caught separately, so UI can potentially treat them differently if desired.

4. **Repository layer emits `Resource.Loading` before async work and forwards `Resource.Success(result.data.data)` after successful calls**, as seen in `AuthRepositoryImpl.kt`. The `Resource` object is the only propagation mechanism observed in this repo.

5. **No custom exception types**: The codebase does not define domain-specific exception classes. Validation errors throw standard Kotlin exceptions (`IllegalArgumentException` in `FlirtTimeCropContract.kt`). All other errors flow through `Resource.Error`.

6. **No global error handler**: There is no `Thread.setDefaultUncaughtExceptionHandler`, no `@file:JvmName` crash reporter, and no Hilt/Koin module providing a global error interceptor beyond the per-call `safeApiCall` wrapper.

7. **UI presentation**: The `Resource.Error` carries a human-readable `message` string intended for direct display; the original `cause` and raw `errorBody` are retained for logging/debugging but are not surfaced to the user by default.

## Conventions & Constraints

- Data-layer functions return `Resource<T>` rather than throwing checked exceptions; callers match on the sealed class.
- Network calls go through `safeApiCall` / `safeRawApiCall` so that `HttpException`, `SocketTimeoutException`, `UnknownHostException`, and `IOException` are consistently mapped to `Resource.Error` with user-friendly messages.
- Server responses where `success == false` are treated as business errors and converted to `Resource.Error` using the response's own `message`/`error` fields.
- `parseErrorMessage` tolerates malformed JSON error bodies by catching `Exception` and returning `null`, falling back to a generic message.
- Local storage operations in `SessionManager.kt` swallow `IOException` and emit an empty preference stream rather than propagating the exception.
- Input validation uses standard `IllegalArgumentException` (e.g., empty source URIs in `FlirtTimeCropContract.kt`).

There is no evidence of a centralized error taxonomy (e.g., `AppError` enum), no panic/recover usage (Kotlin has no `panic`), and no application-wide error middleware beyond the per-call `safeApiCall` wrappers.