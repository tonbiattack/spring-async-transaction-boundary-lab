package com.example.asynctx;

public record NotificationObservation(
        boolean transactionActive,
        boolean invoiceVisible,
        String workerThread
) {
}
