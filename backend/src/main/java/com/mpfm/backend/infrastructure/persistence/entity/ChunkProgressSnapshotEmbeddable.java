package com.mpfm.backend.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** 分片进度快照，记录字节进度与分片状态统计。 */
@Embeddable
public class ChunkProgressSnapshotEmbeddable {

    @Column(name = "transferred_bytes", nullable = false)
    private long transferredBytes;

    @Column(name = "total_bytes", nullable = false)
    private long totalBytes;

    @Column(name = "chunk_size_bytes", nullable = false)
    private long chunkSizeBytes;

    @Column(name = "total_chunks", nullable = false)
    private int totalChunks;

    @Column(name = "completed_chunks", nullable = false)
    private int completedChunks;

    @Column(name = "failed_chunks", nullable = false)
    private int failedChunks;

    @Column(name = "chunk_states_json", columnDefinition = "TEXT")
    private String chunkStatesJson;

    public long getTransferredBytes() { return transferredBytes; }
    public void setTransferredBytes(long transferredBytes) { this.transferredBytes = transferredBytes; }
    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
    public long getChunkSizeBytes() { return chunkSizeBytes; }
    public void setChunkSizeBytes(long chunkSizeBytes) { this.chunkSizeBytes = chunkSizeBytes; }
    public int getTotalChunks() { return totalChunks; }
    public void setTotalChunks(int totalChunks) { this.totalChunks = totalChunks; }
    public int getCompletedChunks() { return completedChunks; }
    public void setCompletedChunks(int completedChunks) { this.completedChunks = completedChunks; }
    public int getFailedChunks() { return failedChunks; }
    public void setFailedChunks(int failedChunks) { this.failedChunks = failedChunks; }
    public String getChunkStatesJson() { return chunkStatesJson; }
    public void setChunkStatesJson(String chunkStatesJson) { this.chunkStatesJson = chunkStatesJson; }
}

