package com.example.rastro.chat

import java.nio.ByteBuffer
import java.util.UUID

/** Public routing material only. Personal contact registration is deliberately separate. */
internal fun publicContact(bytes: ByteArray): ChatContact {
    lateinit var result: ChatContact
    unpack(bytes) { result=ChatContact("Transporte",field(1024),field(1024)) }
    ChatCrypto.validate(result)
    return result
}
internal fun ChatContact.publicBytes()=pack { field(encryption); field(verification) }
internal fun fingerprint(value: String) { require(value.matches(Regex("[a-f0-9]{64}"))) }

data class ForwardPolicy(
    val issuer: ChatContact, val message: String, val hash: String, val destination: String,
    val lanes: List<String>, val reference: String, val referenceHash: String, val signature: ByteArray
) {
    fun signed()=pack {
        writeUTF("rastro-forward-policy-2"); field(issuer.publicBytes())
        writeUTF(message); writeUTF(hash); writeUTF(destination)
        writeInt(2); lanes.forEach { writeUTF(it) }
        writeLong(CustodyStore.LIFETIME); writeInt(16)
        writeUTF(reference); writeUTF(referenceHash)
    }
    fun encode()=pack { field(signed()); field(signature) }
    fun validate(e: ChatEnvelope) {
        require(lanes.size==2 && lanes.distinct().size==2); lanes.forEach(::uuid)
        require(message==e.id && hash==e.hash() && issuer.id==e.from && destination==e.to)
        if(e.kind=="TEXT") require(reference.isEmpty() && referenceHash.isEmpty())
        else { uuid(reference); fingerprint(referenceHash) }
        ChatCrypto.validate(issuer)
        ChatCrypto.verify(issuer,e.signedBytes(),e.signature)
        ChatCrypto.verify(issuer,signed(),signature)
    }
    companion object {
        fun create(crypto: ChatCrypto,e: ChatEnvelope,reference: String="",hash: String=""): ForwardPolicy {
            require(crypto.contact("local").id==e.from)
            val policy=ForwardPolicy(crypto.contact("Transporte"),e.id,e.hash(),e.to,
                List(2) { UUID.randomUUID().toString() },reference,hash,byteArrayOf())
            return policy.copy(signature=crypto.sign(policy.signed()))
        }
        fun decode(bytes: ByteArray): ForwardPolicy {
            lateinit var result: ForwardPolicy
            unpack(bytes) {
                val data=field(4096); val sig=field(256)
                unpack(data) {
                    require(readUTF()=="rastro-forward-policy-2")
                    val issuer=publicContact(field(2200)); val id=readUTF(); val hash=readUTF(); val to=readUTF()
                    require(readInt()==2); val lanes=List(2) { readUTF() }
                    require(readLong()==CustodyStore.LIFETIME && readInt()==16)
                    result=ForwardPolicy(issuer,id,hash,to,lanes,readUTF(),readUTF(),sig)
                }
            }
            require(result.encode().contentEquals(bytes))
            return result
        }
    }
}

data class ForwardHop(val transaction: String,val signer: ChatContact,val next: String,val remaining: Long,val signature: ByteArray) {
    fun signed(previous: String,lane: String)=pack {
        writeUTF("rastro-forward-grant-2"); writeUTF(previous); writeUTF(lane)
        writeUTF(transaction); field(signer.publicBytes()); writeUTF(next); writeLong(remaining)
    }
    fun encode()=pack { writeUTF(transaction); field(signer.publicBytes()); writeUTF(next); writeLong(remaining); field(signature) }
    companion object {
        fun decode(bytes: ByteArray): ForwardHop {
            lateinit var hop: ForwardHop
            unpack(bytes) { hop=ForwardHop(readUTF(),publicContact(field(2200)),readUTF(),readLong(),field(256)) }
            uuid(hop.transaction); fingerprint(hop.next); require(hop.remaining in 1..CustodyStore.LIFETIME)
            return hop
        }
    }
}

