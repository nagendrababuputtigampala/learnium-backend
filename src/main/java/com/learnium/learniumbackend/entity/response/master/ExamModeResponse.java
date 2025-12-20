package com.learnium.learniumbackend.entity.response.master;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExamModeResponse{
    private UUID examModeId;

    private String testType;
    private String title;
    private String description;

    private String icon;
    private String buttonText;
    private String buttonColor;

    private String gradientFrom;
    private String gradientTo;

    private List<String> features;
    private Integer sortOrder;
}
