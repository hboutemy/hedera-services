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

package com.swirlds.regression.validators;

import com.swirlds.common.PlatformLogMarker;
import com.swirlds.regression.csv.CsvReader;
import com.swirlds.regression.logs.LogEntry;
import com.swirlds.regression.logs.LogReader;

import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static com.swirlds.common.PlatformLogMessages.FINISHED_RECONNECT;
import static com.swirlds.common.PlatformLogMessages.RECV_STATE_ERROR;
import static com.swirlds.common.PlatformLogMessages.RECV_STATE_HASH_MISMATCH;
import static com.swirlds.common.PlatformLogMessages.START_RECONNECT;
import static com.swirlds.common.PlatformStatNames.TRANSACTIONS_HANDLED_PER_SECOND;
import static com.swirlds.regression.RegressionUtilities.OLD_EVENT_PARENT;

public class ReconnectValidator extends NodeValidator {

	public ReconnectValidator(List<NodeData> nodeData) {
		super(nodeData);
	}


	boolean isValidated = false;
	boolean isValid = true;

	/**
	 * Check log and csv of reconnect test results
	 * An passing test case should have following key messages in order
	 *
	 * START_RECONNECT
	 * FINISHED_RECONNECT
	 *
	 * Failed test A) multiple repetition of following messages
	 *
	 * START_RECONNECT
	 * FINISHED_RECONNECT
	 * RECV_STATE_HASH_MISMATCH
	 *
	 * START_RECONNECT
	 * FINISHED_RECONNECT
	 * RECV_STATE_HASH_MISMATCH
	 *
	 * START_RECONNECT
	 * FINISHED_RECONNECT
	 * RECV_STATE_HASH_MISMATCH
	 *
	 *
	 * Failed test B) multiple repetition of following messages
	 *
	 * START_RECONNECT
	 * RECV_STATE_ERROR
	 *
	 * START_RECONNECT
	 * RECV_STATE_ERROR
	 *
	 * START_RECONNECT
	 * RECV_STATE_ERROR
	 *
	 * @throws IOException
	 */
	@Override
	public void validate() throws IOException {
		int nodeNum = nodeData.size();

		double transHandleAverage = 0; //transH/sec
		for (int i = 0; i < nodeNum - 1; i++) {
			LogReader nodeLog = nodeData.get(i).getLogReader();
			CsvReader nodeCsv = nodeData.get(i).getCsvReader();
			boolean isNotLoaded = false;
			if (nodeLog == null) {
				log.error(MARKER, "could not load log, exiting validation for node {}", i);
				isNotLoaded = true;
			}
			if (nodeCsv == null) {
				log.error(MARKER, "could not load csv file, exiting validation for node {}", i);
				isNotLoaded = true;
			}
			if (isNotLoaded) {
				continue;
			}

			nodeLog.readFully();
			int socketExceptions = 0;
			int invalidEvent = 0;
			int unexpectedErrors = 0;
			for (LogEntry e : nodeLog.getExceptions()) {
				if (e.getMarker() == PlatformLogMarker.SOCKET_EXCEPTIONS) {
					socketExceptions++;
				} else if (e.getMarker() == PlatformLogMarker.INVALID_EVENT_ERROR) {
					invalidEvent++;
				} else {
					if ( e.getLogEntry().contains(OLD_EVENT_PARENT)) {
						addWarning(String.format("Node %d has error:[ %s ]", i, e.getLogEntry()));
					}else {
						unexpectedErrors++;
						isValid = false;
					}
				}
			}
			if (socketExceptions > 0) {
				addInfo(String.format("Node %d has %d socket exceptions. Some are expected for Reconnect.", i,
						socketExceptions));
			}
			if (invalidEvent > 0) {
				addInfo(String.format("Node %d has %d invalid event errors. Some are expected for Reconnect.", i,
						invalidEvent));
			}
			if (unexpectedErrors > 0) {
				addError(String.format("Node %d has %d unexpected errors!", i, unexpectedErrors));
			}
			if (nodeCsv != null) {
				log.info(MARKER, "nodecsv is not null");
				if (nodeCsv.getColumn(TRANSACTIONS_HANDLED_PER_SECOND) == null) {
					log.error(MARKER, "{} is returning null", TRANSACTIONS_HANDLED_PER_SECOND);
				}
			} else {
				isValid = false;
				log.error(MARKER, "nodecsv is null, that's an issue");
			}
			transHandleAverage += nodeCsv.getColumn(TRANSACTIONS_HANDLED_PER_SECOND).getAverage();
		}
		transHandleAverage /= nodeNum - 1;

		LogReader nodeLog = nodeData.get(nodeNum - 1).getLogReader();
		CsvReader nodeCsv = nodeData.get(nodeNum - 1).getCsvReader();
		boolean nodeReconnected = false;
		while (true) {
			LogEntry start = nodeLog.nextEntryContaining(Arrays.asList(START_RECONNECT, RECV_STATE_HASH_MISMATCH));
			if (start == null) {
				break;
			} else {
				if (start.getLogEntry().contains(RECV_STATE_HASH_MISMATCH)) {
					addError(String.format("Node %d hash mismatch of received hash", nodeNum - 1));
					continue; // try to find next START_RECONNECT
				}
			}
			// we have a START_RECONNECT now, try to find FINISHED_RECONNECT or RECV_STATE_ERROR

			LogEntry end = nodeLog.nextEntryContaining(Arrays.asList(FINISHED_RECONNECT, RECV_STATE_ERROR));
			if (end == null) {
				addError(String.format("Node %d started a reconnect, but did not finish!", nodeNum - 1));
				isValid = false;
				break;
			}

			if (end.getLogEntry().contains(FINISHED_RECONNECT)) {
				nodeReconnected = true;
				long time = start.getTime().until(end.getTime(), ChronoUnit.MILLIS);
				addInfo(String.format("Node %d reconnected, time taken %dms", nodeNum - 1, time));
			} else if (end.getLogEntry().contains(RECV_STATE_ERROR)) {
				addError(String.format("Node %d hash error during receiving SignedState", nodeNum - 1));
				isValid = false;
			}

		}

		if (!nodeReconnected) {
			isValid = false;
		} else {
			double transHandleLast = nodeCsv.getColumn(TRANSACTIONS_HANDLED_PER_SECOND).getLastEntryAsDouble();

			if (transHandleAverage * 0.8 > transHandleLast) {
				addError("transH/sec too low on reconnected node");
				isValid = false;
			}
		}

		isValidated = true;
	}

	@Override
	public boolean isValid() {
		return isValidated && isValid;
	}
}