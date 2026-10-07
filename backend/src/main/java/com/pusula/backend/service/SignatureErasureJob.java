package com.pusula.backend.service;

import com.pusula.backend.repository.SignatureErasureTaskRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class SignatureErasureJob {
    private static final Logger log = LoggerFactory.getLogger(SignatureErasureJob.class);
    private final SignatureErasureTaskRepository tasks;
    private final SignatureErasureService service;
    public SignatureErasureJob(SignatureErasureTaskRepository tasks, SignatureErasureService service) {
        this.tasks = tasks; this.service = service;
    }
    @Scheduled(fixedDelay = 10000)
    public void erasePendingSignatures() {
        for (var task : tasks.findTop100ByOrderByIdAsc()) {
            try { service.erase(task.getId()); }
            catch (Exception ex) {
                log.error("Account signature erasure pending; taskId={}, exceptionType={}",
                        task.getId(), ex.getClass().getSimpleName());
            }
        }
    }
}
