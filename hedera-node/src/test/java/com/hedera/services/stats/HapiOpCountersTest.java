package com.hedera.services.stats;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.swirlds.common.Platform;
import com.swirlds.common.StatEntry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.mock;

@RunWith(JUnitPlatform.class)
class HapiOpCountersTest {
	Platform platform;
	CounterFactory factory;
	Function<HederaFunctionality, String> statNameFn;

	HapiOpCounters subject;

	@BeforeEach
	public void setup() throws Exception {
		HapiOpCounters.allFunctions = () -> new HederaFunctionality[] {
				CryptoTransfer,
				TokenGetInfo
		};

		platform = mock(Platform.class);
		factory = mock(CounterFactory.class);
		statNameFn = HederaFunctionality::toString;

		subject = new HapiOpCounters(factory, statNameFn);
	}

	@AfterEach
	public void cleanup() {
		HapiOpCounters.allFunctions = HederaFunctionality.class::getEnumConstants;
	}

	@Test
	public void beginsRationally() {
		// expect:
		assertTrue(subject.receivedOps.containsKey(CryptoTransfer));
		assertTrue(subject.submittedTxns.containsKey(CryptoTransfer));
		assertTrue(subject.handledTxns.containsKey(CryptoTransfer));
		assertFalse(subject.answeredQueries.containsKey(CryptoTransfer));
		// and:
		assertTrue(subject.receivedOps.containsKey(TokenGetInfo));
		assertTrue(subject.answeredQueries.containsKey(TokenGetInfo));
		assertFalse(subject.submittedTxns.containsKey(TokenGetInfo));
		assertFalse(subject.handledTxns.containsKey(TokenGetInfo));
	}

	@Test
	public void registersExpectedStatEntries() {
		// setup:
		StatEntry transferRcv = mock(StatEntry.class);
		StatEntry transferSub = mock(StatEntry.class);
		StatEntry transferHdl = mock(StatEntry.class);
		StatEntry tokenInfoRcv = mock(StatEntry.class);
		StatEntry tokenInfoAns = mock(StatEntry.class);

		given(factory.from(
				argThat("CryptoTransferRcv"::equals),
				argThat("number of CryptoTransfer received"::equals),
				any())).willReturn(transferRcv);
		given(factory.from(
				argThat("CryptoTransferSub"::equals),
				argThat("number of CryptoTransfer submitted"::equals),
				any())).willReturn(transferSub);
		given(factory.from(
				argThat("CryptoTransferHdl"::equals),
				argThat("number of CryptoTransfer handled"::equals),
				any())).willReturn(transferHdl);
		// and:
		given(factory.from(
				argThat("TokenGetInfoRcv"::equals),
				argThat("number of TokenGetInfo received"::equals),
				any())).willReturn(tokenInfoRcv);
		given(factory.from(
				argThat("TokenGetInfoAns"::equals),
				argThat("number of TokenGetInfo answered"::equals),
				any())).willReturn(tokenInfoAns);

		// when:
		subject.registerWith(platform);

		// then:
		verify(platform).addAppStatEntry(transferRcv);
		verify(platform).addAppStatEntry(transferSub);
		verify(platform).addAppStatEntry(transferHdl);
		verify(platform).addAppStatEntry(tokenInfoRcv);
		verify(platform).addAppStatEntry(tokenInfoAns);
	}

	@Test
	public void updatesExpectedEntries() {
		// when:
		subject.countReceived(CryptoTransfer);
		subject.countReceived(CryptoTransfer);
		subject.countReceived(CryptoTransfer);
		subject.countSubmitted(CryptoTransfer);
		subject.countSubmitted(CryptoTransfer);
		subject.countHandled(CryptoTransfer);
		// and:
		subject.countReceived(TokenGetInfo);
		subject.countReceived(TokenGetInfo);
		subject.countReceived(TokenGetInfo);
		subject.countAnswered(TokenGetInfo);
		subject.countAnswered(TokenGetInfo);

		// then
		assertEquals(3L, subject.receivedSoFar(CryptoTransfer));
		assertEquals(2L, subject.submittedSoFar(CryptoTransfer));
		assertEquals(1L, subject.handledSoFar(CryptoTransfer));
		// and:
		assertEquals(3L, subject.receivedSoFar(TokenGetInfo));
		assertEquals(2L, subject.answeredSoFar(TokenGetInfo));
	}
}