package com.limitedbooking.backend.support;

import java.util.EnumMap;

/** 두 모드의 독립 컨테이너를 순차 준비하고 모든 소유 자원을 정리한다. */
public final class SchemaDatabases implements AutoCloseable {
    private final EnumMap<SchemaDatabase.Mode, SchemaDatabase> databases = new EnumMap<>(SchemaDatabase.Mode.class);

    public SchemaDatabases() {
        try {
            for (var mode : SchemaDatabase.Mode.values()) databases.put(mode, new SchemaDatabase(mode));
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    public SchemaDatabase use(SchemaDatabase.Mode mode) {
        var database = databases.get(mode);
        database.reset();
        return database;
    }

    @Override public void close() {
        RuntimeException failure = null;
        for (var mode : SchemaDatabase.Mode.values()) {
            var database = databases.get(mode);
            if (database == null) continue;
            try { database.close(); }
            catch (RuntimeException cleanup) {
                if (failure == null) failure = cleanup; else failure.addSuppressed(cleanup);
            }
        }
        if (failure != null) throw failure;
    }
}
