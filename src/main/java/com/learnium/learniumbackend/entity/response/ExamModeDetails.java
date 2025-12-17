package com.learnium.learniumbackend.entity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ExamModeDetails {

    private Integer examModeId;
    private String modeName;
}
