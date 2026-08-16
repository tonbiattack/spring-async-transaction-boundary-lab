package com.example.asynctx;

import java.util.concurrent.CompletableFuture;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private final InvoiceRepository repository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    public InvoiceService(
            InvoiceRepository repository,
            NotificationService notificationService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.repository = repository;
        this.notificationService = notificationService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CompletableFuture<NotificationObservation> createInvoiceAndNotify(String invoiceId) {
        CompletableFuture<NotificationObservation> notification =
                notificationService.prepareObservation(invoiceId);
        repository.save(invoiceId);
        eventPublisher.publishEvent(new InvoiceCreatedEvent(invoiceId));
        return notification;
    }
}
