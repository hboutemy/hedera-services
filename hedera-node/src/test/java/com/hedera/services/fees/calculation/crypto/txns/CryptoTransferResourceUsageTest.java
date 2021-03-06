package com.hedera.services.fees.calculation.crypto.txns;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.UsageEstimatorUtils;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.crypto.CryptoTransferUsage;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.fee.SigValueObj;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@RunWith(JUnitPlatform.class)
class CryptoTransferResourceUsageTest {
	private CryptoTransferResourceUsage subject;

	private TransactionBody nonCryptoTransferTxn;
	private TransactionBody cryptoTransferTxn;

	StateView view;
	int numSigs = 10, sigsSize = 100, numPayerKeys = 3;
	SigValueObj obj = new SigValueObj(numSigs, numPayerKeys, sigsSize);
	SigUsage sigUsage = new SigUsage(numSigs, sigsSize, numPayerKeys);

	CryptoTransferUsage usage;
	BiFunction<TransactionBody, SigUsage, CryptoTransferUsage> factory;

	@BeforeEach
	private void setup() throws Throwable {
		view = mock(StateView.class);

		cryptoTransferTxn = mock(TransactionBody.class);
		given(cryptoTransferTxn.hasCryptoTransfer()).willReturn(true);

		nonCryptoTransferTxn = mock(TransactionBody.class);
		given(nonCryptoTransferTxn.hasCryptoTransfer()).willReturn(false);

		factory = (BiFunction<TransactionBody, SigUsage, CryptoTransferUsage>)mock(BiFunction.class);
		given(factory.apply(cryptoTransferTxn, sigUsage)).willReturn(usage);

		usage = mock(CryptoTransferUsage.class);
		given(usage.get()).willReturn(MOCK_CRYPTO_TRANSFER_USAGE);

		CryptoTransferResourceUsage.factory = factory;
		given(factory.apply(cryptoTransferTxn, sigUsage)).willReturn(usage);

		subject = new CryptoTransferResourceUsage();
	}

	@Test
	public void recognizesApplicability() {
		// expect:
		assertTrue(subject.applicableTo(cryptoTransferTxn));
		assertFalse(subject.applicableTo(nonCryptoTransferTxn));
	}

	@Test
	public void delegatesToCorrectEstimate() throws Exception {
		// expect:
		assertEquals(
				MOCK_CRYPTO_TRANSFER_USAGE,
				subject.usageGiven(cryptoTransferTxn, obj, view));
	}

	public static final FeeData MOCK_CRYPTO_TRANSFER_USAGE = UsageEstimatorUtils.defaultPartitioning(
			FeeComponents.newBuilder()
					.setMin(1)
					.setMax(1_000_000)
					.setConstant(2)
					.setBpt(2)
					.setVpt(2)
					.setRbh(2)
					.setSbh(2)
					.setGas(2)
					.setTv(2)
					.setBpr(2)
					.setSbpr(2)
					.build(), 2);
}
