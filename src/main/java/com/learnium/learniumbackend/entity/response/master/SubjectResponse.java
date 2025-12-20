package com.learnium.learniumbackend.entity.response.master;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubjectResponse {
    private UUID subjectId;
    private String subjectName;
    private String icon;
    private String color;
    private String description;

    // optional (if you store these in subjects)
    private String gradientFrom;
    private String gradientTo;
}
