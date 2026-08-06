package com.pusula.desktop.util;

import com.pusula.desktop.dto.CustomerDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CustomerSearchSupportTest {

    @Test
    void matchesTurkishCharactersWithoutRequiringDiacritics() {
        CustomerDTO customer = customer("Çağrı Şimşek", "0532 111 22 33");

        assertTrue(CustomerSearchSupport.matches(customer, "cagri"));
        assertTrue(CustomerSearchSupport.matches(customer, "simsek"));
        assertFalse(CustomerSearchSupport.matches(customer, "mehmet"));
    }

    @Test
    void matchesPhoneRegardlessOfFormatting() {
        CustomerDTO customer = customer("Ayşe", "+90 (532) 111-22-33");

        assertTrue(CustomerSearchSupport.matches(customer, "53211122"));
        assertTrue(CustomerSearchSupport.matches(customer, "0532 111"));
    }

    @Test
    void safelyHandlesMissingCustomerFields() {
        CustomerDTO customer = customer(null, null);

        assertEquals(" - -", CustomerSearchSupport.displayText(customer));
        assertFalse(CustomerSearchSupport.matches(customer, "ali"));
        assertTrue(CustomerSearchSupport.matches(customer, ""));
    }

    private CustomerDTO customer(String name, String phone) {
        CustomerDTO customer = new CustomerDTO();
        customer.setName(name);
        customer.setPhone(phone);
        return customer;
    }
}
