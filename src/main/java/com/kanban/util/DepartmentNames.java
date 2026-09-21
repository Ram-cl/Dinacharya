package com.kanban.util;

import java.util.Map;

public final class DepartmentNames {

    private static final Map<String, String> CANONICAL = Map.ofEntries(
            Map.entry("ase", "ASE"),
            Map.entry("business development", "Business Development"),
            Map.entry("bde", "Business Development"),
            Map.entry("bd", "Business Development"),
            Map.entry("cybersecurity", "Cybersecurity"),
            Map.entry("devops", "DevOps"),
            Map.entry("dev", "Dev"),
            Map.entry("engineering", "Engineering"),
            Map.entry("ui", "UI"),
            Map.entry("uiux", "UI"),
            Map.entry("ui/ux", "UI"),
            Map.entry("ui ux", "UI")
    );

    private DepartmentNames() {
    }

    public static String canonical(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        String mapped = CANONICAL.get(trimmed.toLowerCase());
        return mapped != null ? mapped : trimmed;
    }
}
