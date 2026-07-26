package com.familyagent.module.memory.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class RecallSourceSummary {

    private String id;
    private String sourceType;
    private String title;
    private String snippet;
    private String visibility;
    private String temporalLayer;
    private List<String> topics;
    private List<String> scenes;
    private RecallParticipantSummary author;
    private RecallParticipantSummary observer;
    private RecallParticipantSummary subject;
}
