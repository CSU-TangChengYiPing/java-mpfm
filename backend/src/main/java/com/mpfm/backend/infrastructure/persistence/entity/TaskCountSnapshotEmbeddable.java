package com.mpfm.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** 任务项统计快照，记录总量、成功、失败、运行中数量与子项结果 JSON。 */
@Embeddable
public class TaskCountSnapshotEmbeddable {

    @Column(name = "total_count", nullable = false)
    private int totalCount;

    @Column(name = "success_count", nullable = false)
    private int successCount;

    @Column(name = "failed_count", nullable = false)
    private int failedCount;

    @Column(name = "running_count", nullable = false)
    private int runningCount;

    @Column(name = "item_results_json", columnDefinition = "TEXT")
    private String itemResultsJson;

    public int getTotalCount() { return totalCount; }
    public void setTotalCount(int totalCount) { this.totalCount = totalCount; }
    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public int getRunningCount() { return runningCount; }
    public void setRunningCount(int runningCount) { this.runningCount = runningCount; }
    public String getItemResultsJson() { return itemResultsJson; }
    public void setItemResultsJson(String itemResultsJson) { this.itemResultsJson = itemResultsJson; }
}

