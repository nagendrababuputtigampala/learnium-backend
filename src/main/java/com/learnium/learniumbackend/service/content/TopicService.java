package com.learnium.learniumbackend.service.content;

import com.learnium.learniumbackend.entity.response.master.TopicResponse;
import com.learnium.learniumbackend.exception.ValidationException;
import com.learnium.learniumbackend.mapper.content.TopicMapper;
import com.learnium.learniumbackend.repository.v1.content.TopicRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TopicService {

    private final TopicRepository topicRepository;
    private final TopicMapper topicMapper;

    @Transactional(readOnly = true)
    public List<TopicResponse> getTopics(UUID gradeId, UUID subjectId) {
        if (gradeId == null) throw new ValidationException("gradeId is required");
        if (subjectId == null) throw new ValidationException("subjectId is required");

        return topicMapper.toDtoList(
                topicRepository.findActiveTopicsByGradeAndSubject(gradeId, subjectId)
        );
    }
}