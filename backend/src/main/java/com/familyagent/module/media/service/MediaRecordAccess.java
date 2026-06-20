package com.familyagent.module.media.service;

import com.familyagent.common.constant.MediaRecordType;

public record MediaRecordAccess(
        MediaRecordType recordType,
        Long recordId,
        Long familyId) {
}
