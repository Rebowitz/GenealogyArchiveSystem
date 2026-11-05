package org.genealogyarchive.worker;

import org.genealogyarchive.jobs.JobExecution;
import org.genealogyarchive.jobs.JobHandler;
import org.genealogyarchive.preservation.PreservationContext;
import org.genealogyarchive.preservation.PreservationServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.UUID;

/**
 * Phase 2 production job kinds. Each job only carries an ID; the persistent file operation, scan or
 * File record is the source of truth, so a replayed job converges (TX-003). Handlers check the job
 * lease before consequential steps (GAS-P1-001).
 */
@Configuration(proxyBeanMethods = false)
public class PreservationJobs {

    private static JobHandler handler(String kind, Body body) {
        return new JobHandler() {
            @Override
            public String kind() {
                return kind;
            }

            @Override
            public void run(JobExecution execution) throws Exception {
                execution.requireLease();
                body.run(PreservationContext.idFromPayload(execution.job().payloadJson()), execution);
            }
        };
    }

    @FunctionalInterface
    interface Body {
        void run(UUID id, JobExecution execution) throws Exception;
    }

    @Bean
    JobHandler fileIngestHandler(PreservationServices p) {
        return handler(PreservationContext.JOB_INGEST, (id, e) -> p.processor().process(id, e::requireLease));
    }

    @Bean
    JobHandler fileRepairHandler(PreservationServices p) {
        return handler(PreservationContext.JOB_REPAIR, (id, e) -> p.processor().process(id, e::requireLease));
    }

    @Bean
    JobHandler fileDestroyHandler(PreservationServices p) {
        return handler(PreservationContext.JOB_DESTROY, (id, e) -> p.destruction().process(id));
    }

    @Bean
    JobHandler fileThumbnailHandler(PreservationServices p) {
        return handler(PreservationContext.JOB_THUMBNAIL, (id, e) -> p.thumbnails().generate(id));
    }

    @Bean
    JobHandler integrityScanHandler(PreservationServices p) {
        return handler(PreservationContext.JOB_INTEGRITY_SCAN, (id, e) -> p.integrity().run(id));
    }
}
