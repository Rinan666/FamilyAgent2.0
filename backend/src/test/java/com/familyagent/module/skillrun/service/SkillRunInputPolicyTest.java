package com.familyagent.module.skillrun.service;

import com.familyagent.common.constant.SkillRunStatus;
import com.familyagent.common.exception.BusinessException;
import com.familyagent.common.response.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkillRunInputPolicyTest {

    private final SkillRunInputPolicy inputPolicy = new SkillRunInputPolicy();

    @Test
    void normalizesStableSkillRunFields() {
        assertEquals("save_memory", inputPolicy.normalizeSkillName(" Save_Memory "));
        assertEquals("RUNNING", inputPolicy.normalizeStatus("running", SkillRunStatus.PLANNED));
        assertEquals("PLANNED", inputPolicy.normalizeStatus("", SkillRunStatus.PLANNED));
        assertEquals(30, inputPolicy.normalizeLimit(0));
        assertEquals(100, inputPolicy.normalizeLimit(500));
        assertEquals("FAMILY_AGENT", inputPolicy.normalizeSource(""));
    }

    @Test
    void rejectsUnsupportedStatus() {
        BusinessException error = assertThrows(
                BusinessException.class,
                () -> inputPolicy.normalizeStatus("DONE", SkillRunStatus.PLANNED));

        assertEquals(ErrorCode.BAD_REQUEST.getCode(), error.getCode());
    }
}
