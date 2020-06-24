package com.hedera.services.legacy.unit;

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

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.legacy.config.PropertiesLoader;
import com.hedera.services.legacy.handler.FCStorageWrapper;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.legacy.service.GlobalFlag;
import com.hedera.services.legacy.unit.handler.FeeScheduleInterceptor;
import com.hedera.services.records.RecordCache;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.txns.validation.BasicPrecheck;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.mocks.TestContextValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.builder.RequestBuilder;
import com.hederahashgraph.builder.TransactionSigner;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.legacy.core.StorageKey;
import com.hedera.services.legacy.core.StorageValue;
import com.hedera.services.legacy.initialization.NodeAccountsCreation;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.swirlds.common.internal.SettingsCommon;
import com.swirlds.fcmap.FCMap;
import net.i2p.crypto.eddsa.EdDSAPublicKey;
import net.i2p.crypto.eddsa.KeyPairGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import static org.mockito.BDDMockito.*;

@TestInstance(Lifecycle.PER_CLASS)
class QueryValidationTest {
	@BeforeAll
    public static void setupAll() {
      SettingsCommon.transactionMaxBytes = 1_234_567;
    }

  long payerAccountInitialBalance = 100000;
  private RecordCache recordCache;
  private FCMap<MapKey, HederaAccount> map = new FCMap<>(MapKey::deserialize, HederaAccount::deserialize);
  private FCMap<StorageKey, StorageValue> storageMap = new FCMap<>(StorageKey::deserialize,
      StorageValue::deserialize);
  ;
  private AccountID nodeAccount =
      AccountID.newBuilder().setAccountNum(3).setRealmNum(0).setShardNum(0).build();
  private AccountID payerAccount =
      AccountID.newBuilder().setAccountNum(300).setRealmNum(0).setShardNum(0).build();
  private AccountID negetiveAccountNo =
      AccountID.newBuilder().setAccountNum(-1111).setRealmNum(0).setShardNum(0).build();
  private AccountID lowBalanceAccount = AccountID.newBuilder().setAccountNum(3001).setRealmNum(0)
      .setShardNum(0).build();
  private KeyPair payerKeyGenerated = new KeyPairGenerator().generateKeyPair();
  private TransactionHandler transactionHandler;

  private Transaction createQueryHeaderTransfer(AccountID payer) throws Exception {
    Timestamp timestamp = RequestBuilder.getTimestamp(Instant.now());
    Duration transactionDuration = RequestBuilder.getDuration(30);

    SignatureList sigList = SignatureList.getDefaultInstance();
    Transaction transferTx = RequestBuilder.getCryptoTransferRequest(payer.getAccountNum(),
        payer.getRealmNum(), payer.getShardNum(), nodeAccount.getAccountNum(),
        nodeAccount.getRealmNum(), nodeAccount.getShardNum(), 100, timestamp, transactionDuration,
        false, "test", sigList, payer.getAccountNum(), -100l, nodeAccount.getAccountNum(), 100l);
    List<PrivateKey> keyList = new ArrayList<>();
    PrivateKey genPrivKey = payerKeyGenerated.getPrivate();
    keyList.add(genPrivKey);
    keyList.add(genPrivKey);
    transferTx = TransactionSigner.signTransaction(transferTx, keyList);
    return transferTx;
  }

  @BeforeAll
  void initializeState() throws Exception {
    FCStorageWrapper storageWrapper = new FCStorageWrapper(
        storageMap);

    FeeScheduleInterceptor feeScheduleInterceptor = mock(FeeScheduleInterceptor.class);
    PropertyLoaderTest.populatePropertiesWithConfigFilesPath(
        "./configuration/dev/application.properties",
        "./configuration/dev/api-permission.properties");
    PrecheckVerifier precheckVerifier = mock(PrecheckVerifier.class);
    given(precheckVerifier.hasNecessarySignatures(any())).willReturn(true);
    transactionHandler = new TransactionHandler(
            mock(RecordCache.class),
            precheckVerifier,
            map,
            nodeAccount);
    transactionHandler.setBasicPrecheck(new BasicPrecheck(TestContextValidator.TEST_VALIDATOR));
    byte[] pubKey = ((EdDSAPublicKey) payerKeyGenerated.getPublic()).getAbyte();
    onboardAccount(payerAccount, pubKey, payerAccountInitialBalance);
    onboardAccount(lowBalanceAccount, pubKey, 100L);

    GlobalFlag.getInstance().setExchangeRateSet(getDefaultExchangeRateSet());
  }

  private static ExchangeRateSet getDefaultExchangeRateSet() {
    long expiryTime = PropertiesLoader.getExpiryTime();
    return RequestBuilder.getExchangeRateSetBuilder(1, 1, expiryTime, 1, 1, expiryTime);
  }

  private void onboardAccount(AccountID account, byte[] publicKey, long initialBalance)
      throws Exception {
    NodeAccountsCreation.createAccounts(initialBalance, MiscUtils.commonsBytesToHex(publicKey), account, map
    );
  }

  @Test
  void testValidateGetInfoQuery_validateQuery() throws Exception {
    Transaction transferTransaction = createQueryHeaderTransfer(payerAccount);
    Query cryptoGetInfoQuery = RequestBuilder.getCryptoGetInfoQuery(payerAccount,
        transferTransaction, ResponseType.ANSWER_ONLY);

    ResponseCodeEnum result = transactionHandler.validateQuery(cryptoGetInfoQuery, true);
    assert (result == ResponseCodeEnum.OK);
  }

  @Test
  void testValidateGetInfoQuery_validateQuery_negetive() throws Exception {
    Transaction transferTransaction = createQueryHeaderTransfer(negetiveAccountNo);
    Query cryptoGetInfoQuery = RequestBuilder.getCryptoGetInfoQuery(negetiveAccountNo,
        transferTransaction, ResponseType.ANSWER_ONLY);

    ResponseCodeEnum result = transactionHandler.validateQuery(cryptoGetInfoQuery, true);
    assertEquals(result, ResponseCodeEnum.NOT_SUPPORTED);
  }


  @Test
  void testValidateGetInfoQuery_validateFee_inSufficientTxFee() throws Exception {
    Transaction transaction = createQueryHeaderTransfer(lowBalanceAccount);
    ResponseCodeEnum result =
            transactionHandler.validateScheduledFee(HederaFunctionality.CryptoGetInfo, transaction, 100);
    assertEquals(ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE, result);

  }
}