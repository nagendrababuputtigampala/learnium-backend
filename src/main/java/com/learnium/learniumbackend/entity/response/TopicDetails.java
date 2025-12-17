package com.learnium.learniumbackend.entity.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class TopicDetails {

    private Integer topicId;
    private String topicName;
}
