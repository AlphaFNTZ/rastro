package com.example.rastro.chat

import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

abstract class ForwardContract {
    abstract fun context(): Context

    @Test fun chainAndIndependentReceiptReturnWithoutPersonalContacts() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val b=n.node("B"); val d=n.node("D"); val c=n.node("C"); val x=n.node("X")
            listOf(b,d,x).forEach { it.custody.setEnabled(true) }
            val original=a.send(c)
            n.connect(a,b)
            assertEquals("FORWARDED",a.db.lines(c.contact.id).single().status)
            assertEquals(1,b.forward.lanes(original.envelope.id).size)
            assertTrue(b.db.contacts().isEmpty())
            n.disconnect(); b.restart(); n.advance(61_000); n.connect(b,d)
            assertEquals("TRANSFERRED",b.forward.lanes(original.envelope.id).single().state)
            assertEquals(2,d.forward.bundle(d.forward.lanes(original.envelope.id).single()).hops.size)
            assertTrue(d.db.contacts().isEmpty())
            n.disconnect(); d.restart(); n.connect(d,c)
            assertEquals(1,c.db.lines(a.contact.id).size)
            val proof=requireNotNull(c.forward.proofFor(original.envelope.id))
            assertEquals("DONE",d.forward.packet(original.envelope.id)?.state)
            n.disconnect(); c.restart(); n.connect(c,x)
            assertNotNull(x.forward.packet(proof.envelope.id))
            n.disconnect(); x.restart(); n.advance(61_000); n.connect(x,a)
            assertEquals("DELIVERED",a.db.lines(c.contact.id).single().status)
            assertEquals("DONE",x.forward.packet(proof.envelope.id)?.state)
            assertTrue(x.db.contacts().isEmpty())
        }
    }
    @Test fun lostStorageReplyFreezesGrantAcrossRestartAndDifferentPeer() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C"); val d=n.node("D")
            b.custody.setEnabled(true); d.custody.setEnabled(true)
            val p=a.send(c)
            n.drop={from,_,f -> from=="B" && f.action=="STORED"}
            n.connect(a,b)
            val pending=a.forward.lanes(p.envelope.id).single { it.state=="PENDING" }
            val firstBytes=a.forward.bundle(pending).encode()
            n.disconnect(); a.restart(); n.connect(a,d)
            val frozen=requireNotNull(a.forward.lane(pending.id))
            assertEquals("PENDING",frozen.state); assertEquals(b.contact.id,frozen.peer)
            assertArrayEquals(firstBytes,a.forward.bundle(frozen).encode())
            assertEquals(2,a.forward.lanes(p.envelope.id).size)
            n.disconnect(); n.drop=null; n.connect(a,b)
            assertEquals("TRANSFERRED",a.forward.lane(pending.id)?.state)
        }
    }
    @Test fun lostOfferNeverCreatesReplacementBudget() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C")
            b.custody.setEnabled(true); val p=a.send(c)
            n.drop={_,_,f -> f.action=="OFFER"}; n.connect(a,b)
            val frozen=a.forward.lanes(p.envelope.id).single { it.state=="PENDING" }
            assertNull(b.forward.packet(p.envelope.id))
            n.disconnect(); a.restart(); b.restart(); n.drop=null; n.connect(a,b)
            assertEquals("TRANSFERRED",a.forward.lane(frozen.id)?.state)
            assertEquals(frozen.id,b.forward.lanes(p.envelope.id).single().id)
        }
    }
    @Test fun twoIndependentLinesDeduplicateFinalDeliveryAndReceiptBudget() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val b=n.node("B"); val d=n.node("D"); val c=n.node("C")
            b.custody.setEnabled(true); d.custody.setEnabled(true)
            val p=a.send(c); n.connect(a,b); n.disconnect(); n.connect(a,d)
            assertEquals(2,a.forward.lanes(p.envelope.id).count { it.state=="TRANSFERRED" })
            n.disconnect(); n.connect(b,c)
            val proof=requireNotNull(c.forward.proofFor(p.envelope.id)).encode()
            n.disconnect(); c.restart(); n.connect(d,c)
            assertEquals(1,c.db.lines(a.contact.id).size)
            assertArrayEquals(proof,c.forward.proofFor(p.envelope.id)?.encode())
            val receipt=requireNotNull(c.forward.proofFor(p.envelope.id))
            assertEquals(2,c.forward.lanes(receipt.envelope.id).size)
        }
    }
    @Test fun disabledModeRejectsNewButDeliversAndDelegatesAcceptedResponsibilities() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val b=n.node("B"); val d=n.node("D"); val c=n.node("C")
            val p=a.send(c); n.connect(a,b); assertNull(b.forward.packet(p.envelope.id))
            b.custody.setEnabled(true); n.tick(); assertNotNull(b.forward.packet(p.envelope.id))
            b.custody.setEnabled(false); n.disconnect(); n.advance(61_000); d.custody.setEnabled(true); n.connect(b,d)
            assertNotNull(d.forward.packet(p.envelope.id))
        }
    }
    @Test fun policyChainDestinationAndSessionTamperingAreRejected() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C"); val d=n.node("D")
            val p=a.send(c)
            assertTrue(runCatching { p.copy(policy=p.policy.copy(destination=b.contact.id)).validate() }.isFailure)
            assertTrue(runCatching { p.copy(policy=p.policy.copy(lanes=List(2) { UUID.randomUUID().toString() })).validate() }.isFailure)
            assertTrue(runCatching { p.copy(envelope=p.envelope.copy(cipher=ByteArray(100))).validate() }.isFailure)
            val grant=p.delegate(a.crypto,b.contact.id,CustodyStore.LIFETIME)
            assertTrue(runCatching { grant.copy(hops=listOf(grant.hops.single().copy(next=d.contact.id))).validate() }.isFailure)
            assertTrue(runCatching { grant.delegate(d.crypto,c.contact.id,1000) }.isFailure)
            n.connect(a,b)
            val old=n.traffic.first { it.first=="A" && it.third.action=="HELLO" }.third
            n.disconnect(); n.connect(a,b)
            assertTrue(runCatching { b.engine.receive("A",old.encode()) }.isFailure)
        }
    }
    @Test fun maxHopsAndCyclesDoNotResetOnAnotherCarrier() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val c=n.node("C")
            var p=a.send(c); var owner=a.crypto
            repeat(16) { index ->
                val next=ChatCrypto.generate()
                p=p.delegate(owner,next.contact("N$index").id,CustodyStore.LIFETIME-index*1000)
                assertTrue(p.encode().size<=ForwardBundle.MAX_BYTES)
                owner=next
            }
            assertEquals(16,p.hops.size)
            assertTrue(runCatching { p.delegate(owner,ChatCrypto.generate().contact("extra").id,1000) }.isFailure)
            assertTrue(runCatching { p.delegate(owner,a.contact.id,1000) }.isFailure)
        }
    }
    @Test fun removalAndExpiryCannotRenewAuthorizationOrReceipt() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C")
            b.custody.setEnabled(true); val p=a.send(c); n.connect(a,b)
            val lane=b.forward.lanes(p.envelope.id).single(); val grant=b.forward.bundle(lane)
            b.forward.finish(p.envelope.id,"REMOVED")
            assertEquals("REMOVED",b.forward.accept(grant,a.contact.id,b.contact.id,1000))
            assertNull(b.forward.lane(lane.id)?.chain)
            n.advance(CustodyStore.LIFETIME+1)
            assertEquals("EXPIRED",a.forward.packet(p.envelope.id)?.state)
            assertEquals("EXPIRED",a.db.lines(c.contact.id).single().status)
            assertTrue(runCatching { a.forward.reserve(p.lane,b.contact.id,a.crypto) }.isFailure)
        }
    }
    @Test fun transactionRollbackRestoresBudgetBeforeAnyGrantIsEmitted() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C")
            val p=a.send(c)
            assertTrue(runCatching { a.forward.atomic { a.forward.reserve(p.lane,b.contact.id,a.crypto); error("disk failure before commit") } }.isFailure)
            assertEquals("ACTIVE",a.forward.lane(p.lane)?.state)
            assertTrue(a.forward.bundle(requireNotNull(a.forward.lane(p.lane))).hops.isEmpty())
        }
    }
    @Test fun quotasAndRebootNeverRefreshTimeBudget() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C")
            b.custody.setEnabled(true)
            val bundles=(1..9).map { a.send(c,"$it") }
            bundles.take(8).forEach { p -> b.forward.accept(p.delegate(a.crypto,b.contact.id,10000),a.contact.id,b.contact.id,10000) }
            assertTrue(runCatching { val p=bundles.last(); b.forward.accept(p.delegate(a.crypto,b.contact.id,10000),a.contact.id,b.contact.id,10000) }.isFailure)
            n.clock=n.clock.copy(wall=n.clock.wall-10000,elapsed=0,boot="boot2")
            b.forward.maintain()
            assertTrue(b.forward.active().isEmpty())
        }
    }
    @Test fun directDeliveryThenCarriedCopyDoesNotDuplicateConversation() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C")
            b.custody.setEnabled(true); val p=a.send(c); n.connect(a,b); n.disconnect()
            c.deliver(p.envelope,null)
            val receipt=c.forward.proofFor(p.envelope.id)!!.encode()
            n.connect(b,c)
            assertEquals(1,c.db.lines(a.contact.id).size)
            assertArrayEquals(receipt,c.forward.proofFor(p.envelope.id)!!.encode())
        }
    }
    @Test fun v2MigrationPreservesLegacyCustodyWithoutNewGrants() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C")
            b.custody.setEnabled(true)
            val e=a.crypto.seal(c.contact,"TEXT","legacy".toByteArray())
            b.custody.accept(e,a.contact.id,CustodyStore.LIFETIME)
            b.db.writableDatabase.execSQL("DROP TABLE forward_lanes")
            b.db.writableDatabase.execSQL("DROP TABLE forward_packets")
            b.db.writableDatabase.version=2; b.restart()
            assertEquals(3,b.db.writableDatabase.version)
            assertEquals("HELD",b.custody.held(e.id)?.state)
            assertNull(b.forward.packet(e.id))
        }
    }

    @Test fun terminalMarkersKeepTheirDeadlineWhenProofIsRepeated() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C")
            b.custody.setEnabled(true); val p=a.send(c); n.connect(a,b); n.disconnect()
            b.forward.finish(p.envelope.id,"REMOVED")
            n.advance(ForwardStore.RETENTION-1000)
            assertNotNull(b.forward.packet(p.envelope.id))
            b.forward.finish(p.envelope.id,"REMOVED")
            n.advance(1001)
            assertNull(b.forward.packet(p.envelope.id))
        }
    }
    @Test fun foreignOrMismatchedReceiptCannotCleanAnotherMessage() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C")
            val p=a.send(c)
            val forged=b.crypto.seal(a.contact,"ACK",pack { writeUTF(p.envelope.id); writeUTF(p.envelope.hash()) })
            val foreign=b.forward.createOwn(b.crypto,forged,p.envelope.id,p.envelope.hash())
            assertTrue(runCatching { a.forward.applyProof(foreign) }.isFailure)
            assertEquals("ACTIVE",a.forward.packet(p.envelope.id)?.state)
            val other=a.send(c,"Outra mensagem")
            c.deliver(other.envelope,null)
            val unrelated=requireNotNull(c.forward.proofFor(other.envelope.id))
            a.forward.applyProof(unrelated)
            assertEquals("ACTIVE",a.forward.packet(p.envelope.id)?.state)
        }
    }
    @Test fun lateReceiptConfirmsExpiredHistoryWithoutRecreatingReceiptBudget() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val c=n.node("C")
            val p=a.send(c)
            val ack=requireNotNull(c.deliver(p.envelope,null))
            val proof=requireNotNull(c.forward.proofFor(p.envelope.id)).encode()
            n.advance(CustodyStore.LIFETIME+1)
            assertEquals("EXPIRED",a.db.lines(c.contact.id).single().status)
            c.deliver(p.envelope,null)
            assertArrayEquals(proof,c.forward.proofFor(p.envelope.id)?.encode())
            assertEquals("EXPIRED",c.forward.packet(ack.id)?.state)
            a.deliver(ack,p.envelope.id)
            assertEquals("DELIVERED",a.db.lines(c.contact.id).single().status)
        }
    }
    @Test fun fullTextQuotaReservesSpaceForReceiptsAndNeverExceedsGlobalBudget() {
        ForwardNetwork(context()).use { n ->
            val a=n.node("A"); val c=n.node("C")
            repeat(48) { a.send(c,"Mensagem $it") }
            assertTrue(runCatching { a.send(c,"Sem capacidade") }.isFailure)
            assertEquals(48,a.db.lines(c.contact.id).size)
            assertFalse(a.forward.capacity(a.contact.id,own=true,kind="TEXT"))
            assertTrue(a.forward.capacity(a.contact.id,own=true,kind="ACK"))
            repeat(16) {
                val ack=a.crypto.seal(c.contact,"ACK",byteArrayOf(1))
                a.forward.createOwn(a.crypto,ack,UUID.randomUUID().toString(),"a".repeat(64))
            }
            assertFalse(a.forward.capacity(a.contact.id,own=true,kind="ACK"))
            assertEquals(64,a.forward.active().size)
        }
    }
}
