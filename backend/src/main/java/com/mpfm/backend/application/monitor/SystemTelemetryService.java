package com.mpfm.backend.application.monitor;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 系统遥测服务：采集运行时 CPU/内存/磁盘等快照，并保留短期趋势供管理端查看。
 */
@Service
public class SystemTelemetryService {
    private static final int HISTORY_LIMIT = 3600;
    private final Deque<SystemSnapshot> snapshotQueue = new ConcurrentLinkedDeque<>();
    private volatile long networkRxBaseline = Long.MIN_VALUE;
    private volatile long networkTxBaseline = Long.MIN_VALUE;

    public SystemSnapshot current() {
        SystemSnapshot snapshot = collect();
        snapshotQueue.addLast(snapshot);
        trimHistory();
        return snapshot;
    }

    public List<SystemSnapshot> history(int minutes) {
        Instant threshold = Instant.now().minusSeconds(Math.max(1, minutes) * 60L);
        return snapshotQueue.stream().filter(item -> item.timestamp().isAfter(threshold)).toList();
    }

    @Scheduled(fixedDelay = 1000)
    void collectScheduled() {
        snapshotQueue.addLast(collect());
        trimHistory();
    }

    private void trimHistory() {
        while (snapshotQueue.size() > HISTORY_LIMIT) {
            snapshotQueue.pollFirst();
        }
    }

