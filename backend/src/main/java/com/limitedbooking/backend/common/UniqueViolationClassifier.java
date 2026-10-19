package com.limitedbooking.backend.common;

import org.postgresql.util.PSQLException;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;

public final class UniqueViolationClassifier {
    private UniqueViolationClassifier() {}

    public static boolean isReservationUserKeyViolation(Throwable failure) {
        if (failure == null) return false;
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var pending = new ArrayDeque<Throwable>();
        pending.add(failure);
        while (!pending.isEmpty()) {
            var current = pending.removeFirst();
            if (!seen.add(current)) continue;
            if (current instanceof PSQLException pg && "23505".equals(pg.getSQLState())
                    && pg.getServerErrorMessage() != null
                    && "uq_reservations_user_key".equals(pg.getServerErrorMessage().getConstraint())) return true;
            if (current.getCause() != null) pending.addLast(current.getCause());
            if (current instanceof SQLException sql && sql.getNextException() != null) pending.addLast(sql.getNextException());
        }
        return false;
    }
}
