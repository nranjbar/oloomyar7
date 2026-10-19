package com.oloomyar.app

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.junit4.ComposeContentTestRule

/** Wait for the real, automatic launch presentation; do not bypass it in tests. */
internal fun ComposeContentTestRule.awaitGrade7Home() {
    waitUntil(15000) { onAllNodesWithTag("hub-list").fetchSemanticsNodes().isNotEmpty() }
}
