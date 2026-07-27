package com.securefilesync.restore;

import com.securefilesync.util.Json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class RestoreResult {
    private final Instant completedAt = Instant.now();
    private final List<String> failures = new ArrayList<>();
    private int restored;

    public void recordRestored() {
        restored++;
    }

    public void recordFailure(String message) {
        failures.add(message);
    }

    public String toJson() {
        return "{"
                + "\"completedAt\":\"" + completedAt + "\","
                + "\"restored\":" + restored + ","
                + "\"failed\":" + failures.size() + ","
                + "\"failures\":" + Json.array(failures)
                + "}";
    }
}
