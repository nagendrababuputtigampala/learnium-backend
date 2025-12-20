package com.learnium.learniumbackend.entity.response.master;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GradeResponse {
    private UUID gradeId;
    private String code;   // e.g. G1, G2...
    private String name;   // e.g. Grade 1
    private Integer sortOrder;
}
