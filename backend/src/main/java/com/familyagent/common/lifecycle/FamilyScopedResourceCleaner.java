package com.familyagent.common.lifecycle;

/**
 * Cleans external resources owned by a family before the database rows are removed.
 */
public interface FamilyScopedResourceCleaner {

    void cleanFamilyResources(Long familyId);
}
