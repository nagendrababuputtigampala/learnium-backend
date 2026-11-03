package com.learnium.marking;

import com.learnium.model.content.GenerationFailureKind;
import org.slf4j.*;
import org.springframework.stereotype.Component;

/** Short claim/persistence transactions surround a model call with no open transaction. */
@Component
public class MarkingWorker {
    private static final Logger log = LoggerFactory.getLogger(MarkingWorker.class);
    private final MarkingJobService jobs;
    private final MarkingService marking;
    public MarkingWorker(MarkingJobService jobs, MarkingService marking) { this.jobs = jobs; this.marking = marking; }
    public boolean runOnce() {
        var candidate = jobs.claimNext();
        if (candidate.isEmpty()) return false;
        var claim = candidate.get(); MarkingService.Outcome outcome;
        try { outcome = marking.mark(claim.provider(), claim.model(), claim.promptVersion(), claim.input()); }
        catch (RuntimeException ex) { outcome = new MarkingService.Outcome(null, GenerationFailureKind.MODEL_ERROR, true); }
        try {
            if (outcome.marked() != null) jobs.succeed(claim, outcome.marked());
            else jobs.fail(claim, outcome.failure(), outcome.retryable());
        } catch (RuntimeException ex) {
            // Preserve the running lease after a persistence rollback; another claim can recover.
            log.warn("Marking job {} persistence failed ({})", claim.id(), ex.getClass().getSimpleName());
        }
        return true;
    }
}
