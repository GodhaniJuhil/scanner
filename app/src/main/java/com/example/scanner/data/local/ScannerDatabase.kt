package com.example.scanner.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.scanner.data.local.dao.DocumentDao
import com.example.scanner.data.local.entity.DocumentEntity
import com.example.scanner.data.local.entity.PageEntity

@Database(entities = [DocumentEntity::class, PageEntity::class], version = 1, exportSchema = true)
abstract class ScannerDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
}
