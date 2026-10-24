package com.example.rastro

import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.rastro.chat.*
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Base64

/**
 * Explicitly invoked ADB laboratory. Each invocation is a fresh process and reopens encrypted keys/SQLite.
 * Public material/ciphertext crosses devices via the host; this is not a Nearby radio test.
 */
@RunWith(AndroidJUnit4::class)
class ForwardExchangeTest {
    @Test fun command() {
        val args=InstrumentationRegistry.getArguments()
        val role=args.getString("role")
        assumeTrue(role in listOf("A","B","C","D"))
        val base=InstrumentationRegistry.getInstrumentation().targetContext
        val prefix="forward-lab-$role"
        val context=object: ContextWrapper(base) {
            override fun getSharedPreferences(name: String,mode: Int)=base.getSharedPreferences("$prefix-$name",mode)
            override fun getNoBackupFilesDir(): File=File(base.noBackupFilesDir,prefix).apply { mkdirs() }
            override fun getDatabasePath(name: String): File=base.getDatabasePath("$prefix.db")
        }
        val result=JSONObject()
        val file=File(base.filesDir,"forward-lab-result.json")
        if(args.getString("op")=="cleanup") {
            base.deleteDatabase("$prefix.db")
            base.deleteSharedPreferences("$prefix-rastro-chat-keys")
            File(context.noBackupFilesDir,"chat-identity-created").delete()
            context.noBackupFilesDir.delete()
            result.put("cleaned",true); file.writeText(result.toString()); return
        }
        val vault=ChatVault(context); val crypto=vault.identity()
        val local=crypto.contact(role!!)
        ChatStore(base,vault,"$prefix.db").use { db ->
            val clock={ CustodyClock(System.currentTimeMillis(),SystemClock.elapsedRealtime(),"lab-boot") }
            val store=ForwardStore(db,0,clock)
            val custody=CustodyStore(db,clock); custody.setEnabled(true); store.maintain()
            fun inputBundle()=ForwardBundle.decode(Base64.getUrlDecoder().decode(requireNotNull(args.getString("bundle"))))
            fun outputBundle(bundle: ForwardBundle) {
                result.put("bundle",Base64.getUrlEncoder().withoutPadding().encodeToString(bundle.encode()))
                result.put("message",bundle.envelope.id); result.put("lane",bundle.lane)
            }
            when(args.getString("op")) {
                "identity" -> result.put("qr",local.qr()).put("identity",local.id)
                "send" -> {
                    val recipient=ChatContact.parse(requireNotNull(args.getString("contact"))); db.saveContact(recipient)
                    val e=crypto.seal(recipient,"TEXT","Ensaio entre três emuladores".toByteArray())
                    val bundle=store.atomic {
                        db.outgoing(e,"Ensaio entre três emuladores")
                        db.writableDatabase.execSQL("UPDATE messages SET transport_version=2 WHERE id=?",arrayOf(e.id))
                        store.createOwn(crypto,e)
                    }
                    outputBundle(bundle)
                }
                "delegate" -> {
                    val recipient=ChatContact.parse(requireNotNull(args.getString("contact")))
                    outputBundle(store.reserve(requireNotNull(args.getString("lane")),recipient.id,crypto))
                }
                "accept" -> {
                    val bundle=inputBundle()
                    val state=store.accept(bundle,bundle.hops.last().signer.id,local.id,bundle.hops.last().remaining)
                    if(bundle.envelope.kind=="ACK") store.applyProof(bundle.copy(lane=bundle.policy.lanes.first(),hops=emptyList()))
                    result.put("state",state); result.put("contacts",db.contacts().size)
                    outputBundle(store.bundle(requireNotNull(store.lane(bundle.lane))))
                }
                "stored" -> {
                    val bundle=inputBundle()
                    store.stored(ForwardQuery(bundle.envelope.id,bundle.envelope.hash(),bundle.envelope.from,bundle.lane,
                        bundle.hops.last().transaction,digest(bundle.encode()),bundle.envelope.kind,bundle.encode().size),bundle.owner())
                    result.put("state",store.lane(bundle.lane)?.state)
                }
                "deliver" -> {
                    val bundle=inputBundle(); bundle.validate(); require(bundle.envelope.to==local.id)
                    val contact=ChatContact.parse(requireNotNull(args.getString("contact"))); db.saveContact(contact)
                    val plain=crypto.open(bundle.envelope,contact)
                    if(bundle.envelope.kind=="TEXT") {
                        val receipt=store.atomic {
                            val ack=db.incoming(bundle.envelope,String(plain,Charsets.UTF_8)) {
                                crypto.seal(contact,"ACK",pack { writeUTF(bundle.envelope.id); writeUTF(bundle.envelope.hash()) })
                            }
                            store.receipt(crypto,bundle.envelope,ack)
                        }
                        outputBundle(receipt)
                    } else {
                        var reference=""; var hash=""
                        unpack(plain) { reference=readUTF(); hash=readUTF() }
                        require(reference==bundle.policy.reference && hash==bundle.policy.referenceHash)
                        db.acknowledge(bundle.envelope.from,reference,hash)
                        store.applyProof(bundle.copy(lane=bundle.policy.lanes.first(),hops=emptyList()))
                        result.put("delivered",db.lines(contact.id).any { it.id==reference && it.status=="DELIVERED" })
                    }
                    result.put("history",db.lines(contact.id).size)
                }
                "snapshot" -> {
                    val lane=requireNotNull(store.lane(requireNotNull(args.getString("lane"))))
                    result.put("state",lane.state).put("peer",lane.peer).put("identity",local.id).put("contacts",db.contacts().size)
                }
                else -> error("Laboratory operation required")
            }
        }
        file.writeText(result.toString())
    }
}
