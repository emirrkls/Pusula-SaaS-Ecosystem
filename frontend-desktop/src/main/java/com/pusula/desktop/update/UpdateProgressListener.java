package com.pusula.desktop.update;

@FunctionalInterface
public interface UpdateProgressListener {
    void onProgress(long downloadedBytes, long totalBytes);
}
