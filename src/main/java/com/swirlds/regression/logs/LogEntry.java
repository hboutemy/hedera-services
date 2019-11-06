/*
 * (c) 2016-2019 Swirlds, Inc.
 *
 * This software is the confidential and proprietary information of
 * Swirlds, Inc. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Swirlds.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

package com.swirlds.regression.logs;

import com.swirlds.common.PlatformLogMarker;

import java.time.Instant;

public class LogEntry {
	private Instant time;
	private PlatformLogMarker marker;
	private long threadId;
	private String threadName;
	private String logEntry;
	private boolean isException;

	public LogEntry(Instant time, PlatformLogMarker marker, long threadId, String threadName, String logEntry,
			boolean isException) {
		this.time = time;
		this.marker = marker;
		this.threadId = threadId;
		this.threadName = threadName;
		this.logEntry = logEntry;
		this.isException = isException;
	}

	public Instant getTime() {
		return time;
	}

	public PlatformLogMarker getMarker() {
		return marker;
	}

	public long getThreadId() {
		return threadId;
	}

	public String getThreadName() {
		return threadName;
	}

	public String getLogEntry() {
		return logEntry;
	}

	public boolean isException() {
		return isException;
	}

	@Override
	public String toString() {
		return "LogEntry{" +
				"time=" + time +
				", marker=" + marker +
				", threadId=" + threadId +
				", threadName='" + threadName + '\'' +
				", logEntry='" + logEntry + '\'' +
				", isException=" + isException +
				'}';
	}
}