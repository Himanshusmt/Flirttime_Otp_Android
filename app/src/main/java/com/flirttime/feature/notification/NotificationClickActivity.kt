package com.flirttime.feature.notification

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * Transparent trampoline launched from an FCM system notification tap.
 * Forwards to [NotificationRouter] then finishes immediately.
 */
class NotificationClickActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val routeName = intent.getStringExtra(EXTRA_ROUTE_TYPE).orEmpty()
        val routeType = runCatching { NotificationRouteType.valueOf(routeName) }
            .getOrDefault(NotificationRouteType.UNKNOWN)

        val keys = intent.getStringArrayExtra(EXTRA_PAYLOAD_KEYS).orEmpty()
        val payload = LinkedHashMap<String, String>()
        keys.forEach { key ->
            val value = intent.getStringExtra(EXTRA_PAYLOAD_PREFIX + key)?.trim().orEmpty()
            if (value.isNotEmpty()) payload[key] = value
        }

        NotificationRouter.open(this, routeType, payload)
        finish()
    }

    companion object {
        const val EXTRA_ROUTE_TYPE = "extra_route_type"
        const val EXTRA_PAYLOAD_KEYS = "extra_payload_keys"
        const val EXTRA_PAYLOAD_PREFIX = "extra_payload_"
    }
}
