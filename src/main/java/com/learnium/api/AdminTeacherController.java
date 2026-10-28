package com.learnium.api;

import com.learnium.admin.TeacherAdminService;
import com.learnium.api.dto.AuthDtos.CreateTeacherRequest;
import com.learnium.api.dto.AuthDtos.ResendInviteDto;
import com.learnium.api.dto.AuthDtos.TeacherDto;
import com.learnium.common.api.PageResponse;
import com.learnium.security.CurrentPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Centre-admin teacher management (invite, list, deactivate). */
@RestController
@RequestMapping("/api/v1/admin/teachers")
@PreAuthorize("hasRole('CENTRE_ADMIN')")
public class AdminTeacherController {

    private final TeacherAdminService teacherAdminService;

    public AdminTeacherController(TeacherAdminService teacherAdminService) {
        this.teacherAdminService = teacherAdminService;
    }

    @PostMapping
    public ResponseEntity<TeacherDto> createTeacher(@Valid @RequestBody CreateTeacherRequest request) {
        TeacherDto created = teacherAdminService.createTeacher(CurrentPrincipal.require(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public ResponseEntity<PageResponse<TeacherDto>> listTeachers(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(
                teacherAdminService.listTeachers(CurrentPrincipal.require(), PageRequest.of(page, size)));
    }

    @PostMapping("/{userId}/resend-invite")
    public ResponseEntity<ResendInviteDto> resendInvite(@PathVariable UUID userId) {
        return ResponseEntity.ok(
                teacherAdminService.resendInvite(CurrentPrincipal.require(), userId));
    }

    @DeleteMapping("/{userId}")
    public ResponseEntity<Void> deactivateTeacher(@PathVariable UUID userId) {
        teacherAdminService.deactivateTeacher(CurrentPrincipal.require(), userId);
        return ResponseEntity.noContent().build();
    }
}
