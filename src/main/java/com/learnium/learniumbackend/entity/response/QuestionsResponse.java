package com.learnium.learniumbackend.entity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class QuestionsResponse {

    private Integer questionId;

    private String questionText;

    private String questionType;

    private List<Options> options;
}
