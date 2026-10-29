Single-feature presentation module under `com.flirttime.feature.main.presentation.moments` organized as:
- Root screen: `MomentsFragment` (RecyclerView feed) + `MomentsViewModel` (pagination via `Resource<T>` StateFlow/SharedFlow), both depending on `MomentsRepository` from the domain layer.
- Data model: `MomentPost` data class with helper `getAllMediaUrls()` consumed by `MomentsAdapter` and `MomentMediaPagerAdapter`.
- Sub-packages per interaction surface, each pairing a `BottomSheetDialogFragment` with its own adapters and mock providers:
  - `add/` — `AddMomentActivity` (media picker + camera + crop via `FlirtTimeCropContract`, caption, privacy, location) driven by `AddMomentViewModel`; `PhotoSourceBottomSheetDialogFragment` selects camera/gallery.
  - `comments/` — `CommentsBottomSheetDialogFragment` + `CommentsAdapter` + `CommentItem`.
  - `gift/` — `SendGiftBottomSheetDialogFragment` + `GiftAdapter` + `GiftItem` + `MockGiftsProvider`.
  - `location/` — `WhereAreYouVibingBottomSheetDialogFragment` + `VibeCategoryAdapter` / `VibeSpotsAdapter` + `VibeSpotItem` + `MockVibeSpotsProvider`.
- Navigation out of this module goes through `AppNavigator` / `Destination` (e.g. to AddMoment, Notification); navigation into it is triggered from elsewhere in the app.
- Dependency direction: Fragment/Activity → ViewModel → domain repository; sub-sheets communicate back to the host fragment via Kotlin lambdas (`onGiftSent`, `onSpotSelected`, `onCitySelected`) rather than shared state.