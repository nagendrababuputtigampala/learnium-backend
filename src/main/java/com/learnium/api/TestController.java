package com.learnium.api;

import com.learnium.api.dto.TestDtos.*;
import com.learnium.exam.TestService;
import com.learnium.security.CurrentPrincipal;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasAnyRole('CENTRE_ADMIN', 'TEACHER')")
public class TestController {
    private final TestService service;
    public TestController(TestService service) { this.service = service; }
    @PostMapping("/tests")
    public ResponseEntity<DefinitionDto> create(@RequestBody DefinitionRequest body) {
        return ResponseEntity.status(201).body(service.create(CurrentPrincipal.require(), body));
    }
    @GetMapping("/tests")
    public List<DefinitionDto> list() { return service.list(CurrentPrincipal.require()); }
    @GetMapping("/tests/{id}")
    public DefinitionDto get(@PathVariable UUID id) { return service.get(CurrentPrincipal.require(), id); }
    @PutMapping("/tests/{id}")
    public DefinitionDto update(@PathVariable UUID id, @RequestBody DefinitionRequest body) { return service.update(CurrentPrincipal.require(), id, body); }
    @DeleteMapping("/tests/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) { service.delete(CurrentPrincipal.require(), id); return ResponseEntity.noContent().build(); }
    @PostMapping("/tests/{id}/publish")
    public ResponseEntity<InstanceDto> publish(@PathVariable UUID id, @RequestBody PublishRequest body) {
        return ResponseEntity.status(201).body(service.publish(CurrentPrincipal.require(), id, body));
    }
    @GetMapping("/instances")
    public List<InstanceDto> instances(@RequestParam(required = false) UUID batchId) { return service.instances(CurrentPrincipal.require(), batchId); }
    @GetMapping("/instances/{id}")
    public InstanceDto instance(@PathVariable UUID id) { return service.instance(CurrentPrincipal.require(), id); }
    @PostMapping("/instances/{id}/regenerate-code")
    public InstanceDto regenerate(@PathVariable UUID id) { return service.regenerate(CurrentPrincipal.require(), id); }
    @PostMapping("/instances/{id}/close")
    public InstanceDto close(@PathVariable UUID id) { return service.close(CurrentPrincipal.require(), id); }
    @PostMapping("/instances/{id}/students")
    public InstanceDto add(@PathVariable UUID id, @RequestBody StudentRequest body) { return service.addStudent(CurrentPrincipal.require(), id, body); }
}
