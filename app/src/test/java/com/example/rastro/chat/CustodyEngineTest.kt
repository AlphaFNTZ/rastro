package com.example.rastro.chat

import com.google.crypto.tink.subtle.AesGcmJce
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],manifest=Config.NONE)
class CustodyEngineTest {
    private class Network : AutoCloseable {
        var time=CustodyClock(1800000000000,1000,"boot1")
        val nodes=linkedMapOf<String,Node>()
        val links=mutableSetOf<Pair<String,String>>()
        val queue=ArrayDeque<Triple<String,String,ByteArray>>()
        var drop: ((String,String,CustodyFrame)->Boolean)?=null
        val packets=mutableListOf<Triple<String,String,CustodyFrame>>()
        fun node(name: String)=Node(name,this).also { nodes[name]=it }
        fun connect(a: Node,b: Node) { links.add(a.name to b.name); links.add(b.name to a.name); update(); flush() }
        fun disconnect() { links.clear(); update(); queue.clear() }
        private fun update() { nodes.values.forEach { n -> n.engine.connections(links.filter { it.first==n.name }.map { it.second }.toSet()) } }
        fun flush() {
            var count=0
            while(queue.isNotEmpty()) {
                check(++count<1000) { "Loop de protocolo" }
                val (from,to,bytes)=queue.removeFirst()
                if(from to to !in links) continue
                val frame=CustodyFrame.decode(bytes); packets.add(Triple(from,to,frame))
                if(drop?.invoke(from,to,frame)!=true) nodes.getValue(to).engine.receive(from,bytes)
            }
        }
        override fun close() { nodes.values.forEach { it.db.close(); RuntimeEnvironment.getApplication().deleteDatabase(it.file) } }
    }
    private class Node(val name: String,val network: Network) {
        val crypto=ChatCrypto.generate()
        val contact=crypto.contact(name)
        val file="custody-${UUID.randomUUID()}.db"
        val aead=AesGcmJce(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) })
        var db=ChatStore(RuntimeEnvironment.getApplication(),aead,file)
        var custody=CustodyStore(db) { network.time }
        var engine=createEngine()
        private fun createEngine()=CustodyEngine(crypto,db,custody,{name},{ endpoint,bytes -> network.queue.add(Triple(name,endpoint,bytes)) },{ e,expected ->
            val from=requireNotNull(db.contact(e.from))
            val plain=crypto.open(e,from)
            if(e.kind=="TEXT") db.incoming(e,utf8(plain)) { crypto.seal(from,"ACK",pack { writeUTF(e.id); writeUTF(e.hash()) }) }
            else { var ref=""; var hash=""; unpack(plain) { ref=readUTF(); hash=readUTF() }; require(expected==null || expected==ref); db.acknowledge(e.from,ref,hash); null }
        })
        fun send(to: Node): ChatEnvelope {
            db.saveContact(to.contact); to.db.saveContact(contact)
            val e=crypto.seal(to.contact,"TEXT","Mensagem privada".toByteArray())
            db.outgoing(e,"Mensagem privada"); custody.maintain(); return e
        }
        fun restart() { db.close(); db=ChatStore(RuntimeEnvironment.getApplication(),aead,file); custody=CustodyStore(db) { network.time }; engine=createEngine() }
    }
    @Test fun storeCarryDeliverAndReturnReceiptAcrossSeparateEncountersAndRestarts() {
        Network().use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C"); val d=n.node("D")
            b.custody.setEnabled(true); d.custody.setEnabled(true)
            val e=a.send(c)
            n.connect(a,b)
            assertEquals("CARRIED",a.custody.status(e.id)); assertEquals("HELD",b.custody.held(e.id)?.state)
            assertEquals(b.contact.id,a.custody.carrier(e.id))
            n.disconnect(); b.restart(); n.connect(b,d)
            assertEquals(0,d.custody.snapshot().active)
            assertFalse(n.packets.any { it.first=="B" && it.second=="D" && it.third.action in listOf("OFFER","DELIVER") })
            n.disconnect(); n.connect(b,c)
            assertEquals(1,c.db.lines(a.contact.id).size); assertEquals("RECEIPT",b.custody.held(e.id)?.state)
            n.disconnect(); b.restart(); n.connect(b,a)
            assertEquals("DELIVERED",a.custody.status(e.id)); assertEquals("DONE",b.custody.held(e.id)?.state)
            assertTrue(a.db.pending().isEmpty()); assertEquals(0,b.custody.snapshot().active)
        }
    }
    @Test fun lostCustodyReplyNeverSelectsAnotherCarrier() {
        Network().use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C"); val d=n.node("D")
            b.custody.setEnabled(true); d.custody.setEnabled(true)
            val e=a.send(c)
            n.drop={from,_,f -> from=="B" && f.action=="STORED"}
            n.connect(a,b)
            assertEquals("CUSTODY_PENDING",a.custody.status(e.id)); assertEquals("HELD",b.custody.held(e.id)?.state)
            n.disconnect(); a.restart(); n.connect(a,d)
            assertEquals(b.contact.id,a.custody.carrier(e.id)); assertEquals(0,d.custody.snapshot().active)
            n.disconnect(); n.drop=null; n.connect(a,b)
            assertEquals("CARRIED",a.custody.status(e.id)); assertEquals(1,b.custody.snapshot().active)
        }
    }
    @Test fun disablingCarrierRefusesNewOffersButStillDeliversAcceptedPacket() {
        Network().use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C")
            val e=a.send(c); n.connect(a,b)
            assertEquals(0,b.custody.snapshot().active)
            b.custody.setEnabled(true); b.engine.tick(); n.flush()
            assertEquals("HELD",b.custody.held(e.id)?.state)
            b.custody.setEnabled(false); n.disconnect(); n.connect(b,c)
            assertEquals(1,c.db.lines(a.contact.id).size)
        }
    }
    @Test fun lostDestinationReceiptReusesDurableInboxWithoutDuplicates() {
        Network().use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C")
            b.custody.setEnabled(true); val e=a.send(c); n.connect(a,b); n.disconnect()
            n.drop={from,_,f -> from=="C" && f.action=="RETURN"}; n.connect(b,c)
            assertEquals("HELD",b.custody.held(e.id)?.state); assertEquals(1,c.db.lines(a.contact.id).size)
            n.disconnect(); c.restart(); n.drop=null; n.connect(b,c)
            assertEquals(1,c.db.lines(a.contact.id).size); assertEquals("RECEIPT",b.custody.held(e.id)?.state)
        }
    }
    @Test fun signatureOrOldSessionCannotForgePeerIdentity() {
        Network().use { n ->
            val a=n.node("A"); val b=n.node("B")
            n.connect(a,b)
            val old=n.packets.first { it.first=="A" && it.second=="B" && it.third.action=="HELLO" }.third
            assertTrue(runCatching { b.engine.receive("A",old.copy(signature=ByteArray(64)).encode()) }.isFailure)
            n.disconnect(); n.connect(a,b)
            assertTrue(runCatching { b.engine.receive("A",old.encode()) }.isFailure)
        }
    }
    @Test fun quotasRemovalAndExpiryDoNotResetOnRepeatedOffer() {
        Network().use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C")
            b.custody.setEnabled(true)
            val packets=(1..9).map { a.crypto.seal(c.contact,"TEXT","$it".toByteArray()) }
            packets.take(8).forEach { b.custody.accept(it,a.contact.id,CustodyStore.LIFETIME) }
            assertTrue(runCatching { b.custody.accept(packets.last(),a.contact.id,CustodyStore.LIFETIME) }.isFailure)
            b.custody.remove(packets.first().id)
            assertEquals("REMOVED",b.custody.accept(packets.first(),a.contact.id,CustodyStore.LIFETIME))
            n.time=n.time.copy(elapsed=n.time.elapsed+CustodyStore.LIFETIME+1,wall=n.time.wall-1000)
            b.custody.maintain()
            assertEquals(0,b.custody.snapshot().active)
            assertEquals("EXPIRED",b.custody.accept(packets[1],a.contact.id,CustodyStore.LIFETIME))
        }
    }
    @Test fun otherOriginCannotTransferCustodyAndUnknownSessionIsIgnored() {
        Network().use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C"); val d=n.node("D")
            d.custody.setEnabled(true)
            val e=a.send(c)
            assertTrue(runCatching { d.custody.accept(e,b.contact.id,CustodyStore.LIFETIME) }.isFailure)
            d.engine.receive("not-connected",CustodyFrame.challenge(UUID.randomUUID().toString()).encode())
            assertEquals(0,d.custody.snapshot().active)
        }
    }
    @Test fun rebootClockBudgetNeverExtendsValidity() {
        val previous=CustodyClock(10000,5000,"boot1")
        assertEquals(800L,remainingBudget(1000,previous,CustodyClock(500,5200,"boot1")))
        assertEquals(500L,remainingBudget(1000,previous,CustodyClock(10500,100,"boot2")))
        assertTrue(remainingBudget(1000,previous,CustodyClock(9000,100,"boot2"))<=0)
    }
    @Test fun globalQuotaReservesSpaceForEveryFutureReceipt() {
        Network().use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C")
            b.custody.setEnabled(true)
            // Distinct origins exercise the global budget independently of the per-origin quota.
            var accepted=0
            for(i in 1..128) {
                val e=a.crypto.seal(c.contact,"TEXT",byteArrayOf(1)).copy(
                    from=i.toString(16).padStart(64,'0'), cipher=ByteArray(8000))
                if(runCatching { b.custody.accept(e,e.from,CustodyStore.LIFETIME) }.isSuccess) accepted++
                else break
            }
            assertTrue(accepted in 1..127)
            val snapshot=b.custody.snapshot()
            assertTrue(snapshot.bytes+accepted*12288L<=2*1024*1024)
            assertFalse(b.custody.capacity("f".repeat(64),8500))
        }
    }
    @Test fun earlierDirectDeliveryAndLostFinalConfirmationRemainIdempotent() {
        Network().use { n ->
            val a=n.node("A"); val b=n.node("B"); val c=n.node("C")
            b.custody.setEnabled(true)
            val e=a.send(c); n.connect(a,b); n.disconnect()
            // A delivered directly first, but its receipt was lost.
            c.db.incoming(e,"Mensagem privada") { c.crypto.seal(a.contact,"ACK",pack { writeUTF(e.id); writeUTF(e.hash()) }) }
            n.connect(b,c)
            assertEquals(1,c.db.lines(a.contact.id).size)
            n.disconnect()
            n.drop={from,_,f -> from=="A" && f.action in listOf("SAVED","RELEASE")}
            n.connect(a,b)
            assertEquals("DELIVERED",a.custody.status(e.id))
            assertEquals("RECEIPT",b.custody.held(e.id)?.state)
            n.disconnect(); a.restart(); b.restart(); n.drop=null; n.connect(a,b)
            assertEquals("DELIVERED",a.custody.status(e.id))
            assertEquals("DONE",b.custody.held(e.id)?.state)
        }
    }
    @Test fun migrationPreservesVersionOneHistoryAndContacts() {
        Network().use { n ->
            val a=n.node("A"); val c=n.node("C"); val e=a.send(c)
            a.db.writableDatabase.execSQL("DROP TABLE dispatch"); a.db.writableDatabase.execSQL("DROP TABLE custody"); a.db.writableDatabase.execSQL("DROP TABLE custody_settings")
            a.db.writableDatabase.version=1; a.restart()
            assertEquals(c.contact.id,a.db.contact(c.contact.id)?.id)
            assertEquals(e.id,a.db.lines(c.contact.id).single().id)
            a.custody.maintain(); assertTrue(a.custody.budget(e.id)>0)
        }
    }
}