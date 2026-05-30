package com.mpfm.backend.application.task;

import com.mpfm.backend.application.transfer.TransferChunkService;
import com.mpfm.backend.application.transfer.TransferSessionStore;
import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 任务执行入口：根据任务类型分派到上传运行时执行器。 */
@Component
public class TransferTaskHandlers {
    private final TransferSessionStore sessionStore;
    private final RuntimeUploadTaskWorker uploadTaskWorker;

    public TransferTaskHandlers(TransferSessionStore sessionStore,
                                RuntimeUploadTaskWorker uploadTaskWorker) {
        this.sessionStore = sessionStore;
        this.uploadTaskWorker = uploadTaskWorker;
    }

    public void handleBatchUpload(TransferTaskContext context) throws Exception {
        TransferChunkService.UploadSession initial = loadUploadSession(context.taskId());
        uploadTaskWorker.handleBatchUpload(context, initial);
    }

    private TransferChunkService.UploadSession loadUploadSession(UUID taskId) {
        Optional<TransferChunkService.UploadSession> found = sessionStore.findUploadByTaskId(taskId);
        if (found.isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "upload session not found");
        }
        return found.get();
    }

}
