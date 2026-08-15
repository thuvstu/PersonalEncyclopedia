package com.thuvstu.personalencyclopedia.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v6 → v7: era_master テーブル追加 + シードデータ投入 (GAP-5, 設計書§5.8.4/§8.9)。
 * 初期データは「江戸期以降 + 歴史教育で頻出する著名な古典元号」を優先する。
 * 変換ロジック: 元年の西暦 + (yearInEra - 1)
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `era_master` (
                `name` TEXT NOT NULL PRIMARY KEY,
                `startYear` INTEGER NOT NULL,
                `endYear` INTEGER,
                `sortOrder` INTEGER NOT NULL
            )
        """)

        // ── 著名な古典元号(天正〜) ──
        insertEra(db, "天文", 1532, 1555, 1)
        insertEra(db, "永禄", 1558, 1570, 2)
        insertEra(db, "天正", 1573, 1592, 3)
        insertEra(db, "文禄", 1592, 1596, 4)
        insertEra(db, "慶長", 1596, 1615, 5)
        insertEra(db, "元和", 1615, 1624, 6)

        // ── 江戸時代 ──
        insertEra(db, "寛永", 1624, 1644, 7)
        insertEra(db, "正保", 1644, 1648, 8)
        insertEra(db, "慶安", 1648, 1652, 9)
        insertEra(db, "承応", 1652, 1655, 10)
        insertEra(db, "明暦", 1655, 1658, 11)
        insertEra(db, "万治", 1658, 1661, 12)
        insertEra(db, "寛文", 1661, 1673, 13)
        insertEra(db, "延宝", 1673, 1681, 14)
        insertEra(db, "天和", 1681, 1684, 15)
        insertEra(db, "貞享", 1684, 1688, 16)
        insertEra(db, "元禄", 1688, 1704, 17)
        insertEra(db, "宝永", 1704, 1711, 18)
        insertEra(db, "正徳", 1711, 1716, 19)
        insertEra(db, "享保", 1716, 1736, 20)
        insertEra(db, "元文", 1736, 1741, 21)
        insertEra(db, "寛保", 1741, 1744, 22)
        insertEra(db, "延享", 1744, 1748, 23)
        insertEra(db, "寛延", 1748, 1751, 24)
        insertEra(db, "宝暦", 1751, 1764, 25)
        insertEra(db, "明和", 1764, 1772, 26)
        insertEra(db, "安永", 1772, 1781, 27)
        insertEra(db, "天明", 1781, 1789, 28)
        insertEra(db, "寛政", 1789, 1801, 29)
        insertEra(db, "享和", 1801, 1804, 30)
        insertEra(db, "文化", 1804, 1818, 31)
        insertEra(db, "文政", 1818, 1830, 32)
        insertEra(db, "天保", 1830, 1844, 33)
        insertEra(db, "弘化", 1844, 1848, 34)
        insertEra(db, "嘉永", 1848, 1854, 35)
        insertEra(db, "安政", 1854, 1860, 36)
        insertEra(db, "万延", 1860, 1861, 37)
        insertEra(db, "文久", 1861, 1864, 38)
        insertEra(db, "元治", 1864, 1865, 39)
        insertEra(db, "慶応", 1865, 1868, 40)

        // ── 近現代 ──
        insertEra(db, "明治", 1868, 1912, 41)
        insertEra(db, "大正", 1912, 1926, 42)
        insertEra(db, "昭和", 1926, 1989, 43)
        insertEra(db, "平成", 1989, 2019, 44)
        insertEra(db, "令和", 2019, null, 45)

        // ── 著名な古典元号(天正より前、歴史教育で頻出) ──
        insertEra(db, "天平", 729, 749, 46)
        insertEra(db, "弘仁", 810, 824, 47)
        insertEra(db, "承和", 834, 848, 48)
        insertEra(db, "貞観", 859, 877, 49)
        insertEra(db, "保元", 1156, 1159, 50)
        insertEra(db, "平治", 1159, 1160, 51)
        insertEra(db, "承久", 1219, 1222, 52)
        insertEra(db, "文永", 1264, 1275, 53)
        insertEra(db, "建武", 1334, 1336, 54)
        insertEra(db, "応仁", 1467, 1469, 55)
        insertEra(db, "文明", 1469, 1487, 56)
    }

    private fun insertEra(db: SupportSQLiteDatabase, name: String, startYear: Int, endYear: Int?, sortOrder: Int) {
        db.execSQL(
            "INSERT OR REPLACE INTO `era_master` (`name`, `startYear`, `endYear`, `sortOrder`) VALUES (?, ?, ?, ?)",
            arrayOf(name, startYear, endYear, sortOrder)
        )
    }
}
