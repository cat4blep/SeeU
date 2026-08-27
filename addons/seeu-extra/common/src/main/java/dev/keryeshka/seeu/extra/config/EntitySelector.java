package dev.keryeshka.seeu.extra.config;

import dev.keryeshka.seeu.extra.ResourceIdentifier;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public final class EntitySelector {
    private final SelectionMode mode;
    private final Set<String> types;
    private final Set<String> namespaces;
    private final Set<String> excludedTypes;
    private final Set<String> excludedNamespaces;

    public EntitySelector(
            SelectionMode mode,
            Collection<String> types,
            Collection<String> namespaces,
            Collection<String> excludedTypes,
            Collection<String> excludedNamespaces
    ) {
        this.mode = mode == null ? SelectionMode.DISABLED : mode;
        this.types = normalizeTypeIds(types);
        this.namespaces = normalizeNamespaces(namespaces);
        this.excludedTypes = normalizeTypeIds(excludedTypes);
        this.excludedNamespaces = normalizeNamespaces(excludedNamespaces);
    }

    public boolean selects(String typeId) {
        if (mode == SelectionMode.DISABLED || !ResourceIdentifier.isValid(typeId)) {
            return false;
        }

        String namespace = ResourceIdentifier.namespace(typeId);
        if (excludedTypes.contains(typeId) || excludedNamespaces.contains(namespace)) {
            return false;
        }
        return mode == SelectionMode.ALL || types.contains(typeId) || namespaces.contains(namespace);
    }

    public SelectionMode mode() {
        return mode;
    }

    public Set<String> types() {
        return types;
    }

    public Set<String> namespaces() {
        return namespaces;
    }

    public Set<String> excludedTypes() {
        return excludedTypes;
    }

    public Set<String> excludedNamespaces() {
        return excludedNamespaces;
    }

    static Set<String> normalizeTypeIds(Collection<String> values) {
        return normalize(values, ResourceIdentifier::isValid);
    }

    static Set<String> normalizeNamespaces(Collection<String> values) {
        return normalize(values, ResourceIdentifier::isValidNamespace);
    }

    private static Set<String> normalize(Collection<String> values, Validator validator) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }

        TreeSet<String> normalized = new TreeSet<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String candidate = value.trim();
            if (validator.accepts(candidate)) {
                normalized.add(candidate);
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    @FunctionalInterface
    private interface Validator {
        boolean accepts(String value);
    }
}
