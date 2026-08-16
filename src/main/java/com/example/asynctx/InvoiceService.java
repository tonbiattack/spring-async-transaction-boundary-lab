package com.example.asynctx;

import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private final InvoiceRepository repository;
    private final NotificationService notificationService;
    private final NotificationGate gate;

    public InvoiceService(
            InvoiceRepository repository,
            NotificationService notificationService,
            NotificationGate gate
    ) {
        this.repository = repository;
        this.notificationService = notificationService;
        this.gate = gate;
    }

    @Transactional
    public CompletableFuture<NotificationObservation> createInvoiceAndNotify(String invoiceId) {
        repository.save(invoiceId);
        CompletableFuture<NotificationObservation> notification =
                notificationService.readInvoiceForNotification(invoiceId);

        gate.awaitWorkerStarted();
        gate.allowRead();
        gate.awaitWorkerReadCompleted();
        return notification;
    }
}
