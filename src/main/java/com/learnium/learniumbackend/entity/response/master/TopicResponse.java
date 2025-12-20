package com.learnium.learniumbackend.entity.response.master;

import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class TopicResponse {
    private UUID topicId;
    private String topicName;
    private String description;

    // UI fields (optional, include if your table has them)
    private String icon;
    private String color;
    private Integer sortOrder;
}
