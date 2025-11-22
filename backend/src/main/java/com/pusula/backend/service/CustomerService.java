package com.pusula.backend.service;

import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.UserRepository;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;

    public CustomerService(CustomerRepository customerRepository, UserRepository userRepository) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public List<Customer> getAllCustomers() {
        User user = getCurrentUser();
        return customerRepository.findByCompanyId(user.getCompanyId());
    }

    public Customer createCustomer(Customer customer) {
        User user = getCurrentUser();
        customer.setCompanyId(user.getCompanyId());
        return customerRepository.save(customer);
    }

    public Customer updateCustomer(UUID id, Customer updatedCustomer) {
        User user = getCurrentUser();
        Customer existing = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        if (!existing.getCompanyId().equals(user.getCompanyId())) {
            throw new RuntimeException("Unauthorized access to customer");
        }

        existing.setName(updatedCustomer.getName());
        existing.setPhone(updatedCustomer.getPhone());
        existing.setAddress(updatedCustomer.getAddress());
        existing.setCoordinates(updatedCustomer.getCoordinates());

        return customerRepository.save(existing);
    }
}
