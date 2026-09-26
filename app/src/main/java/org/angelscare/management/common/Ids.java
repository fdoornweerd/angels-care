package org.angelscare.management.common;

import java.util.UUID;

/**
 * Primary keys are random UUIDs generated here, never database autoincrement: rows created on
 * different computers must not collide when they are synced.
 */
public final class Ids {

    private Ids() {
    }

    public static String newId() {
        return UUID.randomUUID().toString();
    }
}
