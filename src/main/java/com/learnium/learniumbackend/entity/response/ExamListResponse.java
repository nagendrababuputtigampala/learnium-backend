package com.learnium.learniumbackend.entity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ExamListResponse {

    private Integer examId;

    private String examName;

    private Integer totalQuestions;

    private String level;

    private Integer duration;
}
