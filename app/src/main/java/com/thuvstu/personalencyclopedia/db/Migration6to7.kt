package com.thuvstu.personalencyclopedia.db

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * v6 → v7: era_master テーブル追加 + シードデータ投入 (GAP-5, 設計書§5.8.4/§8.9)。
 * 初期データは「江戸期以降 + 歴史教育で頻出する著名な古典元号」を優先する。
 * 変換ロジック: 元年の西暦 + (yearInEra - 1)
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("""
            CREATE TABLE IF NOT EXISTS `era_master` (
                `name` TEXT NOT NULL PRIMARY KEY,
                `startYear` INTEGER NOT NULL,
                `endYear` INTEGER,
                `sortOrder` INTEGER NOT NULL
            )
        """)

        // ── 著名な古典元号(天正〜) ──
        insertEra(connection, "天文", 1532, 1555, 1)
        insertEra(connection, "永禄", 1558, 1570, 2)
        insertEra(connection, "天正", 1573, 1592, 3)
        insertEra(connection, "文禄", 1592, 1596, 4)
        insertEra(connection, "慶長", 1596, 1615, 5)
        insertEra(connection, "元和", 1615, 1624, 6)

        // ── 江戸時代 ──
        insertEra(connection, "寛永", 1624, 1644, 7)
        insertEra(connection, "正保", 1644, 1648, 8)
        insertEra(connection, "慶安", 1648, 1652, 9)
        insertEra(connection, "承応", 1652, 1655, 10)
        insertEra(connection, "明暦", 1655, 1658, 11)
        insertEra(connection, "万治", 1658, 1661, 12)
        insertEra(connection, "寛文", 1661, 1673, 13)
        insertEra(connection, "延宝", 1673, 1681, 14)
        insertEra(connection, "天和", 1681, 1684, 15)
        insertEra(connection, "貞享", 1684, 1688, 16)
        insertEra(connection, "元禄", 1688, 1704, 17)
        insertEra(connection, "宝永", 1704, 1711, 18)
        insertEra(connection, "正徳", 1711, 1716, 19)
        insertEra(connection, "享保", 1716, 1736, 20)
        insertEra(connection, "元文", 1736, 1741, 21)
        insertEra(connection, "寛保", 1741, 1744, 22)
        insertEra(connection, "延享", 1744, 1748, 23)
        insertEra(connection, "寛延", 1748, 1751, 24)
        insertEra(connection, "宝暦", 1751, 1764, 25)
        insertEra(connection, "明和", 1764, 1772, 26)
        insertEra(connection, "安永", 1772, 1781, 27)
        insertEra(connection, "天明", 1781, 1789, 28)
        insertEra(connection, "寛政", 1789, 1801, 29)
        insertEra(connection, "享和", 1801, 1804, 30)
        insertEra(connection, "文化", 1804, 1818, 31)
        insertEra(connection, "文政", 1818, 1830, 32)
        insertEra(connection, "天保", 1830, 1844, 33)
        insertEra(connection, "弘化", 1844, 1848, 34)
        insertEra(connection, "嘉永", 1848, 1854, 35)
        insertEra(connection, "安政", 1854, 1860, 36)
        insertEra(connection, "万延", 1860, 1861, 37)
        insertEra(connection, "文久", 1861, 1864, 38)
        insertEra(connection, "元治", 1864, 1865, 39)
        insertEra(connection, "慶応", 1865, 1868, 40)

        // ── 近現代 ──
        insertEra(connection, "明治", 1868, 1912, 41)
        insertEra(connection, "大正", 1912, 1926, 42)
        insertEra(connection, "昭和", 1926, 1989, 43)
        insertEra(connection, "平成", 1989, 2019, 44)
        insertEra(connection, "令和", 2019, null, 45)

        // ── 著名な古典元号(天正より前、歴史教育で頻出) ──
        insertEra(connection, "天平", 729, 749, 46)
        insertEra(connection, "弘仁", 810, 824, 47)
        insertEra(connection, "承和", 834, 848, 48)
        insertEra(connection, "貞観", 859, 877, 49)
        insertEra(connection, "保元", 1156, 1159, 50)
        insertEra(connection, "平治", 1159, 1160, 51)
        insertEra(connection, "承久", 1219, 1222, 52)
        insertEra(connection, "文永", 1264, 1275, 53)
        insertEra(connection, "建武", 1334, 1336, 54)
        insertEra(connection, "応仁", 1467, 1469, 55)
        insertEra(connection, "文明", 1469, 1487, 56)
    }

    private fun insertEra(connection: SQLiteConnection, name: String, startYear: Int, endYear: Int?, sortOrder: Int) {
        connection.prepare(
            "INSERT OR REPLACE INTO `era_master` (`name`, `startYear`, `endYear`, `sortOrder`) VALUES (?, ?, ?, ?)"
        ).use { stmt ->
            stmt.bindText(1, name)
            stmt.bindLong(2, startYear.toLong())
            if (endYear == null) stmt.bindNull(3) else stmt.bindLong(3, endYear.toLong())
            stmt.bindLong(4, sortOrder.toLong())
            stmt.step()
        }
    }
}
