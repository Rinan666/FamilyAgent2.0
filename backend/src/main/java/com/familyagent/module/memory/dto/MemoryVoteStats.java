package com.familyagent.module.memory.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryVoteStats {

    private Long memoryId;
    private int upVotes;
    private int downVotes;
    private int voteScore;
    private double consensusWeight;
    private String myVote;
}