    private SystemSnapshot collect() {
        com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        Runtime runtime = Runtime.getRuntime();
        long heapUsed = runtime.totalMemory() - runtime.freeMemory();
        long heapMax = runtime.maxMemory();
        long totalSwap = Math.max(0L, osBean.getTotalSwapSpaceSize());
        long freeSwap = Math.max(0L, osBean.getFreeSwapSpaceSize());
        long totalDisk = 0L;
        long usableDisk = 0L;
        LoadAverage loadAverage = readLoadAverage();
        NetworkBytes networkBytes = readNetworkBytes();
        long networkRxSinceStartup = networkRxBytesSinceStartup(networkBytes.rxBytes());
        long networkTxSinceStartup = networkTxBytesSinceStartup(networkBytes.txBytes());
        DiskIoBytes diskIoBytes = readDiskIoBytes();
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                totalDisk += Math.max(0L, root.getTotalSpace());
                usableDisk += Math.max(0L, root.getUsableSpace());
            }
        }
        return new SystemSnapshot(
                Instant.now(),
                Math.max(0D, osBean.getCpuLoad()),
                Math.max(0D, osBean.getProcessCpuLoad()),
                Math.max(0L, osBean.getTotalMemorySize()),
                Math.max(0L, osBean.getFreeMemorySize()),
                totalSwap,
                freeSwap,
                Math.max(0L, heapUsed),
                Math.max(0L, heapMax),
                totalDisk,
                usableDisk,
                System.getProperty("os.name", "unknown"),
                System.getProperty("os.version", "unknown"),
                System.getProperty("os.arch", "unknown"),
                Math.max(1, osBean.getAvailableProcessors()),
                readCpuModel(),
                loadAverage.load1(),
                loadAverage.load5(),
                loadAverage.load15(),
                networkBytes.rxBytes(),
                networkBytes.txBytes(),
                networkRxSinceStartup,
                networkTxSinceStartup,
                diskIoBytes.readBytes(),
                diskIoBytes.writeBytes(),
                ManagementFactory.getRuntimeMXBean().getUptime());
    }

    private long networkRxBytesSinceStartup(long currentRxBytes) {
        long baseline = networkRxBaseline;
        if (baseline == Long.MIN_VALUE) {
            synchronized (this) {
                if (networkRxBaseline == Long.MIN_VALUE) {
                    networkRxBaseline = Math.max(0L, currentRxBytes);
                }
                baseline = networkRxBaseline;
            }
        }
        return Math.max(0L, currentRxBytes - baseline);
    }

    private long networkTxBytesSinceStartup(long currentTxBytes) {
        long baseline = networkTxBaseline;
        if (baseline == Long.MIN_VALUE) {
            synchronized (this) {
                if (networkTxBaseline == Long.MIN_VALUE) {
                    networkTxBaseline = Math.max(0L, currentTxBytes);
                }
                baseline = networkTxBaseline;
            }
        }
        return Math.max(0L, currentTxBytes - baseline);
    }

    private LoadAverage readLoadAverage() {
        try {
            Path path = Path.of("/proc/loadavg");
            if (!Files.exists(path)) {
                return new LoadAverage(0D, 0D, 0D);
            }
            String line = Files.readString(path, StandardCharsets.UTF_8).trim();
            String[] parts = line.split("\\s+");
            if (parts.length < 3) {
                return new LoadAverage(0D, 0D, 0D);
            }
            return new LoadAverage(
                    parseDouble(parts[0]),
                    parseDouble(parts[1]),
                    parseDouble(parts[2]));
        } catch (Exception ignore) {
            return new LoadAverage(0D, 0D, 0D);
        }
    }

    private String readCpuModel() {
        try {
            Path path = Path.of("/proc/cpuinfo");
            if (!Files.exists(path)) {
                return "Core";
            }
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.startsWith("model name")) {
                    int idx = line.indexOf(':');
                    if (idx > -1 && idx < line.length() - 1) {
                        return line.substring(idx + 1).trim();
                    }
                }
            }
            return "Core";
        } catch (Exception ignore) {
            return "Core";
        }
    }

    private NetworkBytes readNetworkBytes() {
        try {
            Path path = Path.of("/proc/net/dev");
            if (!Files.exists(path)) {
                return new NetworkBytes(0L, 0L);
            }
            long rx = 0L;
            long tx = 0L;
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (!line.contains(":")) {
                    continue;
                }
                String[] ifaceSplit = line.trim().split(":");
                if (ifaceSplit.length != 2) {
                    continue;
                }
                String iface = ifaceSplit[0].trim();
                if (iface.isBlank() || "lo".equals(iface)) {
                    continue;
                }
                String[] cols = ifaceSplit[1].trim().split("\\s+");
                if (cols.length < 16) {
                    continue;
                }
                rx += parseLong(cols[0]);
                tx += parseLong(cols[8]);
            }
            return new NetworkBytes(Math.max(0L, rx), Math.max(0L, tx));
        } catch (Exception ignore) {
            return new NetworkBytes(0L, 0L);
        }
    }

    private DiskIoBytes readDiskIoBytes() {
        try {
            Path path = Path.of("/proc/diskstats");
            if (!Files.exists(path)) {
                return new DiskIoBytes(0L, 0L);
            }
            long reads = 0L;
            long writes = 0L;
            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            for (String line : lines) {
                String[] cols = line.trim().split("\\s+");
                if (cols.length < 14) {
                    continue;
                }
                String device = cols[2];
                if (device.startsWith("loop") || device.startsWith("ram") || device.startsWith("dm-")) {
                    continue;
                }
                long sectorsRead = parseLong(cols[5]);
                long sectorsWritten = parseLong(cols[9]);
                reads += sectorsRead * 512L;
                writes += sectorsWritten * 512L;
            }
            return new DiskIoBytes(Math.max(0L, reads), Math.max(0L, writes));
        } catch (Exception ignore) {
            return new DiskIoBytes(0L, 0L);
        }
    }

    private long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignore) {
            return 0L;
        }
    }

    private double parseDouble(String value) {
        try {
            return Double.parseDouble(value.trim());
        } catch (Exception ignore) {
            return 0D;
        }
    }

    /** 系统监控快照：同时保留原始网络累计字节与服务启动后的增量，前端可分别用于趋势和总量展示。 */
    public record SystemSnapshot(Instant timestamp,
                                 double cpuLoad,
                                 double processCpuLoad,
                                 long totalMemBytes,
                                 long freeMemBytes,
                                 long totalSwapBytes,
                                 long freeSwapBytes,
                                 long heapUsedBytes,
                                 long heapMaxBytes,
                                 long diskTotalBytes,
                                 long diskUsableBytes,
                                 String osName,
                                 String osVersion,
                                 String osArch,
                                 int cpuCores,
                                 String cpuModel,
                                 double load1,
                                 double load5,
                                 double load15,
                                 long networkRxBytes,
                                 long networkTxBytes,
                                 long networkRxBytesSinceStartup,
                                 long networkTxBytesSinceStartup,
                                 long diskReadBytes,
                                 long diskWriteBytes,
                                 long uptimeMs) {
    }

    private record LoadAverage(double load1, double load5, double load15) { }
    private record NetworkBytes(long rxBytes, long txBytes) { }
    private record DiskIoBytes(long readBytes, long writeBytes) { }
}
