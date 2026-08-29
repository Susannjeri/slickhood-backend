package org.pms.silverocean.controller;

import lombok.RequiredArgsConstructor;
import org.pms.silverocean.service.architecture.events.OutboxOperationsService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/operations")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class OperationsController {
    private final OutboxOperationsService outbox;

    @GetMapping("/outbox/summary")
    public OutboxOperationsService.Summary outboxSummary() {
        return outbox.summary();
    }

    @GetMapping("/outbox/failures")
    public Page<OutboxOperationsService.Failure> outboxFailures(Pageable pageable) {
        return outbox.failures(pageable);
    }
}
