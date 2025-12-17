package com.learnium.learniumbackend.service;

import com.learnium.learniumbackend.entity.response.*;
import com.learnium.learniumbackend.mapper.*;
import com.learnium.learniumbackend.model.*;
import com.learnium.learniumbackend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExamFlowService {
    private static final Logger logger = LoggerFactory.getLogger(ExamFlowService.class);
    private final GradesRepository gradesRepository;
    private final GradeMapper gradeMapper;
    private final SubjectRepository subjectRepository;
    private final SubjectMapper subjectMapper;
    private final TopicRepository topicRepository;
    private final TopicDetailsMapper topicDetailsMapper;
    private final ExamModeRepository examModeRepository;
    private final ExamListRepository examListRepository;

    @Autowired
    private Questionsepository questionsepository;


    public ExamFlowService(GradesRepository gradesRepository, GradeMapper gradeMapper, SubjectRepository subjectRepository,
                           SubjectMapper subjectMapper, TopicRepository topicRepository, TopicDetailsMapper topicDetailsMapper,
                           ExamModeRepository examModeRepository, ExamListRepository examListRepository) {
        this.gradesRepository = gradesRepository;
        this.gradeMapper = gradeMapper;
        this.subjectRepository = subjectRepository;
        this.subjectMapper = subjectMapper;
        this.topicRepository = topicRepository;
        this.topicDetailsMapper = topicDetailsMapper;
        this.examModeRepository = examModeRepository;
        this.examListRepository = examListRepository;
    }

    public List<Grades> getGrades() {
        List<Grade> gradeEntities = gradesRepository.findAll();
        return gradeMapper.toResponseList(gradeEntities);

    }

    public List<SubjectResponse> getSubjectsByGrade(Integer gradeId) {
        List<Subject> subjects = subjectRepository.getSubjectsGradeId(gradeId);
        if (subjects != null && !subjects.isEmpty()) {
            return subjectMapper.toSubjectResponseList(subjects);
        }
        return null;
    }

    public List<TopicDetails> getTopicsByGradeAndSubject(Integer gradeId, Integer subjectId) {
        List<Topic> topics = topicRepository.findTopicsByGradeAndSubject(gradeId, subjectId);
        return topicDetailsMapper.toTopicResponseList(topics);
    }

    public List<ExamModeDetails> getExamModes(Integer gradeId, Integer subjectId, Integer topicId) {
        List<ExamMode> examModes = examModeRepository.findExamModesByGradeSubjectTopic(gradeId, subjectId, topicId);
        return ExamModeMapper.INSTANCE.toExamModeDetailsList(examModes);
    }

    public List<ExamListResponse> getExams(Integer gradeId, Integer subjectId, Integer topicId, Integer examModeId) {
        List<Exam> exams = examListRepository.findExamsByFullFilter(gradeId, subjectId, topicId, examModeId);
        return ExamListMapper.INSTANCE.toExamList(exams);
    }

    public List<QuestionsResponse> getQuestionsWithOptions(Integer gradeId, Integer subjectId, Integer topicId, Integer examModeId, Integer examId) {
        List<Question> questions = questionsepository.findQuestionsWithOptions(gradeId, subjectId, topicId, examModeId, examId);
        return QuestionsMapper.INSTANCE.toExamModeDetailsList(questions);
    }
}
