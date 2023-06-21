package com.spn.secquraiseassignment.Room

import androidx.room.*

@Dao
interface DAO {
    @Query("SELECT * FROM data")
    suspend fun getAllData(): List<UploadData>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertData(myData: UploadData)

    @Delete
    suspend fun deleteData(myData: UploadData)
}