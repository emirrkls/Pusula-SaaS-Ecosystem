package com.pusula.backend.service;

public class PaymentOwnershipException extends RuntimeException {

    public PaymentOwnershipException() {
        super("Odeme kaydi baska bir sirkete ait");
    }
}
