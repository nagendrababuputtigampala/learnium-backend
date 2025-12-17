package com.learnium.learniumbackend.entity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Options {
    private Integer optionId;

    private String optionText;

    private Boolean isCorrect;
}
