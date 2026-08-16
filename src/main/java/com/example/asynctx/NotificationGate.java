package com.example.asynctx;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class NotificationGate {

    private volatile CountDownLatch workerStarted = new CountDownLatch(1);
    private volatile CountDownLatch readPermitted = new CountDownLatch(1);
    private volatile CountDownLatch workerReadCompleted = new CountDownLatch(1);

    public void reset() {
        workerStarted = new CountDownLatch(1);
        readPermitted = new CountDownLatch(1);
        workerReadCompleted = new CountDownLatch(1);
    }

    public void signalWorkerStarted() {
        workerStarted.countDown();
    }

    public void awaitWorkerStarted() {
        await(workerStarted, "非同期通知ワーカーが開始しませんでした");
    }

    public void awaitReadPermission() {
        await(readPermitted, "非同期通知ワーカーへ読取り許可が渡されませんでした");
    }

    public void allowRead() {
        readPermitted.countDown();
    }

    public void signalWorkerReadCompleted() {
        workerReadCompleted.countDown();
    }

    public void awaitWorkerReadCompleted() {
        await(workerReadCompleted, "非同期通知ワーカーの読取りが完了しませんでした");
    }

    private void await(CountDownLatch latch, String failureMessage) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException(failureMessage);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(failureMessage, exception);
        }
    }
}
