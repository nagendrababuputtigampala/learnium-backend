package com.learnium.api;

import com.learnium.admin.CatalogService;
import com.learnium.api.dto.AdminDtos.CreateTopicRequest;
import com.learnium.api.dto.AdminDtos.GradeSubjectDto;
import com.learnium.api.dto.AdminDtos.PublicBatchDto;
import com.learnium.api.dto.AdminDtos.PublicCentreDto;
import com.learnium.api.dto.AdminDtos.TopicDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Catalogues: staff master data, plus the public join-page pickers. */
@RestController
@RequestMapping("/api/v1")
public class CatalogController {

    private final CatalogService catalogService;

    public CatalogController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping("/master/grade-subjects")
    @PreAuthorize("hasAnyRole('CENTRE_ADMIN', 'TEACHER')")
    public ResponseEntity<List<GradeSubjectDto>> gradeSubjects() {
        return ResponseEntity.ok(catalogService.gradeSubjects());
    }

    @GetMapping("/master/grade-subjects/{gradeSubjectId}/topics")
    @PreAuthorize("hasAnyRole('CENTRE_ADMIN', 'TEACHER')")
    public ResponseEntity<List<TopicDto>> topics(@PathVariable UUID gradeSubjectId) {
        return ResponseEntity.ok(catalogService.topics(gradeSubjectId));
    }

    @PostMapping("/master/grade-subjects/{gradeSubjectId}/topics")
    @PreAuthorize("hasAnyRole('CENTRE_ADMIN', 'TEACHER')")
    public ResponseEntity<TopicDto> createTopic(
            @PathVariable UUID gradeSubjectId, @Valid @RequestBody CreateTopicRequest request) {
        var result = catalogService.createTopic(gradeSubjectId, request.name());
        return result.created()
                ? ResponseEntity.status(HttpStatus.CREATED).body(result.topic())
                : ResponseEntity.ok(result.topic());
    }

    @GetMapping("/public/centres")
    public ResponseEntity<List<PublicCentreDto>> publicCentres() {
        return ResponseEntity.ok(catalogService.publicCentres());
    }

    @GetMapping("/public/centres/{centreId}/batches")
    public ResponseEntity<List<PublicBatchDto>> publicBatches(@PathVariable UUID centreId) {
        return ResponseEntity.ok(catalogService.publicBatches(centreId));
    }
}
