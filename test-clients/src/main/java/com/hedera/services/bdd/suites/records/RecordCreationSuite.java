package com.hedera.services.bdd.suites.records;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.crypto.HapiGetAccountRecords;
import com.hedera.services.bdd.suites.HapiApiSuite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

/* --------------------------- SPEC STATIC IMPORTS --------------------------- */
import static com.hedera.services.bdd.spec.HapiApiSpec.*;
import static com.hedera.services.bdd.spec.assertions.TransactionRecordAsserts.recordWith;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.*;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.*;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.*;
import static com.hedera.services.bdd.spec.assertions.AssertUtils.*;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.*;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.*;
import static org.junit.Assert.assertEquals;
/* --------------------------------------------------------------------------- */

public class RecordCreationSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(RecordCreationSuite.class);

	public static void main(String... args) {
		new RecordCreationSuite().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(
				new HapiApiSpec[] {
						newlyCreatedContractNoLongerGetsRecord(),
						accountsGetPayerRecordsIfSoConfigured(),
						calledContractNoLongerGetsRecord(),
						thresholdRecordsDontExistAnymore(),
				}
		);
	}

	private HapiApiSpec accountsGetPayerRecordsIfSoConfigured() {
		return defaultHapiSpec("AccountsGetPayerRecordsIfSoConfigured")
				.given(
						cryptoCreate("payer")
				).when(
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, FUNDING, 1_000L)
						).payingWith("payer").via("firstXfer"),
						getAccountRecords("payer").has(inOrder()),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("ledger.keepRecordsInState", "true"))
				).then(
						cryptoTransfer(
								tinyBarsFromTo(GENESIS, FUNDING, 1_000L)
						).payingWith("payer").via("secondXfer"),
						getAccountRecords("payer").has(inOrder(recordWith().txnId("secondXfer"))),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of("ledger.keepRecordsInState", "false"))
				);
	}

	private HapiApiSpec calledContractNoLongerGetsRecord() {
		return defaultHapiSpec("CalledContractNoLongerGetsRecord")
				.given(
						fileCreate("bytecode").path(PATH_TO_PAYABLE_CONTRACT_BYTECODE)
				).when(
						contractCreate("contract").bytecode("bytecode").via("createTxn"),
						contractCall("contract", DEPOSIT_ABI, 1_000L).via("callTxn").sending(1_000L)
				).then(
						getContractRecords("contract").has(inOrder())
				);
	}

	private HapiApiSpec newlyCreatedContractNoLongerGetsRecord() {
		return defaultHapiSpec("NewlyCreatedContractNoLongerGetsRecord")
				.given(
						fileCreate("bytecode").path(PATH_TO_PAYABLE_CONTRACT_BYTECODE)
				).when(
						contractCreate("contract").bytecode("bytecode").via("createTxn")
				).then(
						getContractRecords("contract").has(inOrder())
				);
	}

	private HapiApiSpec thresholdRecordsDontExistAnymore() {
		return defaultHapiSpec("OnlyNetAdjustmentIsComparedToThresholdWhenCreating")
				.given(
						cryptoCreate("payer"),
						cryptoCreate("lowSendThreshold").sendThreshold(1L),
						cryptoCreate("lowReceiveThreshold").receiveThreshold(1L),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"ledger.keepRecordsInState", "true"
								))
				).when(
						cryptoTransfer(
								tinyBarsFromTo(
										"lowSendThreshold",
										"lowReceiveThreshold",
										2L)
						).payingWith("payer").via("testTxn")
				).then(
						getAccountRecords("payer").has(inOrder(recordWith().txnId("testTxn"))),
						getAccountRecords("lowSendThreshold").has(inOrder()),
						getAccountRecords("lowReceiveThreshold").has(inOrder()),
						fileUpdate(APP_PROPERTIES)
								.payingWith(ADDRESS_BOOK_CONTROL)
								.overridingProps(Map.of(
										"ledger.keepRecordsInState", "false"
								))
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	private final String PATH_TO_PAYABLE_CONTRACT_BYTECODE = "src/main/resource/PayReceivable.bin";
	private final String DEPOSIT_ABI = "{\"constant\":false,\"inputs\":[{\"name\":\"amount\",\"type\":\"uint256\"}]," +
			"\"name\":\"deposit\",\"outputs\":[],\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"function\"}";
}
