package com.example.rastro.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class IdentidadeNoTest {
    @Test fun `anuncio preserva uuid e nome com caracteres especiais`() {
        val id = UUID.randomUUID().toString()
        val identidade = AnuncioNo.decodificar(AnuncioNo.codificar(id, "  Base | Resgate ç  "))!!
        assertEquals(id, identidade.id)
        assertEquals("Base | Resgate ç", identidade.nome)
        assertEquals("Base | Resgate ç • ${id.take(8)}", identidade.rotulo)
    }

    @Test fun `nome e normalizado limitado e nao aceita vazio`() {
        assertEquals("Celular João", AnuncioNo.normalizarNome(" Celular\n\tJoão "))
        val longo = AnuncioNo.normalizarNome("á".repeat(40))
        assertTrue(longo.toByteArray(Charsets.UTF_8).size <= AnuncioNo.MAX_NOME_BYTES)
        assertEquals("a".repeat(31), AnuncioNo.normalizarNome("a".repeat(31) + "📡"))
        assertThrows(IllegalArgumentException::class.java) { AnuncioNo.normalizarNome(" \n\t ") }
    }

    @Test fun `anuncios invalidos sao ignorados`() {
        assertNull(AnuncioNo.decodificar("telefone-do-joao"))
        assertNull(AnuncioNo.decodificar("rastro2|${UUID.randomUUID()}|bm9tZQ"))
        assertNull(AnuncioNo.decodificar("rastro1|uuid-invalido|bm9tZQ"))
    }

    @Test fun `nomes duplicados continuam distintos pelo uuid`() {
        val a = AnuncioNo.decodificar(AnuncioNo.codificar(UUID.randomUUID().toString(), "Equipe"))!!
        val b = AnuncioNo.decodificar(AnuncioNo.codificar(UUID.randomUUID().toString(), "Equipe"))!!
        assertEquals(a.nome, b.nome)
        assertNotEquals(a.id, b.id)
        assertNotEquals(a.rotulo, b.rotulo)
    }
}
