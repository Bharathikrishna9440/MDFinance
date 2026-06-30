package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MicrofinanceDao {

    @Query("SELECT * FROM customer_entities ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customer_entities WHERE customerId = :id")
    suspend fun getCustomerById(id: Int): CustomerEntity?

    @Query("SELECT * FROM transaction_entities WHERE customerId = :id")
    suspend fun getTransactionsByCustomerId(id: Int): List<TransactionEntity>

    @Query("SELECT * FROM customer_entities")
    suspend fun getAllCustomersList(): List<CustomerEntity>

    @Query("SELECT * FROM customer_entities WHERE routeDay = :routeDay ORDER BY name ASC")
    fun filterCustomersByRoute(routeDay: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM transaction_entities ORDER BY timestamp DESC")
    fun getTransactionHistories(): Flow<List<TransactionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<CustomerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(transactions: List<TransactionEntity>)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteTransaction(transaction: TransactionEntity)
}
