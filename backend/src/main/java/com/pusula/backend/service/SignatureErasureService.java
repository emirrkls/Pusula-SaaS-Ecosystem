package com.pusula.backend.service;

import com.pusula.backend.repository.SignatureErasureTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.io.IOException;

@Service
public class SignatureErasureService {
    private final SignatureErasureTaskRepository tasks;
    private final FileUploadService files;
    public SignatureErasureService(SignatureErasureTaskRepository tasks, FileUploadService files) {
        this.tasks = tasks; this.files = files;
    }
    @Transactional(rollbackFor = IOException.class)
    public void erase(Long id) throws IOException {
        var task = tasks.lockById(id);
        if (task.isEmpty()) return;
        files.deleteUserSignature(task.get().getUserId(), task.get().getFilePath());
        tasks.delete(task.get());
    }
}
