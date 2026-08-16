package com.example.asynctx;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InvoiceNotificationListener {

    private final NotificationService notificationService;

    public InvoiceNotificationListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @TransactionalEventListener
    public void notifyAfterCommit(InvoiceCreatedEvent event) {
        notificationService.readInvoiceForNotification(event.invoiceId());
    }
}
