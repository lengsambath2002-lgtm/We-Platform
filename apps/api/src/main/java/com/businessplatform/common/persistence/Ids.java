package com.businessplatform.common.persistence;

import java.util.UUID;

/** New ids (random UUIDs, created by the application so they are known before saving). */
public final class Ids {

    private Ids() {
    }

    public static UUID next() {
        return UUID.randomUUID();
    }
}
