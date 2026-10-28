package org.genealogyarchive.worker;

import org.genealogyarchive.boot.CoreServices;
import org.genealogyarchive.identity.GlobalIds;
import org.genealogyarchive.jobs.JobHandler;
import org.genealogyarchive.jobs.JobRunner;
import org.genealogyarchive.service.SystemStatus;
import org.genealogyarchive.service.SystemStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * The background worker process: refuses to start unless the archive is ready (fail visibly), then
 * runs persistent PostgreSQL-backed jobs (BUILD-SPEC s.47), reports its presence for the status view,
 * and stops cleanly on service stop (SIGTERM on Linux, console Ctrl+C from WinSW on Windows).
 * <p>
 * Phase 1 registers no production job kinds; queued jobs of kinds no handler serves simply wait.
 */
@Component
public class WorkerProcess implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WorkerProcess.class);

    private final SystemStatusService statusService;
    private final CoreServices core;
    private final List<JobHandler> handlers;
    private final Duration heartbeat;
    private final Duration pollInterval;
    private final Duration lease;
    private final UUID workerId = GlobalIds.newId();
    private final CountDownLatch stopSignal = new CountDownLatch(1);
    private volatile Thread thread;
    private Instant startedAt;

    public WorkerProcess(SystemStatusService statusService, CoreServices core, ObjectProvider<JobHandler> handlers,
                         @Value("${gas.worker.heartbeat:PT60S}") Duration heartbeat,
                         @Value("${gas.worker.poll-interval:PT5S}") Duration pollInterval,
                         @Value("${gas.worker.job-lease:PT5M}") Duration lease) {
        this.statusService = statusService;
        this.core = core;
        this.handlers = handlers.orderedStream().toList();
        this.heartbeat = heartbeat;
        this.pollInterval = pollInterval.compareTo(heartbeat) > 0 ? heartbeat : pollInterval;
        this.lease = lease;
    }

    @Override
    public void start() {
        SystemStatus status = statusService.check();
        if (!status.ready()) {
            throw new IllegalStateException("Worker cannot start: the archive is not ready ("
                    + status.schema().state() + ", archive " + status.archive().state() + "). "
                    + String.join(" ", status.messages()));
        }
        startedAt = core.clock().instant();
        reportPresence();
        JobRunner runner = new JobRunner(core.jobRepository(), core.clock(), workerId, lease, handlers);
        log.info("Worker {} started for archive {}; schema version {}; job kinds: {}", workerId,
                status.archive().archiveId(), status.schema().currentVersion(),
                handlers.isEmpty() ? "none" : handlers.stream().map(JobHandler::kind).toList());
        Thread t = new Thread(() -> loop(runner), "gas-worker");
        t.setDaemon(false); // keeps the JVM (and therefore the service) running
        thread = t;
        t.start();
    }

    private void loop(JobRunner runner) {
        Instant nextHeartbeat = Instant.MIN;
        boolean ready = true;
        try {
            do {
                Instant now = core.clock().instant();
                if (!now.isBefore(nextHeartbeat)) {
                    nextHeartbeat = now.plus(heartbeat);
                    ready = heartbeatCheck();
                }
                if (ready) {
                    try {
                        runner.runAvailable(50);
                    } catch (RuntimeException e) {
                        log.error("Worker could not process jobs: {}", e.getMessage());
                    }
                }
            } while (!stopSignal.await(pollInterval.toMillis(), TimeUnit.MILLISECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            core.workerPresence().remove(workerId);
        } catch (RuntimeException e) {
            log.warn("Worker presence could not be removed: {}", e.getMessage());
        }
        log.info("Worker stopped");
    }

    private boolean heartbeatCheck() {
        SystemStatus status = statusService.check();
        if (status.ready()) {
            reportPresence();
            log.debug("Worker heartbeat: archive ready");
            return true;
        }
        log.error("Worker heartbeat: archive NOT ready ({}): {}", status.schema().state(),
                String.join(" ", status.messages()));
        return false;
    }

    private void reportPresence() {
        try {
            core.workerPresence().heartbeat(workerId, startedAt, core.clock().instant(),
                    SystemStatusService.applicationVersion());
        } catch (RuntimeException e) {
            log.warn("Worker presence could not be recorded: {}", e.getMessage());
        }
    }

    @Override
    public void stop() {
        stopSignal.countDown();
        Thread t = thread;
        if (t != null) {
            try {
                t.join(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        thread = null;
    }

    @Override
    public boolean isRunning() {
        Thread t = thread;
        return t != null && t.isAlive();
    }

    public UUID workerId() {
        return workerId;
    }
}
