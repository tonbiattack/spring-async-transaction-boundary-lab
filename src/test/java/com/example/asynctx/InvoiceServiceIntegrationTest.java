package com.example.asynctx;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class InvoiceServiceIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void 非同期通知はコミット済みの請求書を読み取れる() throws Exception {
        CompletableFuture<NotificationObservation> notification =
                invoiceService.createInvoiceAndNotify("invoice-001");

        NotificationObservation observation = notification.get(5, TimeUnit.SECONDS);

        assertThat(observation.workerThread())
                .as("通知は専用Executorで実行されること")
                .startsWith("notification-");
        assertThat(observation.transactionActive())
                .as("通知ワーカーは呼び出し元のトランザクションを共有しないこと")
                .isFalse();
        assertThat(observation.invoiceVisible())
                .as("通知ワーカーはコミット済み請求書を読み取れること")
                .isTrue();
        assertThat(repository.exists("invoice-001"))
                .as("呼び出し元の請求書は最終的に永続化されていること")
                .isTrue();
    }
}
