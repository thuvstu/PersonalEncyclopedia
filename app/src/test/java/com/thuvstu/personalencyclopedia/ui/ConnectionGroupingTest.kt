package com.thuvstu.personalencyclopedia.ui

import com.thuvstu.personalencyclopedia.db.dao.ConnectionWithEntry
import com.thuvstu.personalencyclopedia.ui.component.groupConnections
import org.junit.Assert.assertEquals
import org.junit.Test

/** ★wt58: 接続セクションのグループ化（前提/次/対比/関連/参照）と向き判定 */
class ConnectionGroupingTest {
    private fun c(type: String, isSource: Boolean, title: String, strength: Float = 0.8f) = ConnectionWithEntry(
        connectionId = title, relationType = type, strength = strength, note = null,
        isDirected = type != "related" && type != "contrast", otherEntryId = title, otherEntryTitle = title,
        otherEntryType = "definition", isSource = isSource
    )

    @Test
    fun prerequisite_direction_splitsIntoBeforeAndNext() {
        val g = groupConnections(listOf(
            c("prerequisite", isSource = true, title = "次A"),
            c("prerequisite", isSource = false, title = "前B"),
            c("extends", isSource = true, title = "次C"),
            c("contrast", isSource = true, title = "対D"),
            c("related", isSource = true, title = "関E", strength = 0.5f),
            c("related", isSource = false, title = "関F", strength = 0.9f),
            c("references", isSource = false, title = "参G"),
        )).associateBy { it.key }
        assertEquals(listOf("前B"), g.getValue("before").items.map { it.otherEntryTitle })
        assertEquals(listOf("次A", "次C"), g.getValue("next").items.map { it.otherEntryTitle })
        assertEquals(listOf("対D"), g.getValue("contrast").items.map { it.otherEntryTitle })
        assertEquals(listOf("関F", "関E"), g.getValue("related").items.map { it.otherEntryTitle })
        assertEquals(listOf("参G"), g.getValue("other").items.map { it.otherEntryTitle })
    }
}
