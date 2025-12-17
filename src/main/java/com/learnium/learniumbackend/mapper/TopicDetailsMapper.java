package com.learnium.learniumbackend.mapper;

import com.learnium.learniumbackend.entity.response.TopicDetails;
import com.learnium.learniumbackend.model.Topic;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(unmappedSourcePolicy = ReportingPolicy.IGNORE, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface TopicDetailsMapper {

    TopicDetailsMapper INSTANCE = Mappers.getMapper(TopicDetailsMapper.class);

    @Mapping(target = "topicId", source = "topicId")
    @Mapping(target = "topicName", source = "topicName")
    TopicDetails toTopicResponse(Topic topic);

    List<TopicDetails> toTopicResponseList(List<Topic> topics);
}
