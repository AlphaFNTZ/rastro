package com.example.rastro.chat

import java.util.UUID

/** Two independently movable grants. All commands are authenticated on explicit live endpoints. */
class ForwardEngine(
    private val crypto: ChatCrypto, private val store: ForwardStore,
    private val enabled: () -> Boolean,
    private val transmit: (String,ByteArray) -> Unit,
    private val deliver: (ChatEnvelope,String?) -> ChatEnvelope?,
    private val changed: () -> Unit = {}
) {
    private data class Peer(val local: String=UUID.randomUUID().toString(),var remote: String?=null,
        var identity: ChatContact?=null,var carrier: Boolean=false,
        val seen: LinkedHashSet<String> = LinkedHashSet(),
        val queries: LinkedHashMap<String,ForwardQuery> = LinkedHashMap())
    private val peers=linkedMapOf<String,Peer>()
    private val local=crypto.contact("local").id
    fun connections(endpoints: Set<String>) {
        peers.keys.retainAll(endpoints)
        endpoints.sorted().take(32).forEach { endpoint ->
            if(endpoint !in peers) peers[endpoint]=Peer().also { transmit(endpoint,ForwardFrame.challenge(it.local).encode()) }
        }
    }
    fun clear() { peers.clear() }
    fun tick() {
        store.maintain()
        peers.entries.sortedBy { it.value.identity?.id ?: it.key }.forEach { (endpoint,p) ->
            if(p.identity==null || p.remote==null) transmit(endpoint,ForwardFrame.challenge(p.local).encode())
            else { hello(endpoint); pump(endpoint) }
        }
        changed()
    }
    private fun send(endpoint: String,action: String,body: ByteArray) {
        val nonce=peers[endpoint]?.remote ?: return
        val frame=ForwardFrame(action,nonce,UUID.randomUUID().toString(),body,byteArrayOf())
        transmit(endpoint,frame.copy(signature=crypto.sign(frame.signed())).encode())
    }
    private fun hello(endpoint: String)=send(endpoint,"HELLO",pack { field(crypto.contact("Transporte").publicBytes()); writeBoolean(enabled()) })
    private fun proof(endpoint: String,bundle: ForwardBundle)=send(endpoint,"PROOF",bundle.copy(lane=bundle.policy.lanes.first(),hops=emptyList()).encode())
    fun receive(endpoint: String,bytes: ByteArray) {
        val peer=peers[endpoint] ?: return
        val frame=ForwardFrame.decode(bytes)
        if(frame.action=="CHALLENGE") {
            require(frame.signature.isEmpty() && frame.body.isEmpty())
            peer.remote=frame.nonce; hello(endpoint); return
        }
        require(frame.nonce==peer.local) { "Sessão expirada" }
        if(frame.action=="HELLO") {
            lateinit var identity: ChatContact; var mode=false
            unpack(frame.body) { identity=publicContact(field(2200)); mode=readBoolean() }
            require(identity.id!=local && (peer.identity==null || peer.identity?.id==identity.id))
            ChatCrypto.verify(identity,frame.signed(),frame.signature)
            peer.identity=identity; peer.carrier=mode
        } else ChatCrypto.verify(requireNotNull(peer.identity),frame.signed(),frame.signature)
        if(!peer.seen.add(frame.id)) return
        if(peer.seen.size>512) peer.seen.remove(peer.seen.first())
        store.maintain()
        val actor=requireNotNull(peer.identity).id
        when(frame.action) {
            "HELLO" -> pump(endpoint)
            "QUERY" -> {
                val q=ForwardQuery.decode(frame.body)
                val packet=store.packet(q.id)
                if(packet!=null) require(packet.hash==q.hash && packet.origin==q.origin)
                val finalProof=store.knownProof(q.id)
                if(finalProof!=null) proof(endpoint,finalProof)
                else if(q.transaction.isNotEmpty() && store.lane(q.lane)!=null) {
                    val saved=store.received(q,actor)
                    send(endpoint,if(saved?.state in listOf("ACTIVE","PENDING","TRANSFERRED")) "STORED" else "REJECT",q.encode())
                } else {
                    val available=enabled() && (packet?.state=="ACTIVE" || (packet==null && store.capacity(q.origin,kind=q.kind)))
                    send(endpoint,if(available) "NEED" else "REJECT",q.encode())
                }
            }
            "NEED" -> {
                val q=ForwardQuery.decode(frame.body)
                if(peer.queries[q.lane]!=q) return // stale answer to an earlier inventory query
                val lane=store.lane(q.lane) ?: return
                val packet=store.packet(q.id) ?: return
                require(lane.message==q.id && packet.hash==q.hash && packet.origin==q.origin)
                if(packet.state!="ACTIVE" || lane.state !in listOf("ACTIVE","PENDING")) return
                val grant=store.reserve(q.lane,actor,crypto)
                send(endpoint,"OFFER",pack { writeLong(requireNotNull(store.packet(q.id)).remaining); field(grant.encode()) })
                val committed=query(grant,true)
                peer.queries[q.lane]=committed
            }
            "OFFER" -> {
                var remaining=0L; lateinit var bundle: ForwardBundle
                unpack(frame.body) { remaining=readLong(); bundle=ForwardBundle.decode(field(ForwardBundle.MAX_BYTES)) }
                require(bundle.owner()==local && bundle.hops.lastOrNull()?.signer?.id==actor)
                store.knownProof(bundle.envelope.id)?.let { proof(endpoint,it); return }
                val result=runCatching { store.atomic {
                    val state=store.accept(bundle,actor,local,remaining)
                    if(bundle.envelope.kind=="ACK") store.applyProof(bundle.copy(lane=bundle.policy.lanes.first(),hops=emptyList()))
                    state
                } }
                val q=query(bundle,true)
                send(endpoint,if(result.getOrNull() in listOf("ACTIVE","PENDING","TRANSFERRED")) "STORED" else "REJECT",q.encode())
            }
            "STORED" -> {
                val q=ForwardQuery.decode(frame.body)
                val lane=store.lane(q.lane) ?: return
                val packet=store.packet(q.id) ?: return
                if(packet.state=="ACTIVE" && lane.state in listOf("PENDING","TRANSFERRED")) store.stored(q,actor)
            }
            "REJECT" -> {
                val q=ForwardQuery.decode(frame.body)
                if(peer.queries[q.lane]==q) peer.queries.remove(q.lane)
                // A grant already emitted remains frozen. A rejection cannot recycle its budget.
            }
            "DELIVER" -> {
                val bundle=ForwardBundle.decode(frame.body)
                require(bundle.envelope.to==local)
                if(bundle.envelope.kind=="TEXT") {
                    val receipt=store.atomic {
                        val ack=requireNotNull(deliver(bundle.envelope,null))
                        store.receipt(crypto,bundle.envelope,ack)
                    }
                    proof(endpoint,receipt)
                } else {
                    deliver(bundle.envelope,bundle.policy.reference)
                    store.applyProof(bundle.copy(lane=bundle.policy.lanes.first(),hops=emptyList()))
                    proof(endpoint,bundle)
                }
            }
            "PROOF" -> {
                val receipt=ForwardBundle.decode(frame.body)
                require(receipt.envelope.kind=="ACK" && receipt.hops.isEmpty())
                if(receipt.envelope.to==local) deliver(receipt.envelope,receipt.policy.reference)
                store.applyProof(receipt)
                // Only the final receipt recipient can acknowledge disposal of the carried ACK.
                val carried=store.packet(receipt.envelope.id)
                if(carried!=null && carried.hash==receipt.envelope.hash() && actor==carried.destination) store.finish(carried.id)
            }
        }
        changed()
    }
    private fun query(bundle: ForwardBundle,committed: Boolean)=ForwardQuery(
        bundle.envelope.id,bundle.envelope.hash(),bundle.envelope.from,bundle.lane,
        if(committed) requireNotNull(bundle.hops.lastOrNull()).transaction else "",
        if(committed) digest(bundle.encode()) else "",bundle.envelope.kind,bundle.encode().size)
    private fun pump(endpoint: String) {
        val peer=peers[endpoint] ?: return
        val identity=peer.identity?.id ?: return
        if(peer.remote==null) return
        val directIds=peers.values.mapNotNull { it.identity?.id }.toSet()
        var sent=0
        for(packet in store.active()) {
            if(sent>=4) break
            val lanes=store.lanes(packet.id)
            val live=lanes.filter { it.chain!=null }
            if(live.isEmpty()) continue
            if(identity==packet.destination) {
                send(endpoint,"DELIVER",store.bundle(live.first()).encode())
                store.attempted(packet.id); sent++; continue
            }
            if(packet.destination in directIds) continue
            for(lane in live) {
                if(sent>=4) break
                val bundle=store.bundle(lane)
                val pending=lane.state=="PENDING"
                if(pending && lane.peer!=identity) continue
                if(!pending) {
                    if(lane.state!="ACTIVE" || !peer.carrier || bundle.owner()!=local ||
                        bundle.hops.size>=16 || identity in bundle.visited()) continue
                    if(!packet.own && packet.initial-packet.remaining<store.delegateIntervalMs) continue
                    // Prefer independent first-hop peers instead of spending both lines on one encounter.
                    if(lanes.any { it.id!=lane.id && it.peer==identity }) continue
                    // Check that extending the chain fits before reserving its irreversible grant.
                    if(runCatching { bundle.delegate(crypto,identity,packet.remaining).encode() }.isFailure) continue
                }
                val q=query(bundle,pending)
                peer.queries[lane.id]=q
                if(peer.queries.size>128) peer.queries.remove(peer.queries.keys.first())
                send(endpoint,"QUERY",q.encode()); sent++
                store.attempted(packet.id)
                // One new line per packet in this encounter; the next opportunity may use another peer.
                break
            }
        }
    }
}
