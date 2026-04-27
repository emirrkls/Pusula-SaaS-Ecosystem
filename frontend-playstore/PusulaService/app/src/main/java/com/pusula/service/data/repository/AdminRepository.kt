package com.pusula.service.data.repository

import com.pusula.service.data.model.DashboardKPIs
import com.pusula.service.data.model.FieldPin
import com.pusula.service.data.model.InventoryItemDTO
import com.pusula.service.data.model.ProfitAnalysis
import com.pusula.service.data.model.QuotaStatus
import com.pusula.service.data.model.TechnicianStat
import com.pusula.service.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getDashboardKPIs(): DashboardKPIs = apiService.dashboard()

    suspend fun getTechnicianStats(): List<TechnicianStat> = apiService.technicianStats()

    suspend fun getProfitAnalysis(): ProfitAnalysis = apiService.profitAnalysis()

    suspend fun getQuotaStatus(): QuotaStatus = apiService.quotaStatus()

    suspend fun getFieldRadar(): List<FieldPin> = apiService.fieldRadar()

    suspend fun createInventoryItem(
        partName: String,
        quantity: Int,
        buyPrice: Double?,
        sellPrice: Double?,
        criticalLevel: Int?,
        brand: String?,
        category: String?,
        barcode: String?
    ): InventoryItemDTO = apiService.createInventory(
        InventoryItemDTO(
            id = 0L,
            partName = partName,
            quantity = quantity,
            buyPrice = buyPrice,
            sellPrice = sellPrice,
            criticalLevel = criticalLevel,
            brand = brand,
            category = category,
            barcode = barcode
        )
    )

    suspend fun updateInventoryItem(
        id: Long,
        partName: String,
        quantity: Int,
        buyPrice: Double?,
        sellPrice: Double?,
        criticalLevel: Int?,
        brand: String?,
        category: String?,
        barcode: String?
    ): InventoryItemDTO = apiService.updateInventory(
        id = id,
        request = InventoryItemDTO(
            id = id,
            partName = partName,
            quantity = quantity,
            buyPrice = buyPrice,
            sellPrice = sellPrice,
            criticalLevel = criticalLevel,
            brand = brand,
            category = category,
            barcode = barcode
        )
    )

    suspend fun deleteInventoryItem(id: Long) = apiService.deleteInventory(id)

    suspend fun findInventoryByBarcode(code: String): InventoryItemDTO = apiService.inventoryByBarcode(code)
}
