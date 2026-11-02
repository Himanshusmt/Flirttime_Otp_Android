package com.flirttime.feature.chat.media

/**
 * An attachment already prepared on the UI side — a compressed photo or a finished
 * voice note — that is ready to be uploaded and sent.
 */
class PreparedMedia(
    /** Wire kind: `image`, `video` or `audio`. */
    val kind: String,
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
    /** Local file path kept for the optimistic bubble preview. */
    val localPath: String,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0
)
