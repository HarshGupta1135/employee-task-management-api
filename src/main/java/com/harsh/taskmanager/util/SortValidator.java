package com.harsh.taskmanager.util;

import com.harsh.taskmanager.exception.BadRequestException;
import org.springframework.data.domain.Sort;

import java.util.Set;
import java.util.TreeSet;

/**
 * Rejects sort properties that are not explicitly allowed, so clients get a clear 400 instead of a
 * persistence error (and cannot sort by internal fields such as passwords).
 */
public final class SortValidator {

    private SortValidator() {
    }

    public static void requireAllowed(Sort sort, Set<String> allowedProperties) {
        for (Sort.Order order : sort) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new BadRequestException("Cannot sort by '" + order.getProperty()
                        + "'. Allowed sort properties: " + new TreeSet<>(allowedProperties));
            }
        }
    }
}
