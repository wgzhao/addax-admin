package com.wgzhao.addax.admin.log;

/**
 * Abstraction for pushing log lines to a centralized sink (Redis Stream, Kafka, etc.)
 */
public interface LogSink {
    /**
     * Push a single line to the sink. Returns the sink-specific id (e.g. Redis stream id) or null on failure.
     */
    String pushLine(String streamKey, long seq, String line);

    /**
     * Notify end of stream (optional)
     */
    void pushEnd(String streamKey, long seq, int exitCode);
}

