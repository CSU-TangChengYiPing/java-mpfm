package com.mpfm.backend.application.task;

import com.mpfm.backend.common.error.BusinessException;
import com.mpfm.backend.common.error.ErrorCode;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 任务类型注册中心：集中管理 type 到执行器与策略配置的映射。 */
@Component
public class TransferTaskRegistry {
    private final Map<String, TransferTaskTypeConfig> configs = new ConcurrentHashMap<>();

    public TransferTaskRegistry() {
        addConfig(new TransferTaskTypeConfig("upload", 2, 3, TransferTaskRegistry::noOpHandler));
        addConfig(new TransferTaskTypeConfig("batch_upload", 2, 3, TransferTaskRegistry::noOpHandler));
        addConfig(new TransferTaskTypeConfig("put_url", 2, 2, TransferTaskRegistry::noOpHandler));
        addConfig(new TransferTaskTypeConfig("thumbnail_prepare", 1, 1, TransferTaskRegistry::noOpHandler));
        addConfig(new TransferTaskTypeConfig("extension_execute", 1, 1, TransferTaskRegistry::noOpHandler));
    }

    @Autowired
    public TransferTaskRegistry(TransferTaskHandlers handlers) {
        addConfig(new TransferTaskTypeConfig("upload", 2, 3, TransferTaskRegistry::noOpHandler));
        addConfig(new TransferTaskTypeConfig("batch_upload", 2, 3, handlers::handleBatchUpload));
        addConfig(new TransferTaskTypeConfig("put_url", 2, 2, TransferTaskRegistry::noOpHandler));
        addConfig(new TransferTaskTypeConfig("thumbnail_prepare", 1, 1, TransferTaskRegistry::noOpHandler));
        addConfig(new TransferTaskTypeConfig("extension_execute", 1, 1, TransferTaskRegistry::noOpHandler));
    }

    public void register(TransferTaskTypeConfig config) {
        addConfig(config);
    }

    public TransferTaskTypeConfig require(String type) {
        TransferTaskTypeConfig config = configs.get(type);
        if (config == null) {
            throw new BusinessException(ErrorCode.CAPABILITY_NOT_SUPPORTED, "unsupported task type: " + type);
        }
        return config;
    }

    private static void noOpHandler(TransferTaskContext context) {
        context.ensureNotCanceled();
    }

    private void addConfig(TransferTaskTypeConfig config) {
        configs.put(config.type(), config);
    }
}
