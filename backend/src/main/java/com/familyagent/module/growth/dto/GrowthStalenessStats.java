package com.familyagent.module.growth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GrowthStalenessStats {

    private Long recordId;
    private int staleVotes;
    private double stalenessWeight;
    private boolean myVoted;
}
