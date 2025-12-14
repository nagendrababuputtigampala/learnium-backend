package com.learnium.learniumbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSummaryResponse {
    private String id;
    private String name;
    private String email;
    private int grade;
    private String avatar;
    private int level;
    private int xp;
    private int totalXp;
    private int percentile;
    private List<String> badges;
}

