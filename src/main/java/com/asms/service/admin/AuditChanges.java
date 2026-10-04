package com.asms.service.admin;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Builder of the {@code changes} column of an audit row: {"field": {"from": ..., "to": ...}}. Fields whose value did
 * not change are left out, so the log only shows what really changed (API-USER-09 step 4).
 *
 * <p>Never put a password, a password hash or a token in it (BR-USER-15).
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-10-04
 * @modified 2026-10-04
 */
public final class AuditChanges {

    private final Map<String, Change> changes = new LinkedHashMap<>();

    private AuditChanges() {}

    public static AuditChanges create() {
        return new AuditChanges();
    }

    /** Records the change when {@code from} and {@code to} differ; enums are stored by name. */
    public AuditChanges field(String name, @Nullable Object from, @Nullable Object to) {
        Object fromValue = asJsonValue(from);
        Object toValue = asJsonValue(to);
        if (!Objects.equals(fromValue, toValue)) {
            changes.put(name, new Change(fromValue, toValue));
        }
        return this;
    }

    /** A created object: every non-null value, with {@code from} null */
    public AuditChanges created(String name, @Nullable Object value) {
        return field(name, null, value);
    }

    /** A deleted object: every non-null value, with {@code to} null */
    public AuditChanges deleted(String name, @Nullable Object value) {
        return field(name, value, null);
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public Map<String, Change> asMap() {
        return Collections.unmodifiableMap(changes);
    }

    @Nullable
    private static Object asJsonValue(@Nullable Object value) {
        return value instanceof Enum<?> e ? e.name() : value;
    }

    /** One changed field; both values may be null */
    public record Change(@Nullable Object from, @Nullable Object to) {}
}
