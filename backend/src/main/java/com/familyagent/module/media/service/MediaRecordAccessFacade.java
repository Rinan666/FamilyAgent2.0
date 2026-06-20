package com.familyagent.module.media.service;

import com.familyagent.common.constant.MediaRecordType;

public interface MediaRecordAccessFacade {

    MediaRecordAccess requireReadable(MediaRecordType recordType, Long recordId);

    MediaRecordAccess requireWritable(MediaRecordType recordType, Long recordId);
}
