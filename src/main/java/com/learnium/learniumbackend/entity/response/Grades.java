package com.learnium.learniumbackend.entity.response;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class Grades implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer gradeId;

    private String gradeName;
}
