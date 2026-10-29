package com.example.medical;

import com.example.medical.common.config.AesAttributeConverter;
import com.example.medical.common.job.KeyRotationService;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round 57.2 review: every column encrypted through {@link AesAttributeConverter} must be in the key
 * rotation list. A column that is missing keeps working until the old key is removed and then stops
 * being readable, which is the kind of failure a rotation "completed" message hides.
 */
class EncryptedColumnCoverageTest {

    @Test
    void everyConverterEncryptedColumnIsRotated() throws Exception {
        Set<String> encrypted = encryptedColumns();
        Set<String> rotated = KeyRotationService.encryptedColumnNames();

        Set<String> missing = new LinkedHashSet<>(encrypted);
        missing.removeAll(rotated);

        assertTrue(missing.isEmpty(),
                "these converter-encrypted columns are absent from the rotation list: " + missing);
    }

    private Set<String> encryptedColumns() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false) {
                    @Override
                    protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                        return definition.getMetadata().isIndependent();
                    }
                };
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        Set<String> columns = new LinkedHashSet<>();
        for (var candidate : scanner.findCandidateComponents("com.example.medical")) {
            Class<?> entity = Class.forName(candidate.getBeanClassName());
            Table table = entity.getAnnotation(Table.class);
            String tableName = table == null || table.name().isEmpty()
                    ? entity.getSimpleName().toLowerCase() : table.name();
            for (Field field : entity.getDeclaredFields()) {
                Convert convert = field.getAnnotation(Convert.class);
                if (convert == null || convert.converter() != AesAttributeConverter.class) continue;
                Column column = field.getAnnotation(Column.class);
                columns.add(tableName + "." + (column == null || column.name().isEmpty()
                        ? snake(field.getName()) : column.name()));
            }
        }
        assertTrue(columns.size() > 30, "the scan must actually find the encrypted fields: " + columns);
        return columns;
    }

    private static String snake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
