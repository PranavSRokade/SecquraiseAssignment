package com.spn.secquraiseassignment.Room

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "data")
@TypeConverters(MapTypeConverter::class, UriTypeConverter::class)
data class UploadData(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val data: Map<String, String>,
    val image: Uri
)
