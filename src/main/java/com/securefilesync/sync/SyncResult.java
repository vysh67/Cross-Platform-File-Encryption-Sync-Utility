package com.securefilesync.sync;

import com.securefilesync.util.Json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SyncResult {
    private final Instant completedAt = Instant.now();
    private final List<String> failures = new ArrayList<>();
    private int encrypted;
    private int deduplicated;
    private int skipped;
    private int deleted;
    private long bytesEncrypted;

    public void recordEncrypted(long bytes) {
        encrypted++;
        bytesEncrypted += bytes;
    }

    public void recordDeduplicated() {
        deduplicated++;
    }

    public void recordSkipped() {
        skipped++;
    }

    public void recordDeleted() {
        deleted++;
    }

    public void recordFailure(String message) {
        failures.add(message);
    }

    public String summaryLine() {
        return "sync completed at " + completedAt
                + " encrypted=" + encrypted
                + " deduplicated=" + deduplicated
                + " skipped=" + skipped
                + " deleted=" + deleted
                + " failed=" + failures.size();
    }

    public String toJson() {
        return "{"
                + "\"completedAt\":\"" + completedAt + "\","
                + "\"encrypted\":" + encrypted + ","
                + "\"deduplicated\":" + deduplicated + ","
                + "\"skipped\":" + skipped + ","
                + "\"deleted\":" + deleted + ","
                + "\"failed\":" + failures.size() + ","
                + "\"bytesEncrypted\":" + bytesEncrypted + ","
                + "\"failures\":" + Json.array(failures)
                + "}";
    }
}
