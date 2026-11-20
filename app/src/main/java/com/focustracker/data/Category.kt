package com.focustracker.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 专注分类（时段分类/标签），用户可自定义 */
@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long = 0xFF4CAF50,
)
