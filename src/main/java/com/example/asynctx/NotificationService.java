package com.example.asynctx;

import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final InvoiceRepository repository;
    private final NotificationGate gate;

    public NotificationService(InvoiceRepository repository, NotificationGate gate) {
        this.repository = repository;
        this.gate = gate;
    }

    @Async("notificationExecutor")
    public CompletableFuture<NotificationObservation> readInvoiceForNotification(String invoiceId) {
        gate.signalWorkerStarted();
        gate.awaitReadPermission();

        NotificationObservation observation = new NotificationObservation(
                TransactionSynchronizationManager.isActualTransactionActive(),
                repository.exists(invoiceId),
                Thread.currentThread().getName()
        );
        log.info("transactionActive={}, invoiceVisible={}, workerThread={}",
                observation.transactionActive(), observation.invoiceVisible(), observation.workerThread());
        gate.signalWorkerReadCompleted();
        return CompletableFuture.completedFuture(observation);
    }
}
