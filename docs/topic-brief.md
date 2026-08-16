# 題材企画: `@Async` 通知がコミット前の請求書を読む

## 対象

| 項目 | 内容 |
| --- | --- |
| 対象言語 | Java 21 |
| 対象読者 | Springの `@Transactional` と `@Async` の基本を理解する中級開発者 |
| 難易度プロファイル | 実践・上級 |
| 選定理由 | 非同期実行、スレッド束縛トランザクション、コミット時点の三つを分けて観測する必要がある。 |
| 実行基盤 | Maven 3.8.7、Spring Boot 3.5.0、Spring JDBC、H2、JUnit 5 |
| フレームワーク非依存性 | 該当しない。ユーザーのJava／Spring教材という文脈に合わせ、Springのスレッド束縛トランザクションとイベント位相を直接扱う。 |

## 学習する契約

> 入力 `invoice-001` を保存して通知を開始した場合、期待する状態は「非同期ワーカーがコミット済み請求書を読める」である。しかしバグ状態では、ワーカーが `invoiceVisible=false` を観測する。

### 対象の直接原因

呼び出し元スレッドに束縛されたJDBCトランザクションが、別Executorで動く `@Async` ワーカーへ伝播しないまま、コミット前にワーカーが読取りを行う。[1]

### 対象外

自己呼び出しによる `@Async` 無効化、例外種別ごとのロールバック規則、JPAフラッシュ、配送保証、アウトボックス、メッセージブローカー、リアクティブトランザクションは扱わない。

## 再現設計

| 要素 | 決定 |
| --- | --- |
| 公開境界 | `InvoiceService#createInvoiceAndNotify` |
| 入力・初期状態 | H2の`invoice`テーブルを空にし、`invoice-001`を保存する。 |
| Redの観測 | 非同期ワーカーの `invoiceVisible` が `true` を期待して `false` になる。 |
| 最終観測 | 呼び出し完了後にリポジトリから請求書の存在を再読込する。 |
| 決定性 | バグ状態だけに `CountDownLatch` を用い、ワーカー読取りをコミット前に固定する。 |
| 固定状態の検証コマンド | `mvn --batch-mode test` |
| バグ状態の確認コマンド | `mvn --batch-mode -Dtest=InvoiceServiceIntegrationTest test` |

## 仮説

| 仮説 | どう検証または除外するか |
| --- | --- |
| `@Async` が無効で同期実行されている | ワーカーのスレッド名を確認する。 |
| 呼び出し元のトランザクションがワーカーへ伝播している | ワーカーで `isActualTransactionActive()` を観測する。 |
| コミット前にワーカーが読んでいる | ラッチで順序を固定し、ワーカーの可視性を確認する。 |

## 予定した履歴

| 順序 | コミットの目的 | 期待する状態 |
| --- | --- | --- |
| 1 | `cda7e87`：非同期通知が未コミット請求書を読む状態を再現する | 対象テストが `invoiceVisible=false` で失敗する。 |
| 2 | `c64976c`：通知を請求書コミット後に開始する | 同じテストと全テストが成功する。 |

## References

[1] [Spring Framework API — TransactionSynchronizationManager](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/transaction/support/TransactionSynchronizationManager.html)
