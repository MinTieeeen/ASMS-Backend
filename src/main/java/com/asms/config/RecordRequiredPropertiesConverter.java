package com.asms.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.media.Schema;
import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Marks every component of a DTO record as {@code required} in the OpenAPI spec unless it is annotated with JSpecify
 * {@link Nullable}. The frontend generates its types from the spec (Orval), so non-null fields become non-optional
 * TypeScript properties instead of {@code field?: T}.
 *
 * <p>Registered automatically by springdoc because it is a {@link ModelConverter} bean.
 *
 * @author MinhTien
 * @version 1.0.0
 * @since 2026-09-27
 * @modified 2026-09-27
 */
@Component
public class RecordRequiredPropertiesConverter implements ModelConverter {

    private static final String REF_PREFIX = "#/components/schemas/";

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public @Nullable Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema schema = chain.hasNext() ? chain.next().resolve(type, context, chain) : null;
        Class<?> raw = Json.mapper().constructType(type.getType()).getRawClass();
        if (schema == null || !raw.isRecord()) {
            return schema;
        }
        Schema target = schema.get$ref() == null
                ? schema
                : context.getDefinedModels().get(schema.get$ref().substring(REF_PREFIX.length()));
        if (target == null || target.getProperties() == null) {
            return schema;
        }
        for (RecordComponent component : raw.getRecordComponents()) {
            String name = component.getName();
            boolean alreadyRequired =
                    target.getRequired() != null && target.getRequired().contains(name);
            if (target.getProperties().containsKey(name) && !alreadyRequired && !isNullable(component)) {
                target.addRequiredItem(name);
            }
        }
        return schema;
    }

    private static boolean isNullable(RecordComponent component) {
        return component.getAnnotatedType().isAnnotationPresent(Nullable.class)
                || component.isAnnotationPresent(Nullable.class);
    }
}
