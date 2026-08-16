package com.example.asynctx;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final InvoiceRepository repository;
    private final ConcurrentMap<String, CompletableFuture<NotificationObservation>> observations =
            new ConcurrentHashMap<>();

    public NotificationService(InvoiceRepository repository) {
        this.repository = repository;
    }

    public CompletableFuture<NotificationObservation> prepareObservation(String invoiceId) {
        return observations.computeIfAbsent(invoiceId, ignored -> new CompletableFuture<>());
    }

    @Async("notificationExecutor")
    public CompletableFuture<NotificationObservation> readInvoiceForNotification(String invoiceId) {
        NotificationObservation observation = new NotificationObservation(
                TransactionSynchronizationManager.isActualTransactionActive(),
                repository.exists(invoiceId),
                Thread.currentThread().getName()
        );
        log.info("transactionActive={}, invoiceVisible={}, workerThread={}",
                observation.transactionActive(), observation.invoiceVisible(), observation.workerThread());
        prepareObservation(invoiceId).complete(observation);
        return CompletableFuture.completedFuture(observation);
    }
}
