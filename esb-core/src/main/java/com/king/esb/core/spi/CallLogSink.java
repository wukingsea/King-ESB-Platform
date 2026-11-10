package com.king.esb.core.spi;

/** Sink for call log records; implemented by the persistence layer in {@code esb-admin}. */
public interface CallLogSink {

    void save(CallLogRecord record);
}