data class ForwardBundle(val envelope: ChatEnvelope,val policy: ForwardPolicy,val lane: String,val hops: List<ForwardHop>) {
    fun encode()=pack {
        field(envelope.encode()); field(policy.encode()); writeUTF(lane); writeInt(hops.size)
        hops.forEach { field(it.encode()) }
    }.also { require(it.size<=MAX_BYTES) { "Prova de transporte excede o limite" } }
    fun chain()=pack { writeInt(hops.size); hops.forEach { field(it.encode()) } }
    fun owner(): String = hops.lastOrNull()?.next ?: envelope.from
    fun visited() = listOf(envelope.from)+hops.map { it.next }
    fun previousHash(): String = hops.fold(digest(policy.encode())) { hash,hop -> digest(pack { writeUTF(hash); field(hop.encode()) }) }
    fun validate() {
        policy.validate(envelope); require(lane in policy.lanes && hops.size<=16)
        var owner=envelope.from; var previous=digest(policy.encode()); var budget=CustodyStore.LIFETIME
        val visited=mutableSetOf(owner); val transactions=mutableSetOf<String>()
        hops.forEach { hop ->
            uuid(hop.transaction); fingerprint(hop.next)
            require(hop.signer.id==owner && hop.next!=envelope.to && visited.add(hop.next) && transactions.add(hop.transaction))
            require(hop.remaining in 1..budget)
            ChatCrypto.verify(hop.signer,hop.signed(previous,lane),hop.signature)
            previous=digest(pack { writeUTF(previous); field(hop.encode()) })
            owner=hop.next; budget=hop.remaining
        }
        encode()
    }
    fun delegate(crypto: ChatCrypto,to: String,remaining: Long): ForwardBundle {
        require(owner()==crypto.contact("local").id && hops.size<16 && to !in visited() && to!=envelope.to)
        require(remaining in 1..(hops.lastOrNull()?.remaining ?: CustodyStore.LIFETIME))
        val hop=ForwardHop(UUID.randomUUID().toString(),crypto.contact("Transporte"),to,remaining,byteArrayOf())
        return copy(hops=hops+hop.copy(signature=crypto.sign(hop.signed(previousHash(),lane)))).also { it.validate() }
    }
    companion object {
        const val MAX_BYTES=14000
        fun decode(bytes: ByteArray): ForwardBundle {
            require(bytes.size<=MAX_BYTES)
            lateinit var bundle: ForwardBundle
            unpack(bytes) {
                val e=ChatEnvelope.decode(field(12288)); val p=ForwardPolicy.decode(field(4600)); val lane=readUTF()
                val size=readInt(); require(size in 0..16)
                bundle=ForwardBundle(e,p,lane,List(size) { ForwardHop.decode(field(3000)) })
            }
            require(bundle.encode().contentEquals(bytes)); bundle.validate(); return bundle
        }
        fun from(e: ChatEnvelope,p: ForwardPolicy,lane: String,chain: ByteArray): ForwardBundle {
            lateinit var hops: List<ForwardHop>
            unpack(chain) { val count=readInt(); require(count in 0..16); hops=List(count) { ForwardHop.decode(field(3000)) } }
            return ForwardBundle(e,p,lane,hops)
        }
    }
}

/** RCS2 is negotiated independently of RCS1; old peers never receive a downgraded grant. */
data class ForwardFrame(val action: String,val nonce: String,val id: String,val body: ByteArray,val signature: ByteArray) {
    init { require(action in setOf("CHALLENGE","HELLO","QUERY","NEED","OFFER","STORED","REJECT","DELIVER","PROOF")); uuid(nonce); uuid(id); require(body.size<=14500 && signature.size<=256) }
    fun signed()=pack { writeInt(MAGIC); writeUTF(action); writeUTF(nonce); writeUTF(id); field(body) }
    fun encode()=pack { field(signed()); field(signature) }.also { require(it.size<=16384) }
    companion object {
        const val MAGIC=0x52435332
        fun recognizes(bytes: ByteArray)=bytes.size>=8 && ByteBuffer.wrap(bytes,4,4).int==MAGIC
        fun challenge(nonce: String)=ForwardFrame("CHALLENGE",nonce,UUID.randomUUID().toString(),byteArrayOf(),byteArrayOf())
        fun decode(bytes: ByteArray): ForwardFrame {
            require(bytes.size<=16384)
            lateinit var frame: ForwardFrame
            unpack(bytes) {
                val data=field(15500); val sig=field(256)
                unpack(data) { require(readInt()==MAGIC); frame=ForwardFrame(readUTF(),readUTF(),readUTF(),field(14500),sig) }
            }
            require(frame.encode().contentEquals(bytes)); return frame
        }
    }
}

/** Full signed query echoed in NEED; fresh session/peer and bounded local query ledger bind the answer. */
data class ForwardQuery(val id: String,val hash: String,val origin: String,val lane: String,val transaction: String,val offerHash: String,val kind: String,val size: Int) {
    init { require(kind in listOf("TEXT","ACK")); uuid(id); uuid(lane); fingerprint(hash); fingerprint(origin); if(transaction.isNotEmpty()) { uuid(transaction); fingerprint(offerHash) }; require(size in 1..ForwardBundle.MAX_BYTES) }
    fun encode()=pack { writeUTF(id); writeUTF(hash); writeUTF(origin); writeUTF(lane); writeUTF(transaction); writeUTF(offerHash); writeUTF(kind); writeInt(size) }
    companion object {
        fun decode(bytes: ByteArray): ForwardQuery {
            lateinit var query: ForwardQuery
            unpack(bytes) { query=ForwardQuery(readUTF(),readUTF(),readUTF(),readUTF(),readUTF(),readUTF(),readUTF(),readInt()) }
            return query
        }
    }
}
