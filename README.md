# E002: `@Async` 通知がコミット前の請求書を読んでしまう

このリポジトリは、Java／Spring Bootで「保存直後に開始した非同期通知が、その請求書を読めない」不具合を、失敗する統合テスト、実行時ログ、最小修正、回帰テストで学ぶ実行可能なラボです。既定ブランチはテストが成功する状態を保ち、バグ状態はGit履歴に残しています。

> 学習する契約：請求書作成後に開始する非同期通知は、呼び出し元のトランザクションを共有しなくても、コミット済みの請求書を読み取れなければなりません。

## 学習の進め方

| 段階 | 実施内容 | 観測すること |
| --- | --- | --- |
| 再現 | バグコミットで統合テストを実行する | 専用ワーカーは請求書を見つけられず、`invoiceVisible=false` となる |
| 観測 | ワーカーのログとH2の最終状態を確認する | ワーカーにはトランザクションがなく、呼び出し元のコミット前に読んでいる |
| 修正 | 保存時にイベントを発行し、コミット後に通知を始める | `@TransactionalEventListener` がコミット後に非同期通知を起動する |
| 回帰防止 | 同じ統合テストを再実行する | ワーカーは `invoiceVisible=true` を観測し、請求書は最終的に永続化される |

## 収録済み教材

| ID | テーマ | バグ状態の観測 | 修正後に守る契約 |
| --- | --- | --- | --- |
| E002 | `@Async` とトランザクション完了時点 | `transactionActive=false, invoiceVisible=false` | 非同期通知はコミット済み請求書を読める |

## 必要な環境

| 項目 | 本ラボで検証したバージョン |
| --- | --- |
| JDK | 21.0.11 |
| Maven | 3.8.7 |
| Spring Boot | 3.5.0 |
| データベース | H2（インメモリ） |

## 修正後のテストを実行する

```bash
mvn --batch-mode test
```

テストは、非同期ワーカーが専用Executorで動くこと、呼び出し元のトランザクションを共有しないこと、コミット済み請求書を読めること、最終的に請求書が残ることを分けて確認します。

## バグを自分で再現する

```bash
git switch --detach cda7e87
mvn --batch-mode -Dtest=InvoiceServiceIntegrationTest test
# invoiceVisible=true を期待するアサーションが false で失敗する

git switch main
mvn --batch-mode test
# BUILD SUCCESS
```

## プロジェクト構成

```text
src/main/java/com/example/asynctx/
├── InvoiceService.java              # 請求書保存とイベント発行
├── InvoiceNotificationListener.java # コミット後に非同期通知を開始
├── NotificationService.java         # 非同期ワーカーと観測結果
└── InvoiceRepository.java           # H2への読み書き
src/test/java/com/example/asynctx/
└── InvoiceServiceIntegrationTest.java

docs/
├── topic-brief.md
├── novelty-report.md
├── debugging-record.md
├── bug-state-test-output.log
└── fixed-state-test-output.log
```

Springのトランザクション資源と同期情報はスレッド単位で管理されるため、別Executorで動く `@Async` のワーカーは呼び出し元スレッドのトランザクションを共有しません。[1] トランザクションに束縛したイベントリスナーは、既定でコミット後に実行されます。[2]

詳細な観測と仮説比較は、[デバッグ記録](docs/debugging-record.md)を参照してください。

## References

[1] [Spring Framework API — TransactionSynchronizationManager](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronizationManager.html)

[2] [Spring Framework Reference — Transaction-bound Events](https://docs.spring.io/spring-framework/reference/data-access/transaction/event.html)
