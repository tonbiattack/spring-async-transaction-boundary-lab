# E002: `@Async` 通知がコミット前の請求書を読んでしまう

## 目的

請求書の作成後に開始する非同期通知は、呼び出し元と同じトランザクションを共有しなくても、コミット済みの請求書を読み取れなければならない。本ラボでは、`invoice-001` を保存して通知を開始した場合、ワーカーの観測結果が `invoiceVisible=true` となり、呼び出し終了後にも請求書がH2に残ることを契約とする。

## 最初に観測した事実

バグ状態はコミット `cda7e87`（`非同期通知が未コミット請求書を読む状態を再現する`）に保存した。`InvoiceService` はトランザクション内で請求書を保存した直後に `NotificationService` の `@Async` メソッドを起動する。再現専用のラッチは、ワーカーの読取りが呼び出し元メソッドの復帰、すなわちコミットより前に完了する順序を固定する。

```bash
git switch --detach cda7e87
mvn --batch-mode -Dtest=InvoiceServiceIntegrationTest test
```

| 観測項目 | 期待 | 実際 | 根拠 |
| --- | --- | --- | --- |
| 実行スレッド | `notification-` 接頭辞の専用Executor | `notification-1` | ワーカーログ |
| ワーカーのトランザクション | 呼び出し元のトランザクションを共有しない | `transactionActive=false` | ワーカーログ |
| ワーカーの請求書可視性 | `invoiceVisible=true` | `invoiceVisible=false` | ワーカーログとテスト |
| 呼び出し後の最終状態 | 請求書がH2に残る | `true` | 統合テストの最終アサーション |

```text
transactionActive=false, invoiceVisible=false, workerThread=notification-1
[通知ワーカーはコミット済み請求書を読み取れること]
Expecting value to be true but was false
```

この結果は、非同期ワーカーが動かなかったことを示すものではない。ワーカーは専用Executorで実行され、そこでトランザクションが有効ではないこと、かつ請求書がまだ読めないことを独立に観測している。

## テストの境界

`@SpringBootTest`、Spring JDBC、H2、実際の `@Async` Executorを使う統合テストを選んだ。単体テストで `NotificationService` を直接呼ぶだけでは、Springの非同期プロキシ、呼び出しスレッドとワーカースレッドの分離、JDBCトランザクションの可視性を同時に検証できないためである。

| 要素 | 決定 |
| --- | --- |
| 公開境界 | `InvoiceService#createInvoiceAndNotify` |
| 初期状態 | H2の`invoice`テーブルを空にする |
| 入力 | `invoice-001` |
| 直接観測 | `NotificationObservation` のスレッド名、トランザクション状態、可視性 |
| 最終観測 | `InvoiceRepository#exists` によるH2からの再読込 |
| 決定性 | 失敗状態だけは`CountDownLatch`で読取りをコミット前に固定し、時刻や`sleep`を使わない |

## 仮説と切り分け

| 仮説 | 確認方法 | 結果 |
| --- | --- | --- |
| `@Async` が無効で同期実行されている | ワーカーのスレッド名を観測する | 棄却。`notification-1` で実行された。 |
| 呼び出し元のトランザクションがワーカーへ伝播している | `TransactionSynchronizationManager.isActualTransactionActive()` をワーカーで観測する | 棄却。`false` だった。 |
| ワーカーがコミット前に請求書を読んでいる | ラッチで読取り順序を固定し、H2の可視性を観測する | 採用。`invoiceVisible=false` だった。 |

Springの `TransactionSynchronizationManager` は、資源とトランザクション同期情報をスレッド単位で管理する。[1] そのため、別Executorで動くワーカーが呼び出し元スレッドのJDBCトランザクションを当然に共有することはない。

## 原因

バグ状態では、`InvoiceService` がトランザクション内で請求書を保存した直後に `@Async` メソッドを呼び出していた。ワーカーは別スレッドで実行されるため、呼び出し元のスレッドに束縛されたトランザクションを共有しない。[1] ラッチによってワーカーの読取りをコミット前に固定すると、H2から請求書は見えなかった。

原因は `@Async` の自己呼び出しでも、例外のロールバック規則でもない。非同期ワーカーが実行される時点と、保存を含むトランザクションがコミットされる時点の境界が契約と一致していないことである。

## 修正

請求書保存後に `InvoiceCreatedEvent` を発行し、`@TransactionalEventListener` を持つ別Beanで非同期通知を開始するよう変更した。`@TransactionalEventListener` は既定でコミットフェーズに束縛されるため、コミットが成功してからリスナーが実行される。[2]

```java
@Transactional
public CompletableFuture<NotificationObservation> createInvoiceAndNotify(String invoiceId) {
    CompletableFuture<NotificationObservation> notification =
            notificationService.prepareObservation(invoiceId);
    repository.save(invoiceId);
    eventPublisher.publishEvent(new InvoiceCreatedEvent(invoiceId));
    return notification;
}

@TransactionalEventListener
public void notifyAfterCommit(InvoiceCreatedEvent event) {
    notificationService.readInvoiceForNotification(event.invoiceId());
}
```

この修正は、ワーカーを呼び出し元のトランザクションに無理に伝播させない。通知の開始時点だけをコミット後へ移す。修正後のログは `transactionActive=false, invoiceVisible=true` であり、非同期ワーカーが独立したままコミット済みの請求書を読めることを示す。

## 再発防止テスト

元の `InvoiceServiceIntegrationTest#非同期通知はコミット済みの請求書を読み取れる` を残した。テストは、ワーカーの実行スレッド、ワーカーでのトランザクション状態、ワーカーからの請求書可視性、呼び出し後のH2最終状態を別々に確認する。

| 順序 | 確認する契約 |
| --- | --- |
| 1 | 通知処理は `notification-` Executorで実行される。 |
| 2 | ワーカーは呼び出し元のトランザクションを共有しない。 |
| 3 | ワーカーはコミット済み請求書を読み取れる。 |
| 4 | 呼び出し後に請求書はH2へ永続化されている。 |

## 再現手順

```bash
# バグ状態：コミット前の読取りにより失敗する
git switch --detach cda7e87
mvn --batch-mode -Dtest=InvoiceServiceIntegrationTest test

# 修正状態：同じテストを含む全テストが成功する
git switch main
mvn --batch-mode test
```

修正コミットは `c64976c`（`通知を請求書コミット後に開始する`）である。実行出力は `docs/bug-state-test-output.log` と `docs/fixed-state-test-output.log` に保存している。

## 適用範囲と注意点

本ラボは、スレッド束縛の通常のJDBCトランザクションと、請求書作成後の非同期読取りを対象にする。イベントが必ず外部通知として配信されること、プロセス障害に耐えて配送を再試行すること、複数サービス間の整合性を保証することまでは扱わない。そのような要件には、アウトボックス、メッセージブローカー、冪等性設計などを別途検討する必要がある。

また、トランザクションが実行されていないとき、`@TransactionalEventListener` は既定では呼び出されない。[2] 本ラボは請求書保存のトランザクションが存在することを前提にしており、`fallbackExecution=true` は採用していない。

## References

[1] [Spring Framework API — TransactionSynchronizationManager](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronizationManager.html)

[2] [Spring Framework Reference — Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
