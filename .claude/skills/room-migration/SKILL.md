---
name: room-migration
description: Add or change a Room entity/column/table in CarGenome with the matching version bump, manual migration, DI registration and exported schema. Use whenever an @Entity, its fields or indices change.
---

# Room schema change

Database: `app/src/main/kotlin/com/cargenome/app/data/db/CarGenomeDatabase.kt` (manual migrations, `exportSchema = true`).

1. Edit the entity (and DAO). New tables: add the entity to `@Database(entities = ...)` and an abstract DAO getter.
2. Bump `const val VERSION` from N to N+1.
3. Add `MIGRATION_N_N+1` in the companion, in the same style as the existing ones
   (`object : androidx.room.migration.Migration(N, N+1)`, backticked identifiers, `CREATE INDEX IF NOT EXISTS`).
   SQL must match what Room generates: column types, `NOT NULL`, defaults, FK actions, index names `index_<table>_<col>`.
4. Register it in `di/DatabaseModule.kt` → `.addMigrations(...)`.
5. Build once (`python scripts/ai/gradle.py :app:kspDebugKotlin`) so KSP exports `app/schemas/com.cargenome.app.data.db.CarGenomeDatabase/N+1.json`.
   Compare the new JSON's `createSql` for the table with your migration SQL; fix any mismatch.
6. If the entity is part of backup/restore or sync, update `data/export/DataBackupManager.kt` and `data/sync/SyncModels.kt`, plus their tests.
7. Run `:app:testDebugUnitTest --tests "*CarGenomeDatabaseTest"` and the backup tests.
8. Commit the schema JSON together with the code.

Never use destructive fallback migrations: users' data lives only on device.
