package com.familyagent.common.lifecycle;

/**
 * Cleans resources owned by a persona member before the persona row is removed.
 */
public interface PersonaScopedResourceCleaner {

    void cleanPersonaResources(Long familyId, Long personaId);
}
