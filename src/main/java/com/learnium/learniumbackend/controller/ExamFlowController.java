package com.learnium.learniumbackend.controller;

import com.learnium.learniumbackend.entity.response.*;
import com.learnium.learniumbackend.service.ExamFlowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/exam")
@Tag(name = "Exam Flow", description = "APIs for navigating grades, subjects, topics, exams, and questions")
public class ExamFlowController {

    private static final Logger logger = LoggerFactory.getLogger(ExamFlowController.class);
    private final ExamFlowService examFlowService;

    public ExamFlowController(ExamFlowService examFlowService) {
        this.examFlowService = examFlowService;
    }

    @GetMapping("/getGrades")
    @Operation(summary = "Get all grades")
    public ResponseEntity<List<Grades>> getGrades(){
        List<Grades> grades = examFlowService.getGrades();
        return ResponseEntity.ok(grades);

    }


    @Operation(summary = "Get subjects by grade")
    @Parameters({
            @Parameter(name = "gradeId", description = "ID of the grade", required = true)
    })
    @GetMapping("/getSubjectsByGrade/{gradeId}")
    public ResponseEntity<List<SubjectResponse>> getSubjectsByGrade(@PathVariable("gradeId") Integer gradeId){
        List<SubjectResponse> subjects = examFlowService.getSubjectsByGrade(gradeId);
        return ResponseEntity.ok(subjects);
    }

    @Operation(summary = "Get topics by grade and subject")
    @Parameters({
            @Parameter(name = "gradeId", description = "ID of the grade", required = true),
            @Parameter(name = "subjectId", description = "ID of the subject", required = true)
    })
    @GetMapping("/getTopicByGradeSubject/{gradeId}/{subjectId}")
    public ResponseEntity<List<TopicDetails>> getTopicsByGradeAndSubject(
            @PathVariable("gradeId") Integer gradeId,
            @PathVariable("subjectId") Integer subjectId){
        List<TopicDetails> topicDetails = examFlowService.getTopicsByGradeAndSubject(gradeId, subjectId);
        return ResponseEntity.ok(topicDetails);
    }

    @Operation(summary = "Get exam modes for a topic")
    @Parameters({
            @Parameter(name = "gradeId", description = "ID of the grade", required = true),
            @Parameter(name = "subjectId", description = "ID of the subject", required = true),
            @Parameter(name = "topicId", description = "ID of the topic", required = true)
    })
    @GetMapping("/grades/{gradeId}/subjects/{subjectId}/topics/{topicId}/exam-modes")
    public ResponseEntity<List<ExamModeDetails>> getExamModes(
            @PathVariable("gradeId") Integer gradeId,
            @PathVariable("subjectId") Integer subjectId,
            @PathVariable("topicId") Integer topicId){
        List<ExamModeDetails> examModeDetails = examFlowService.getExamModes(gradeId, subjectId, topicId);
        return ResponseEntity.ok(examModeDetails);
    }

    @Operation(summary = "Get exams by grade, subject, topic, and exam mode")
    @Parameters({
            @Parameter(name = "gradeId", description = "ID of the grade", required = true),
            @Parameter(name = "subjectId", description = "ID of the subject", required = true),
            @Parameter(name = "topicId", description = "ID of the topic", required = true),
            @Parameter(name = "examModeId", description = "ID of the exam mode", required = true)
    })
    @GetMapping("/grades/{gradeId}/subjects/{subjectId}/topics/{topicId}/exam-modes/{examModeId}/exams")
    public ResponseEntity<List<ExamListResponse>> getExams(
            @PathVariable("gradeId") Integer gradeId,
            @PathVariable("subjectId") Integer subjectId,
            @PathVariable("topicId") Integer topicId,
            @PathVariable("examModeId") Integer examModeId){
        List<ExamListResponse> examListResponses = examFlowService.getExams(gradeId, subjectId, topicId,examModeId);
        return ResponseEntity.ok(examListResponses);
    }


    @Operation(summary = "Get questions with options for a specific exam")
    @Parameters({
            @Parameter(name = "gradeId", description = "ID of the grade", required = true),
            @Parameter(name = "subjectId", description = "ID of the subject", required = true),
            @Parameter(name = "topicId", description = "ID of the topic", required = true),
            @Parameter(name = "examModeId", description = "ID of the exam mode", required = true),
            @Parameter(name = "examId", description = "ID of the exam", required = true)
    })
    @GetMapping("/grades/{gradeId}/subjects/{subjectId}/topics/{topicId}/exam-modes/{examModeId}/exams/{examId}/questions")
    public ResponseEntity<List<QuestionsResponse>> getQuestionsWithOptions(
            @PathVariable("gradeId") Integer gradeId,
            @PathVariable("subjectId") Integer subjectId,
            @PathVariable("topicId") Integer topicId,
            @PathVariable("examModeId") Integer examModeId,
            @PathVariable("examId") Integer examId){
        List<QuestionsResponse> questionsResponses = examFlowService.getQuestionsWithOptions(gradeId, subjectId, topicId,examModeId,examId);
        return ResponseEntity.ok(questionsResponses);
    }

}
