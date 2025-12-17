package com.learnium.learniumbackend.mapper;

import com.learnium.learniumbackend.entity.response.ExamModeDetails;
import com.learnium.learniumbackend.entity.response.Options;
import com.learnium.learniumbackend.entity.response.QuestionsResponse;
import com.learnium.learniumbackend.model.ExamMode;
import com.learnium.learniumbackend.model.OptionItem;
import com.learnium.learniumbackend.model.Question;
import org.mapstruct.*;
import org.mapstruct.factory.Mappers;

import java.util.List;

@Mapper(unmappedSourcePolicy = ReportingPolicy.IGNORE, nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface QuestionsMapper {

    QuestionsMapper INSTANCE = Mappers.getMapper(QuestionsMapper.class);

    @Mapping(target = "questionId", source = "questionId")
    @Mapping(target = "questionText", source = "questionText")
    @Mapping(target = "questionType", source = "questionType")
    @Mapping(target = "options", source = "options")
    QuestionsResponse toExamModeDetails(Question question);

    Options toOptionResponse(OptionItem option);

    List<QuestionsResponse> toExamModeDetailsList(List<Question> questions);
}
