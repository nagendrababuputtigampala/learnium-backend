package com.learnium.learniumbackend.mapper.content;

import com.learnium.learniumbackend.entity.response.master.TopicResponse;
import com.learnium.learniumbackend.model.v1.content.Topic;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface TopicMapper {
    TopicResponse toDto(Topic topic);
    List<TopicResponse> toDtoList(List<Topic> topics);
}