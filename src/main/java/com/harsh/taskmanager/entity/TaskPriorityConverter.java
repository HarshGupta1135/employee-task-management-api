package com.harsh.taskmanager.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Persists {@link TaskPriority} as its explicit numeric level (not the enum ordinal),
 * so reordering or adding enum constants can never corrupt existing rows.
 */
@Converter
public class TaskPriorityConverter implements AttributeConverter<TaskPriority, Integer> {

    @Override
    public Integer convertToDatabaseColumn(TaskPriority priority) {
        return priority == null ? null : priority.getLevel();
    }

    @Override
    public TaskPriority convertToEntityAttribute(Integer level) {
        return level == null ? null : TaskPriority.fromLevel(level);
    }
}
