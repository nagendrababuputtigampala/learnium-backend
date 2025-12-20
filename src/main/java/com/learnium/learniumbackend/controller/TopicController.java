package com.learnium.learniumbackend.controller;

import com.learnium.learniumbackend.entity.response.master.TopicResponse;
import com.learnium.learniumbackend.service.content.TopicService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    @GetMapping("/topics")
    public ResponseEntity<List<TopicResponse>> getTopics(
            @RequestParam UUID gradeCode,
            @RequestParam UUID subjectId
    ) {
        return ResponseEntity.ok(topicService.getTopics(gradeCode, subjectId));
    }
}