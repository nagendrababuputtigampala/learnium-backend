package com.learnium.learniumbackend.entity.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ExamFlowRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private String grade;
    private String subject;
    private String topic;
    private String examType;
    private String testId;
}
