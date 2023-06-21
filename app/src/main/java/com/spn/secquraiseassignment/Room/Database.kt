package com.spn.secquraiseassignment.Room

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [UploadData::class], version = 1)
abstract class Database: RoomDatabase() {
    abstract fun dataDao(): DAO
}