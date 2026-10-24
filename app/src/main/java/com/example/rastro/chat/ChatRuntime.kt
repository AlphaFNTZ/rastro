package com.example.rastro.chat

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException

/** Process-scoped owner; SQLite, keys and cryptography never run on the UI thread. */
class ChatRuntime private constructor(private val context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private val worker = ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,ArrayBlockingQueue(128)) { r -> Thread(r,"rastro-chat") }
    private val vault by lazy { ChatVault(context) }
    private val crypto by lazy { vault.identity() }
    private val db by lazy { ChatStore(context,vault) }
    private val clockBoot by lazy { android.provider.Settings.Global.getInt(context.contentResolver, "boot_count", -1).let { if(it >= 0) it.toString() else java.util.UUID.randomUUID().toString() } }
    private val custody by lazy { CustodyStore(db) { CustodyClock(System.currentTimeMillis(),SystemClock.elapsedRealtime(),clockBoot) } }
    private val forwarding by lazy { ForwardStore(db) { CustodyClock(System.currentTimeMillis(),SystemClock.elapsedRealtime(),clockBoot) } }
    private val forwardEngine by lazy { ForwardEngine(crypto,forwarding,{ custody.enabled() },
        { endpoint,bytes -> main.post { if(endpoint in endpoints) directTransport?.invoke(endpoint,bytes) } },::process,::changed) }
    private val custodyEngine by lazy { CustodyEngine(crypto,db,custody,::name,{ endpoint,bytes -> main.post { if(endpoint in endpoints) directTransport?.invoke(endpoint,bytes) } },::process,::changed) }
    private var directTransport: ((String,ByteArray) -> Unit)? = null
    private var endpoints = emptySet<String>()
    fun connections(ids: Set<String>) {
        endpoints=ids
        task<Unit> { custodyEngine.connections(ids); forwardEngine.connections(ids) }
        connected(ids.isNotEmpty())
    }
    fun custodySnapshot(callback: (Result<CustodySnapshot>) -> Unit) = task(callback) {
        forwarding.maintain()
        val legacy=custody.snapshot(); val usage=forwarding.usage()
        legacy.copy(active=legacy.active+usage.first,bytes=legacy.bytes+usage.second,packets=legacy.packets+forwarding.snapshot())
    }
    fun carrierMode(enabled: Boolean, callback: (Result<Unit>) -> Unit) = task(callback) { custody.setEnabled(enabled); custodyEngine.tick(); forwardEngine.tick(); changed() }
    fun removeCustody(id: String, callback: (Result<Unit>) -> Unit) = task(callback) { if(forwarding.packet(id)!=null) forwarding.finish(id,"REMOVED") else custody.remove(id); changed() }
    private val relay by lazy { ChatRelay(crypto.contact("local").id, ::deliver) { frame, except -> main.post { transport?.invoke(frame.encode(), except) } } }
    private val listeners = LinkedHashSet<() -> Unit>()
    // Only touched on main thread. Transport exists only while the service is active.
    private var transport: ((ByteArray, String?) -> Unit)? = null
    private var connected = false
    fun connected(value: Boolean) { val changed = connected != value; connected = value; if(changed && value) { lastTick = 0; tick() } }
    private var lastTick = 0L
    private var inboundWindow = 0L
    private var inboundCount = 0
    private var lastNotice = 0L
    var notice: String = "Cadastre os contatos por QR Code nos dois aparelhos."
        private set
    private fun name(): String = context.getSharedPreferences("rastro-no",Context.MODE_PRIVATE).getString("nome",null) ?: "${Build.MANUFACTURER} ${Build.MODEL}".take(24)
    fun listen(listener: () -> Unit) { listeners.add(listener); listener() }
    fun unlisten(listener: () -> Unit) { listeners.remove(listener) }
    private fun changed() { main.post { listeners.toList().forEach { it() } } }
    fun attach(sender: (ByteArray, String?) -> Unit, direct: (String,ByteArray) -> Unit) { transport = sender; directTransport=direct; lastTick = 0; tick() }
    fun detach() { transport = null; directTransport=null; endpoints=emptySet(); connected = false; task<Unit> { custodyEngine.clear(); forwardEngine.clear() } }
    private fun <T> task(callback: (Result<T>) -> Unit = {}, action: () -> T) {
        try { worker.execute { val result = runCatching(action); main.post { callback(result) } } }
        catch (_: RejectedExecutionException) { callback(Result.failure(IllegalStateException("Chat ocupado; tente novamente"))) }
    }
    fun ownQr(callback: (Result<String>) -> Unit) = task(callback) { crypto.contact(name()).qr() }
    fun inspectQr(qr: String, callback: (Result<ChatContact>) -> Unit) = task(callback) {
        val c = ChatContact.parse(qr); ChatCrypto.register(); ChatCrypto.validate(c)
        require(c.id != crypto.contact("local").id) { "Este é o QR Code deste aparelho" }; c
    }
    fun saveContact(c: ChatContact, callback: (Result<Unit>) -> Unit) = task(callback) { db.saveContact(c); main.post { lastTick = 0; tick() }; changed() }
    fun contacts(callback: (Result<List<ChatContact>>) -> Unit) = task(callback) { db.contacts() }
    fun entries(callback: (Result<List<ChatContactEntry>>) -> Unit) = task(callback) { db.entries() }
    fun alias(id: String, name: String, callback: (Result<Unit>) -> Unit) = task(callback) { db.alias(id,name); changed() }
    fun lines(peer: String, callback: (Result<List<ChatLine>>) -> Unit) = task(callback) { db.lines(peer) }
    fun send(peer: String, text: String, callback: (Result<Unit>) -> Unit) = task(callback) {
        require(text.isNotBlank() && text.toByteArray().size <= ChatEnvelope.MAX_TEXT_BYTES) { "Use de 1 a 2.048 bytes de texto" }
        val c = requireNotNull(db.contact(peer)) { "Cadastre o contato por QR Code" }
        custody.maintain()
        forwarding.maintain()
        val e = crypto.seal(c,"TEXT",text.toByteArray())
        forwarding.atomic {
            db.outgoing(e,text)
            db.writableDatabase.execSQL("UPDATE messages SET transport_version=2 WHERE id=?",arrayOf(e.id))
            forwarding.createOwn(crypto,e)
        }
        main.post { lastTick = 0; tick() }; changed()
    }
    fun receive(bytes: ByteArray, endpoint: String) {
        if(transport == null || bytes.size > ChatFrame.MAX_BYTES) return
        val now = SystemClock.elapsedRealtime()
        if(now - inboundWindow >= 1000) { inboundWindow = now; inboundCount = 0 }
        if(++inboundCount > 24) return
        task<Unit>({ result -> if(result.isFailure && now - lastNotice > 10000) {
            lastNotice = now; notice = "Pacote de chat rejeitado. Confira o cadastro mútuo por QR Code."; listeners.toList().forEach { it() }
        } }) { if(ForwardFrame.recognizes(bytes)) forwardEngine.receive(endpoint,bytes) else if(CustodyFrame.recognizes(bytes)) custodyEngine.receive(endpoint,bytes) else relay.receive(ChatFrame.decode(bytes),endpoint) }
    }
    fun tick() {
        if(transport == null || !connected) return
        val now = SystemClock.elapsedRealtime()
        if(lastTick != 0L && now - lastTick < 30000) return
        lastTick = now
        task<Unit>({ result -> if(result.isFailure) { notice = "Chat indisponível: não foi possível abrir a identidade ou a fila local."; listeners.toList().forEach { it() } } }) {
            custody.maintain(); forwarding.maintain()
            db.pending().forEach { relay.originate(it); db.attempted(it.id) }
            forwardEngine.tick()
            // Bounded connected-mesh attempts do not create any new custody authorizations.
            forwarding.active().filter { !it.own }.take(2).forEach { packet ->
                packet.envelope?.let { relay.originate(ChatEnvelope.decode(it)) }
                forwarding.attempted(packet.id)
            }
            custodyEngine.tick(); changed()
        }
    }
    private fun deliver(e: ChatEnvelope) { process(e,null)?.let { relay.originate(it) } }
    private fun process(e: ChatEnvelope, reference: String?): ChatEnvelope? {
        val from = requireNotNull(db.contact(e.from)) { "Remetente não cadastrado" }
        val plain = crypto.open(e,from)
        if(e.kind == "TEXT") {
            require(plain.size <= ChatEnvelope.MAX_TEXT_BYTES)
            val text = utf8(plain); require(text.isNotBlank())
            val receipt = forwarding.atomic {
                val ack=db.incoming(e,text) { crypto.seal(from,"ACK",pack { writeUTF(e.id); writeUTF(e.hash()) }) }
                forwarding.receipt(crypto,e,ack)
                ack
            }
            changed(); return receipt
        } else {
            var ref = ""; var hash = ""
            unpack(plain) { ref = readUTF(); hash = readUTF(); uuid(ref); require(hash.matches(Regex("[0-9a-f]{64}"))) }
            require(reference==null || reference==ref) { "Recibo referente a outra mensagem" }
            db.acknowledge(e.from,ref,hash)
            forwarding.packet(ref)?.let { require(it.hash==hash && it.destination==e.from); forwarding.finish(ref) }
        }
        changed()
        return null
    }
    companion object {
        @Volatile private var instance: ChatRuntime? = null
        fun get(context: Context): ChatRuntime = instance ?: synchronized(this) { instance ?: ChatRuntime(context.applicationContext).also { instance = it } }
    }
}