package com.cargenome.app.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cargenome.app.data.db.entity.ExpenseCategory
import com.cargenome.app.data.db.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow

/** One row of the "where the money went" breakdown. */
data class CategoryTotal(
    val category: ExpenseCategory,
    val totalMinor: Long,
)

@Dao
interface ExpenseDao {

    @Query(
        """
        SELECT * FROM expenses
        WHERE vehicleId = :vehicleId
        ORDER BY incurredAt DESC, id DESC
        """,
    )
    fun observeForVehicle(vehicleId: Long): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE id = :id")
    suspend fun findById(id: Long): ExpenseEntity?

    @Query(
        """
        SELECT * FROM expenses
        WHERE vehicleId = :vehicleId
        ORDER BY incurredAt ASC, id ASC
        """,
    )
    suspend fun listChronological(vehicleId: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses ORDER BY id ASC")
    suspend fun listAll(): List<ExpenseEntity>

    @Query(
        """
        SELECT category, SUM(amount_minor) AS totalMinor FROM expenses
        WHERE vehicleId = :vehicleId AND incurredAt >= :from AND incurredAt < :to
        GROUP BY category
        ORDER BY totalMinor DESC
        """,
    )
    fun observeTotalsByCategory(vehicleId: Long, from: Long, to: Long): Flow<List<CategoryTotal>>

    @Query(
        """
        SELECT COALESCE(SUM(amount_minor), 0) FROM expenses
        WHERE vehicleId = :vehicleId AND incurredAt >= :from AND incurredAt < :to
        """,
    )
    fun observeSpendBetween(vehicleId: Long, from: Long, to: Long): Flow<Long>

    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>): List<Long>

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)
}
