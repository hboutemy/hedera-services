package com.hedera.services.bdd.suites.perf;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.utilops.LoadTest;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.randomUtf8Bytes;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromPem;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class SubmitMessageLoadTest extends LoadTest {

	private static final org.apache.logging.log4j.Logger log = LogManager.getLogger(SubmitMessageLoadTest.class);
	private static String topicID = null;
	private static int messageSize = 256;
	private static String pemFile = null;
	public static void main(String... args) {
		int usedArgs = parseArgs(args);

		// Usage
		//
		// 1) create new topic with auto generated key, an topicSubmitKey.pem will ge exported for later use
		// args: [size]
		//
		// 2) create new topic with pre-exist PEM file
		// args: [size] [pemFile]
		//
		// 3) submit message to pre-exist topic
		// args: [size] [pemFile] [topicID]
		//


		// parsing local argument specific to this test
		if (args.length > (usedArgs)) {
			messageSize = Integer.parseInt(args[usedArgs]);
			log.info("Set messageSize as " + messageSize);
			usedArgs++;
		}

		if (args.length > (usedArgs)) {
			pemFile = args[usedArgs];
			log.info("Set pemFile as " + pemFile);
			usedArgs++;
		}

		if (args.length > usedArgs) {
			topicID = args[usedArgs];
			log.info("Set topicID as " + topicID);
			usedArgs++;
		}

		SubmitMessageLoadTest suite = new SubmitMessageLoadTest();
		suite.setReportStats(true);
		suite.runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(runSubmitMessages());
	}

	@Override
	public boolean hasInterestingStats() {
		return true;
	}

	private static HapiApiSpec runSubmitMessages() {
		PerfTestLoadSettings settings = new PerfTestLoadSettings();
		final AtomicInteger submittedSoFar = new AtomicInteger(0);

		Supplier<HapiSpecOperation[]> submitBurst = () -> new HapiSpecOperation[] {
				opSupplier(settings).get()
		};

		return defaultHapiSpec("RunSubmitMessages")
				.given(
						withOpContext((spec, ignore) -> settings.setFrom(spec.setup().ciPropertiesMap())),
						// if no pem file defined then create a new submitKey
						pemFile == null ? newKeyNamed("submitKey") :
								keyFromPem(pemFile)
								.name("submitKey")
								.simpleWacl()
								.passphrase(KeyFactory.PEM_PASSPHRASE),

						// if just created a new key then export spec for later reuse
						pemFile == null ? withOpContext((spec, ignore) ->spec.keys().exportSimpleKey("topicSubmitKey.pem", "submitKey")):
								sleepFor(100),
						logIt(ignore -> settings.toString())
				).when(
						cryptoCreate("sender").balance(Long.valueOf(500000000L))
								.withRecharging()
								.rechargeWindow(30).payingWith(GENESIS)
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED),
						topicID == null ? createTopic("topic")
								.submitKeyName("submitKey").payingWith(GENESIS)
								.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED):
								sleepFor(100),
						sleepFor(5000) //wait all other thread ready
				).then(
						defaultLoadTest(submitBurst, settings)
				);
	}

	private static Supplier<HapiSpecOperation> opSupplier(PerfTestLoadSettings settings) {
		byte[] payload = randomUtf8Bytes(settings.getIntProperty("messageSize", messageSize) - 8);
		var op = submitMessageTo(topicID != null ? topicID : "topic")
				.message(ArrayUtils.addAll(ByteBuffer.allocate(8).putLong(Instant.now().toEpochMilli()).array(), payload))
				.noLogging()
				.payingWith("sender")
				.signedBy("sender", "submitKey")
				.suppressStats(true)
				.hasRetryPrecheckFrom(BUSY, DUPLICATE_TRANSACTION, PLATFORM_TRANSACTION_NOT_CREATED, INSUFFICIENT_PAYER_BALANCE)
				.hasKnownStatusFrom(SUCCESS, OK)
				.deferStatusResolution();
		if (settings.getBooleanProperty("isChunk", false)) {
			return () -> op.chunkInfo(1, 1).usePresetTimestamp();
		}
		return () -> op;
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
