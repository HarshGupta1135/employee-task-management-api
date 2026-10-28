package com.harsh.taskmanager.entity;

import java.util.Arrays;

/**
 * Task priority. Each constant has a stable numeric level that is what gets persisted,
 * so sorting by priority in SQL follows LOW &lt; MEDIUM &lt; HIGH &lt; CRITICAL instead of alphabetical order.
 */
public enum TaskPriority {
    LOW(1),
    MEDIUM(2),
    HIGH(3),
    CRITICAL(4);

    private final int level;

    TaskPriority(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public static TaskPriority fromLevel(int level) {
        return Arrays.stream(values())
                .filter(priority -> priority.level == level)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown task priority level: " + level));
    }
}
