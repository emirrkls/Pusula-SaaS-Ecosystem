package com.pusula.backend.dto;

import com.pusula.backend.entity.Expense;
import com.pusula.backend.entity.ExpenseCategory;
import com.pusula.backend.entity.ExpenseTreatment;
import com.pusula.backend.entity.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Manual expense input only; IDs, tenant and managed source links are server-owned. */
public record ReliableExpenseCreateRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @NotBlank String description,
        @NotNull LocalDate date,
        @NotNull ExpenseCategory category,
        PaymentMethod paymentMethod,
        ExpenseTreatment financialTreatment) {

    public Expense toExpense(Long companyId) {
        return Expense.builder().companyId(companyId).amount(amount).description(description)
                .date(date).category(category).paymentMethod(paymentMethod)
                .financialTreatment(financialTreatment == null
                        ? ExpenseTreatment.OPERATING_EXPENSE : financialTreatment).build();
    }
}
