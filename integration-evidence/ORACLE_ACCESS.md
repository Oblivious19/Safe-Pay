# Direct read-only Oracle snapshots

The user authorized read-only owner snapshots and supplied the existing local connection credentials. No further connection setup is needed for this completed integration run. SQL Developer did not need to stay open.

Connection: localhost:1521/FREEPDB1, SAFEPAY_OWNER, default role. The SAFEPAY_APP password was not needed or read. No environment configuration was read.

SnapshotRunner.java used the existing JDK and Oracle JDBC jar. The owner password was passed to process stdin in memory; it was not stored in source, evidence files, process arguments or a new credentials file. The utility also supports an explicitly designated local credentials file for future user-operated runs, but this integration used --stdin.

Every connection begins with SET TRANSACTION READ ONLY, verifies SELECT USER, runs the reviewed SELECT-only snapshot block, retrieves output, rolls back and closes. The runner rejects non-local URLs, other usernames, unexpected SQL mutations and overwriting an existing snapshot.

Completed:
- baseline-before-v2.txt: 21 September 2026, 02:11:50 +05:30.
- baseline-after-v2.txt: 21 September 2026, 02:37:17 +05:30.
- Both exports validated all 19 tables, keys, counts, chunk order and exact UTF-8 byte lengths.
- No SQL DML, DDL, cleanup, migrations or grants were executed.
- Snapshot exports are ignored by Git; secret/hash/token/key/LOB values are excluded.
- All included differences are in DATA_CHANGES.md with interpretation in DATA_CHANGE_SUMMARY.md.

The earlier user-run baseline-before.txt is retained unchanged but rejected due to wrapped text. The valid V2 snapshot supersedes it. Never infer excluded secret-field equality from this comparison or overwrite these baselines for a later test run.
