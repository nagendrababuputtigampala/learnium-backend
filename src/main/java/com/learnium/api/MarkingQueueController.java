package com.learnium.api;

import com.learnium.api.dto.MarkingQueueDtos.*;
import com.learnium.marking.MarkingQueueService;
import com.learnium.security.CurrentPrincipal;
import java.util.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/marking-queue")
@PreAuthorize("hasAnyRole('CENTRE_ADMIN', 'TEACHER')")
public class MarkingQueueController {
    private final MarkingQueueService service;
    public MarkingQueueController(MarkingQueueService service) { this.service = service; }
    @GetMapping public List<QueueGroupDto> queue() { return service.queue(CurrentPrincipal.require()); }
    @GetMapping("/{id}") public MarkingDetailDto detail(@PathVariable UUID id) { return service.detail(CurrentPrincipal.require(), id); }
    @PostMapping("/{id}/confirm") public MarkingDetailDto confirm(@PathVariable UUID id, @RequestBody ConfirmRequest request) {
        return service.confirm(CurrentPrincipal.require(), id, request);
    }
    @PostMapping("/{id}/rerun") public MarkingDetailDto rerun(@PathVariable UUID id) { return service.rerun(CurrentPrincipal.require(), id); }
}
