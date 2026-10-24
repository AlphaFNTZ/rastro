package com.example.rastro.chat

import android.content.Context
import com.google.crypto.tink.subtle.AesGcmJce
import java.util.UUID

/** Deterministic encounter harness shared by local and real-Android tests; no user's data is touched. */
class ForwardNetwork(private val context: Context): AutoCloseable {
    var clock=CustodyClock(1800000000000,1000,"boot-1")
    val nodes=linkedMapOf<String,Node>()
    private val links=mutableSetOf<Pair<String,String>>()
    private val queue=ArrayDeque<Triple<String,String,ByteArray>>()
    var drop: ((String,String,ForwardFrame)->Boolean)?=null
    val traffic=mutableListOf<Triple<String,String,ForwardFrame>>()
    fun node(name: String)=Node(name).also { nodes[name]=it }
    fun connect(a: Node,b: Node) {
        links.add(a.name to b.name); links.add(b.name to a.name)
        update(); flush()
    }
    fun disconnect() { links.clear(); update(); queue.clear() }
    fun advance(ms: Long) { clock=clock.copy(wall=clock.wall+ms,elapsed=clock.elapsed+ms); nodes.values.forEach { it.forward.maintain() } }
    fun tick() { nodes.values.forEach { it.engine.tick() }; flush() }
    private fun update() { nodes.values.forEach { n -> n.engine.connections(links.filter { it.first==n.name }.map { it.second }.toSet()) } }
    fun flush() {
        var count=0
        while(queue.isNotEmpty()) {
            check(++count<2000) { "Protocol loop" }
            val (from,to,bytes)=queue.removeFirst()
            if((from to to) !in links) continue
            val f=ForwardFrame.decode(bytes); traffic.add(Triple(from,to,f))
            if(drop?.invoke(from,to,f)!=true) nodes.getValue(to).engine.receive(from,bytes)
        }
    }
    inner class Node(val name: String) {
        val crypto=ChatCrypto.generate()
        val contact=crypto.contact(name)
        val file="forward-test-${UUID.randomUUID()}.db"
        private val aead=AesGcmJce(ByteArray(32).also { java.security.SecureRandom().nextBytes(it) })
        var db=ChatStore(context,aead,file)
        var forward=ForwardStore(db) { clock }
        var custody=CustodyStore(db) { clock }
        var engine=createEngine()
        private fun createEngine()=ForwardEngine(crypto,forward,{custody.enabled()},
            { to,bytes -> queue.add(Triple(name,to,bytes)) },::deliver)
        fun deliver(e: ChatEnvelope,expected: String?): ChatEnvelope? {
            val sender=requireNotNull(db.contact(e.from))
            val plain=crypto.open(e,sender)
            return if(e.kind=="TEXT") forward.atomic {
                val text=utf8(plain); require(text.isNotBlank() && plain.size<=ChatEnvelope.MAX_TEXT_BYTES)
                val receipt=db.incoming(e,text) { crypto.seal(sender,"ACK",pack { writeUTF(e.id); writeUTF(e.hash()) }) }
                forward.receipt(crypto,e,receipt); receipt
            } else {
                var id=""; var hash=""
                unpack(plain) { id=readUTF(); hash=readUTF() }
                require(expected==null || expected==id)
                db.acknowledge(e.from,id,hash)
                forward.packet(id)?.let { require(it.hash==hash && it.destination==e.from); forward.finish(id) }
                null
            }
        }
        fun send(to: Node,text: String="Texto privado entre A e C"): ForwardBundle {
            db.saveContact(to.contact); to.db.saveContact(contact)
            val e=crypto.seal(to.contact,"TEXT",text.toByteArray())
            return forward.atomic {
                db.outgoing(e,text)
                db.writableDatabase.execSQL("UPDATE messages SET transport_version=2 WHERE id=?",arrayOf(e.id))
                forward.createOwn(crypto,e)
            }
        }
        fun restart() { db.close(); db=ChatStore(context,aead,file); forward=ForwardStore(db) { clock }; custody=CustodyStore(db) { clock }; engine=createEngine() }
    }
    override fun close() { nodes.values.forEach { it.db.close(); context.deleteDatabase(it.file) } }
}
