package org.genealogyarchive.worker;

import org.genealogyarchive.service.SystemStatus;
import org.genealogyarchive.service.SystemStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Phase 0 worker: refuses to start unless the foundation is ready (fail visibly), then keeps the
 * process alive under the service manager with a periodic heartbeat check, and stops cleanly on
 * service stop (SIGTERM on Linux, console Ctrl+C from WinSW on Windows).
 * <p>
 * It deliberately performs no jobs: the persistent PostgreSQL-backed job mechanism is Phase 1.
 */
@Component
public class WorkerProcess implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(WorkerProcess.class);

    private final SystemStatusService statusService;
    private final Duration heartbeat;
    private final CountDownLatch stopSignal = new CountDownLatch(1);
    private volatile Thread thread;

    public WorkerProcess(SystemStatusService statusService,
                         @Value("${gas.worker.heartbeat:PT60S}") Duration heartbeat) {
        this.statusService = statusService;
        this.heartbeat = heartbeat;
    }

    @Override
    public void start() {
        SystemStatus status = statusService.check();
        if (!status.ready()) {
            throw new IllegalStateException("Worker cannot start: the archive foundation is not ready ("
                    + status.schema().state() + "). " + String.join(" ", status.schema().messages()));
        }
        log.info("Worker started; schema version {}. No job processing in Phase 0.", status.schema().currentVersion());
        Thread t = new Thread(this::loop, "gas-worker");
        t.setDaemon(false); // keeps the JVM (and therefore the service) running
        thread = t;
        t.start();
    }

    private void loop() {
        try {
            while (!stopSignal.await(heartbeat.toMillis(), TimeUnit.MILLISECONDS)) {
                SystemStatus status = statusService.check();
                if (status.ready()) {
                    log.debug("Worker heartbeat: foundation ready");
                } else {
                    log.error("Worker heartbeat: foundation NOT ready ({}): {}", status.schema().state(),
                            String.join(" ", status.schema().messages()));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Worker stopped");
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
}
