package com.pusula.service.data.repository

import com.pusula.service.data.model.CustomerDTO
import com.pusula.service.data.model.ProposalDTO
import com.pusula.service.data.model.ProposalItemDTO
import com.pusula.service.data.remote.ApiService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProposalRepository @Inject constructor(
    private val apiService: ApiService
) {
    suspend fun getProposals(): List<ProposalDTO> = apiService.proposals()

    suspend fun getCustomers(): List<CustomerDTO> = apiService.customers()

    suspend fun createProposal(
        customerId: Long,
        title: String,
        status: String,
        validUntil: String,
        note: String,
        taxRate: Double,
        discount: Double,
        items: List<ProposalItemDTO>
    ): ProposalDTO = apiService.createProposal(
        ProposalDTO(
            customerId = customerId,
            title = title,
            status = status,
            validUntil = validUntil,
            note = note.ifBlank { null },
            taxRate = taxRate,
            discount = discount,
            items = items.map {
                it.copy(totalPrice = it.unitPrice * it.quantity)
            }
        )
    )

    suspend fun updateProposal(
        id: Long,
        customerId: Long,
        title: String,
        status: String,
        validUntil: String,
        note: String,
        taxRate: Double,
        discount: Double,
        items: List<ProposalItemDTO>
    ): ProposalDTO = apiService.updateProposal(
        id = id,
        request = ProposalDTO(
            id = id,
            customerId = customerId,
            title = title,
            status = status,
            validUntil = validUntil,
            note = note.ifBlank { null },
            taxRate = taxRate,
            discount = discount,
            items = items.map {
                it.copy(totalPrice = it.unitPrice * it.quantity)
            }
        )
    )

    suspend fun deleteProposal(id: Long) = apiService.deleteProposal(id)

    suspend fun convertToJob(id: Long): ProposalDTO = apiService.convertProposalToJob(id)

    suspend fun downloadProposalPdf(id: Long): ByteArray = apiService.proposalPdf(id).bytes()
}
