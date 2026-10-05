# Inventory export

The Android **Master Property Inventory** action uses a disk-backed export pipeline.
The app scans the last 7 days. That window starts at 00:00 seven days before the latest message's date.

The app reads text-only messages from active group chats through scoped SQLite
cursors. It caches parsed text in `noBackupFilesDir/inventory_cache/parsed-text.db`,
with at most 256 parsed texts retained in memory. Sender details are applied to
each post separately. Cache entries include non-listings and survive subsequent
runs. Increment `StreamingInventoryStore.CACHE_VERSION` whenever extraction,
project selection, or field conversion changes.

Listings are consolidated in a per-run SQLite database under `cacheDir`.
The earliest exact normalized dealer/message repost is retained; older distinct
ads for the same society and dealer are marked as duplicates using full message
timestamps, with stable encounter-order tie breaking. The app's existing project
registry and multi-listing parsing rules remain in use, so its extracted fields
are not promised to match the separate Python script.

SQLite orders the inventory; CSV rows are written directly to a staged file.
Only a completed file replaces the shareable export. Temporary listing files are
removed on completion, failure, or coroutine cancellation. The persistent text
cache is excluded from Android backup and is removed by the decrypted-data purge
operation. Purging is refused while an export is running.

The progress dialog reports group scanning, processed messages, cache reuse,
preparation, and CSV writing. The original database is opened read-only.

Verification:

```sh
./gradlew :core-crypto:test :app:assembleDebug
```

`StreamingInventoryStoreTest` exercises real SQLite cache persistence, sender
isolation, duplicate ordering, empty output, interrupted processing, and streamed
CSV equivalence to the existing writer.
