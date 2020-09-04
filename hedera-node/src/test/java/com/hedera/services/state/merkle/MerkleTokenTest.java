package com.hedera.services.state.merkle;

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

import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.serdes.IoReadingFunction;
import com.hedera.services.state.serdes.IoWritingConsumer;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.io.IOException;

import static com.hedera.services.state.merkle.MerkleTopic.serdes;
import static com.hedera.services.utils.MiscUtils.describe;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.Mockito.inOrder;

@RunWith(JUnitPlatform.class)
class MerkleTokenTest {
	JKey adminKey, otherAdminKey;
	JKey freezeKey, otherFreezeKey;
	String symbol = "NotAnHbar", otherSymbol = "NotAnHbarEither";
	int divisibility = 2, otherDivisibility = 3;
	long tokenFloat = 1_000_000, otherFloat = 1_000_001;
	boolean freezeDefault = true, otherFreezeDefault = false;
	EntityId treasury = new EntityId(1, 2, 3), otherTreasury = new EntityId(3, 2, 1);

	MerkleToken subject;
	MerkleToken other;

	@BeforeEach
	public void setup() {
		adminKey = new JEd25519Key("not-a-real-admin-key".getBytes());
		freezeKey = new JEd25519Key("not-a-real-freeze-key".getBytes());
		otherAdminKey = new JEd25519Key("not-a-real-admin-key-either".getBytes());
		otherFreezeKey = new JEd25519Key("not-a-real-freeze-key-either".getBytes());

		subject = new MerkleToken(tokenFloat, divisibility, adminKey, symbol, freezeDefault, treasury);
		subject.setFreezeKey(freezeKey);

		serdes = mock(DomainSerdes.class);
		MerkleToken.serdes = serdes;
	}

	@AfterEach
	public void cleanup() {
		MerkleToken.serdes = new DomainSerdes();
	}

	@Test
	public void deleteIsNoop() {
		// expect:
		assertDoesNotThrow(subject::delete);
	}

	@Test
	public void serializeWorks() throws IOException {
		// setup:
		var out = mock(SerializableDataOutputStream.class);
		// and:
		InOrder inOrder = inOrder(serdes, out);

		// when:
		subject.serialize(out);

		// then:
		inOrder.verify(serdes).serializeKey(adminKey, out);
		inOrder.verify(out).writeNormalisedString(symbol);
		inOrder.verify(out).writeSerializable(treasury, true);
		inOrder.verify(out).writeLong(tokenFloat);
		inOrder.verify(out).writeInt(divisibility);
		inOrder.verify(out).writeBoolean(freezeDefault);
		inOrder.verify(serdes).writeNullable(
				argThat(freezeKey::equals), argThat(out::equals), any(IoWritingConsumer.class));
	}

	@Test
	public void copyWorks() {
		// given:
		var copySubject = subject.copy();

		// expect:
		assertNotSame(copySubject, subject);
		assertEquals(subject, copySubject);
	}

	@Test
	public void deserializeWorks() throws IOException {
		// setup:
		SerializableDataInputStream fin = mock(SerializableDataInputStream.class);

		given(serdes.deserializeKey(fin)).willReturn(adminKey);
		given(serdes.readNullable(argThat(fin::equals), any(IoReadingFunction.class))).willReturn(freezeKey);
		given(fin.readNormalisedString(anyInt())).willReturn(symbol);
		given(fin.readLong()).willReturn(subject.tokenFloat());
		given(fin.readInt()).willReturn(subject.divisibility());
		given(fin.readBoolean()).willReturn(subject.accountsAreFrozenByDefault());
		given(fin.readSerializable()).willReturn(subject.treasury());
		// and:
		var read = new MerkleToken();

		// when:
		read.deserialize(fin, MerkleToken.MERKLE_VERSION);

		// then:
		assertEquals(subject, read);
	}

	@Test
	public void objectContractHoldsForDifferentFloats() {
		// given:
		other = new MerkleToken(otherFloat, divisibility, adminKey, symbol, freezeDefault, treasury);
		other.setFreezeKey(freezeKey);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void objectContractHoldsForDifferentDivisibility() {
		// given:
		other = new MerkleToken(tokenFloat, otherDivisibility, adminKey, symbol, freezeDefault, treasury);
		other.setFreezeKey(freezeKey);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void objectContractHoldsForDifferentAdminKey() {
		// given:
		other = new MerkleToken(tokenFloat, divisibility, otherAdminKey, symbol, freezeDefault, treasury);
		other.setFreezeKey(freezeKey);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void objectContractHoldsForDifferentSymbol() {
		// given:
		other = new MerkleToken(tokenFloat, divisibility, adminKey, otherSymbol, freezeDefault, treasury);
		other.setFreezeKey(freezeKey);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void objectContractHoldsForDifferentFreezeDefault() {
		// given:
		other = new MerkleToken(tokenFloat, divisibility, adminKey, symbol, otherFreezeDefault, treasury);
		other.setFreezeKey(freezeKey);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void objectContractHoldsForDifferentTreasury() {
		// given:
		other = new MerkleToken(tokenFloat, divisibility, adminKey, symbol, freezeDefault, otherTreasury);
		other.setFreezeKey(freezeKey);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void objectContractHoldsForDifferentFreezeKeys() {
		// given:
		other = new MerkleToken(tokenFloat, divisibility, adminKey, symbol, freezeDefault, treasury);
		other.setFreezeKey(otherFreezeKey);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());

		// and given:
		other.setFreezeKey(MerkleToken.UNUSED_KEY);

		// expect:
		assertNotEquals(subject, other);
		// and:
		assertNotEquals(subject.hashCode(), other.hashCode());
	}

	@Test
	public void hashCodeContractMet() {
		// given:
		var defaultSubject = new MerkleAccountState();
		// and:
		var identicalSubject = new MerkleToken(tokenFloat, divisibility, adminKey, symbol, freezeDefault, treasury);
		identicalSubject.setFreezeKey(freezeKey);

		// and:
		other = new MerkleToken(
				otherFloat, otherDivisibility, otherAdminKey, otherSymbol, otherFreezeDefault, otherTreasury);
		other.setFreezeKey(otherFreezeKey);

		// expect:
		assertNotEquals(subject.hashCode(), defaultSubject.hashCode());
		assertNotEquals(subject.hashCode(), other.hashCode());
		assertEquals(subject.hashCode(), identicalSubject.hashCode());
	}

	@Test
	public void equalsWorksWithExtremes() {
		// expect:
		assertEquals(subject, subject);
		assertNotEquals(subject, null);
		assertNotEquals(subject, new Object());
	}

	@Test
	public void toStringWorks() {
		// expect:
		assertEquals("MerkleToken{" +
						"symbol=" + symbol + ", " +
						"treasury=" + treasury.toAbbrevString() + ", " +
						"float=" + tokenFloat + ", " +
						"divisibility=" + divisibility + ", " +
						"adminKey=" + describe(adminKey) + ", " +
						"freezeKey=" + describe(freezeKey) + ", " +
						"accountsFrozenByDefault=" + freezeDefault + "}",
				subject.toString());
	}

	@Test
	public void merkleMethodsWork() {
		// expect;
		assertEquals(MerkleToken.MERKLE_VERSION, subject.getVersion());
		assertEquals(MerkleToken.RUNTIME_CONSTRUCTABLE_ID, subject.getClassId());
		assertTrue(subject.isLeaf());
	}

	@Test
	public void tmpProviderThrowsAlways() {
		// expect:
		assertThrows(UnsupportedOperationException.class,
				() -> MerkleToken.LEGACY_PROVIDER.deserialize(null));
	}
}