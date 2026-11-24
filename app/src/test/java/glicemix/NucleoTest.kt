package glicemix

import glicemix.analise.AnalisadorPadroes
import glicemix.analise.Dados
import glicemix.analise.Episodios
import glicemix.data.Conversao
import glicemix.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class NucleoTest {
    private val base = LocalDateTime.of(2026, 11, 10, 12, 0)

    @Test fun etiquetasEMarcacaoSobrevivemAoCsv() {
        val r = Refeicao("r1", base, TipoRefeicao.ALMOCO, "Arroz", 60.0, tags = listOf("Estresse", "Álcool"), marcado = true)
        val d = Dose("d1", base, TipoDose.ULTRARRAPIDA, "Fiasp", 6.0, refeicaoId = "r1", tags = listOf("Estresse"))
        assertEquals(r, Conversao.refeicao(Conversao.campos(r)))
        assertEquals(d, Conversao.dose(Conversao.campos(d)))
    }

    @Test fun linhaAntigaSemCamposNovosContinuaLegivel() {
        val d = Conversao.dose(listOf("x", Conversao.texto(base), "BASAL", "Tresiba", "18.0", "UI", ""))
        assertEquals("", d.refeicaoId); assertTrue(d.tags.isEmpty()); assertTrue(!d.marcado)
    }

    @Test fun episodioLigaDoseEMedeOEfeito() {
        val r = Refeicao("r1", base, TipoRefeicao.ALMOCO, "Arroz", 60.0)
        val d = Dose("d1", base, TipoDose.ULTRARRAPIDA, "Fiasp", 6.0, refeicaoId = "r1")
        val antes = Medicao("m1", base.minusMinutes(10), 112, MomentoMedicao.PRE_REFEICAO)
        val depois = Medicao("m2", base.plusMinutes(120), 198, MomentoMedicao.POS_REFEICAO)
        val ep = Episodios.de(listOf(r), listOf(d), listOf(antes, depois)).single()
        assertEquals(86, ep.variacao); assertEquals(6.0, ep.bolus, 0.0)
    }

    @Test fun doseSemVinculoAindaEAssociadaPeloHorario() {
        val r = Refeicao("r1", base, TipoRefeicao.ALMOCO, "Arroz", 60.0)
        val d = Dose("d1", base.minusMinutes(10), TipoDose.ULTRARRAPIDA, "Fiasp", 6.0)
        assertEquals(listOf(d), Episodios.de(listOf(r), listOf(d), emptyList()).single().doses)
    }

    @Test fun padraoPorEtiquetaApareceQuandoADiferencaEGrande() {
        val ms = mutableListOf<Medicao>()
        for (dia in 0 until 10) {
            val doente = dia < 3
            repeat(4) { i -> ms += Medicao("m$dia-$i", base.plusDays(dia.toLong()).plusHours(i.toLong()), if (doente) 230 else 140, MomentoMedicao.ALEATORIA, tags = if (doente) listOf("Doença") else emptyList()) }
        }
        val padroes = AnalisadorPadroes(Dados(ms, emptyList(), emptyList(), emptyList(), Configuracao())).analisar()
        assertTrue(padroes.any { it.categoria == "Circunstâncias" && it.titulo.contains("Doença") })
    }

    // ---------- TACO e contagem de carboidratos ----------

    private val taco by lazy { glicemix.data.BaseAlimentos.deTsv(java.io.File("src/main/assets/taco.tsv").readText(Charsets.UTF_8)) }

    @Test fun tacoTem597AlimentosEValoresConhecidos() {
        assertEquals(597, taco.alimentos.size)
        assertEquals(13.6, taco.buscar("feijao carioca cozido").first().carboidratos100!!, 0.0)
        assertEquals(58.6, taco.buscar("pao frances").first().carboidratos100!!, 0.0)
    }

    @Test fun cozidoVemAntesDoCruEOCruTemAviso() {
        val nomes = taco.buscar("arroz").map { it.nome }
        assertTrue(nomes.indexOf("Arroz, tipo 1, cozido") < nomes.indexOf("Arroz, tipo 1, cru"))
        assertTrue(taco.buscar("arroz").first { it.nome == "Arroz, tipo 1, cru" }.aviso.startsWith("CRU"))
    }

    @Test fun semDadoNuncaViraZero() {
        val leite = taco.buscar("leite de vaca integral").first { it.nome == "Leite, de vaca, integral" }
        assertEquals(null, leite.carboidratos100)
        assertEquals(0.0, taco.buscar("oleo soja").first().carboidratos100!!, 0.0)
    }

    @Test fun itensSomamEViajamNoCsv() {
        val itens = listOf(ItemRefeicao("Arroz, tipo 1, cozido", 150.0, 28.1, Origem.TACO), ItemRefeicao("Biscoito X", 30.0, 70.0, Origem.ROTULO))
        assertEquals(63.2, itens.totalCarboidratos(), 0.0)
        val r = Refeicao("r", base, TipoRefeicao.ALMOCO, itens.resumo(), itens.totalCarboidratos(), itens = itens)
        assertEquals(r, Conversao.refeicao(Conversao.campos(r)))
    }

    @Test fun referenciaDoseExigeBaseEMostraResultado() {
        val cfg = Configuracao()
        fun ep(i: Int, carbs: Double, ui: Double, depois: Int): glicemix.analise.Episodio {
            val t = base.minusDays(i + 1L)
            val ref = Refeicao("r$i", t, TipoRefeicao.ALMOCO, "x", carbs)
            return glicemix.analise.Episodio(ref, listOf(Dose("d$i", t, TipoDose.ULTRARRAPIDA, "Fiasp", ui, refeicaoId = ref.id)), null,
                Medicao("p$i", t.plusMinutes(120), depois, MomentoMedicao.POS_REFEICAO))
        }
        val eps = listOf(ep(0, 60.0, 5.0, 150), ep(1, 55.0, 6.0, 170), ep(2, 65.0, 6.0, 65), ep(3, 120.0, 12.0, 140))
        val r = Episodios.referenciaDose(eps, TipoRefeicao.ALMOCO, 60.0, cfg, base)!!
        assertEquals(3, r.n); assertEquals(6.0, r.mediana, 0.0); assertEquals(1, r.abaixo)
        assertEquals(null, Episodios.referenciaDose(eps.take(2), TipoRefeicao.ALMOCO, 60.0, cfg, base))
    }
}
