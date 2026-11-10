package glicemix.relatorio

import glicemix.analise.AnalisadorPadroes
import glicemix.analise.Dados
import glicemix.analise.Estatistica
import glicemix.analise.Nivel
import glicemix.analise.Turno
import glicemix.model.Configuracao
import glicemix.model.Faixa
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class Alinhamento { ESQ, CENTRO, DIR }

/**
 * Superfície de desenho mínima, em pontos tipográficos (A4 = 595 × 842).
 * O Android implementa sobre o PdfDocument; o desktop sobre Java2D (impressão/visualização).
 * Cores em ARGB. Em [texto], y é a linha de base.
 */
interface Superficie {
    fun texto(s: String, x: Float, y: Float, tamanho: Float, cor: Int, negrito: Boolean = false, alinhamento: Alinhamento = Alinhamento.ESQ)
    fun largura(s: String, tamanho: Float, negrito: Boolean = false): Float
    fun retangulo(x: Float, y: Float, w: Float, h: Float, cor: Int, raio: Float = 0f)
    fun linha(x1: Float, y1: Float, x2: Float, y2: Float, cor: Int, espessura: Float = 0.6f, tracejada: Boolean = false)
    fun poligono(xs: FloatArray, ys: FloatArray, cor: Int)
    fun polilinha(xs: FloatArray, ys: FloatArray, cor: Int, espessura: Float)
}

object CoresRelatorio {
    const val PRIMARIA = 0xFF0E7C86.toInt()
    const val PRIMARIA_ESCURA = 0xFF0A5C63.toInt()
    const val TEXTO = 0xFF1D2733.toInt()
    const val SUAVE = 0xFF667085.toInt()
    const val GRADE = 0xFFE3E8EF.toInt()
    const val FUNDO = 0xFFF3F5F8.toInt()
    const val BRANCO = 0xFFFFFFFF.toInt()
    const val ALVO_FUNDO = 0x221E9E61

    fun faixa(f: Faixa): Int = when (f) {
        Faixa.MUITO_BAIXA -> 0xFFA11B12.toInt()
        Faixa.BAIXA -> 0xFFE5483B.toInt()
        Faixa.NO_ALVO -> 0xFF1E9E61.toInt()
        Faixa.ALTA -> 0xFFF2A22C.toInt()
        Faixa.MUITO_ALTA -> 0xFFB8580E.toInt()
    }

    fun nivel(n: Nivel): Int = when (n) {
        Nivel.ALERTA -> 0xFFD92D20.toInt()
        Nivel.ATENCAO -> 0xFFF79009.toInt()
        Nivel.INFO -> 0xFF2E90FA.toInt()
        Nivel.POSITIVO -> 0xFF12B76A.toInt()
    }

    /** Mesma cor com outra opacidade (0..255). */
    fun alfa(cor: Int, a: Int): Int = (cor and 0x00FFFFFF) or (a shl 24)
}

/**
 * Monta o relatório para a consulta: indicadores, tempo nas faixas, perfil diário,
 * tempo no alvo por dia, tabelas de rotina e os padrões encontrados.
 * [novaPagina] é chamado a cada página e deve devolver uma superfície limpa.
 */
class GeradorRelatorio(
    private val d: Dados,
    private val descricaoPeriodo: String,
    private val agora: LocalDateTime = LocalDateTime.now(),
) {
    companion object {
        const val LARGURA = 595f
        const val ALTURA = 842f
        private const val M = 40f
        private const val CONTEUDO = LARGURA - 2 * M
    }

    private val cfg: Configuracao = d.config
    private val pt = Locale.forLanguageTag("pt-BR")
    private val fData = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private fun f1(x: Double) = String.format(pt, "%.1f", x)
    private fun f0(x: Double) = x.roundToInt().toString()

    private lateinit var s: Superficie
    private lateinit var fabrica: () -> Superficie
    private var y = 0f
    private var pagina = 0

    /** Desenha o relatório inteiro; retorna o número de páginas. */
    fun gerar(novaPagina: () -> Superficie): Int {
        fabrica = novaPagina
        pagina = 0
        abrirPagina()
        cabecalho()
        val r = Estatistica.resumo(d)
        if (r == null) {
            paragrafo("Não há medições de glicemia no período selecionado. Registre algumas medições e gere o relatório novamente.", 11f, CoresRelatorio.SUAVE)
            aviso()
            return pagina
        }
        indicadores(r)
        tempoNasFaixas(r.percentualPorFaixa)
        perfilDiario()
        tempoNoAlvoPorDia()
        tabelas(r)
        padroes()
        aviso()
        return pagina
    }

    // ---------- paginação ----------

    private fun abrirPagina() {
        s = fabrica()
        pagina++
        y = M
        rodape()
        if (pagina > 1) {
            s.texto("Glicemix · ${cfg.nome.ifBlank { "Relatório" }}", M, y + 8, 9f, CoresRelatorio.SUAVE, negrito = true)
            s.texto(descricaoPeriodo, LARGURA - M, y + 8, 9f, CoresRelatorio.SUAVE, alinhamento = Alinhamento.DIR)
            s.linha(M, y + 16, LARGURA - M, y + 16, CoresRelatorio.GRADE)
            y += 32
        }
    }

    private fun espaco(h: Float) {
        if (y + h > ALTURA - M - 18) abrirPagina()
    }

    private fun rodape() {
        val yr = ALTURA - 26
        s.linha(M, yr - 12, LARGURA - M, yr - 12, CoresRelatorio.GRADE)
        s.texto("Gerado pelo Glicemix em ${agora.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm"))} · informativo, não substitui orientação médica",
            M, yr, 7.5f, CoresRelatorio.SUAVE)
        s.texto("Página $pagina", LARGURA - M, yr, 7.5f, CoresRelatorio.SUAVE, alinhamento = Alinhamento.DIR)
    }

    // ---------- blocos ----------

    private fun cabecalho() {
        s.retangulo(0f, 0f, LARGURA, 96f, CoresRelatorio.PRIMARIA)
        s.texto("Glicemix", M, 44f, 24f, CoresRelatorio.BRANCO, negrito = true)
        s.texto("Relatório de controle glicêmico", M, 64f, 11.5f, CoresRelatorio.alfa(CoresRelatorio.BRANCO, 220))
        if (cfg.nome.isNotBlank()) s.texto(cfg.nome, LARGURA - M, 40f, 12f, CoresRelatorio.BRANCO, negrito = true, alinhamento = Alinhamento.DIR)
        val dias = d.medicoes.map { it.dataHora.toLocalDate() }
        val intervalo = if (dias.isEmpty()) descricaoPeriodo
        else "${dias.min().format(fData)} a ${dias.max().format(fData)} · ${dias.toSet().size} dias com medição"
        s.texto(intervalo, LARGURA - M, 58f, 9.5f, CoresRelatorio.BRANCO, alinhamento = Alinhamento.DIR)
        s.texto("Alvo: ${cfg.alvoMin}–${cfg.alvoMax} mg/dL", LARGURA - M, 72f, 9.5f, CoresRelatorio.alfa(CoresRelatorio.BRANCO, 220), alinhamento = Alinhamento.DIR)
        y = 96f + 26f
    }

    /** Título de seção; [junto] reserva espaço para o conteúdo, para o título não ficar sozinho no pé da página. */
    private fun titulo(t: String, sub: String? = null, junto: Float = 40f) {
        espaco((if (sub != null) 50f else 28f) + junto)
        s.texto(t, M, y + 12, 13f, CoresRelatorio.TEXTO, negrito = true)
        y += 18
        if (sub != null) {
            quebrar(sub, CONTEUDO, 8.5f).forEach { l -> s.texto(l, M, y + 9, 8.5f, CoresRelatorio.SUAVE); y += 11 }
        }
        y += 8
    }

    private fun indicadores(r: glicemix.analise.Resumo) {
        data class Kpi(val titulo: String, val valor: String, val unidade: String, val legenda: String, val cor: Int)
        val bom = CoresRelatorio.faixa(Faixa.NO_ALVO); val medio = CoresRelatorio.faixa(Faixa.ALTA); val ruim = CoresRelatorio.faixa(Faixa.BAIXA)
        val kpis = listOf(
            Kpi("MÉDIA", f0(r.media), "mg/dL", "desvio padrão ${f0(r.desvio)} · ${r.n} medições", CoresRelatorio.TEXTO),
            Kpi("HbA1c ESTIMADA (GMI)", f1(r.gmi), "%", "estimativa a partir da média", if (r.gmi < 7) bom else if (r.gmi < 8) medio else ruim),
            Kpi("TEMPO NO ALVO", f0(r.tempoNoAlvo), "%", "meta: acima de 70%", if (r.tempoNoAlvo >= 70) bom else if (r.tempoNoAlvo >= 50) medio else ruim),
            Kpi("VARIABILIDADE (CV)", f0(r.cv), "%", "meta: até 36%", if (r.cv <= 36) bom else medio),
            Kpi("HIPOGLICEMIAS", r.episodiosHipo.toString(), if (r.episodiosHipo == 1) "episódio" else "episódios",
                "${f1(r.tempoAbaixo)}% das leituras < ${cfg.alvoMin}", if (r.episodiosHipo == 0) bom else ruim),
            Kpi("MEDIÇÕES POR DIA", f1(r.medicoesPorDia), "", "faixa ${r.min}–${r.max} mg/dL", CoresRelatorio.TEXTO),
        )
        val gap = 10f; val w = (CONTEUDO - 2 * gap) / 3; val h = 60f
        espaco(2 * h + gap)
        kpis.forEachIndexed { i, k ->
            val x = M + (i % 3) * (w + gap); val yy = y + (i / 3) * (h + gap)
            s.retangulo(x, yy, w, h, CoresRelatorio.FUNDO, 6f)
            s.retangulo(x, yy + 12, 3f, h - 24, k.cor, 1.5f)
            s.texto(k.titulo, x + 12, yy + 16, 7f, CoresRelatorio.SUAVE, negrito = true)
            s.texto(k.valor, x + 12, yy + 39, 19f, k.cor, negrito = true)
            if (k.unidade.isNotEmpty()) s.texto(k.unidade, x + 15 + s.largura(k.valor, 19f, true), yy + 39, 8.5f, CoresRelatorio.SUAVE)
            s.texto(k.legenda, x + 12, yy + 52, 7.5f, CoresRelatorio.SUAVE)
        }
        y += 2 * h + gap + 22
    }

    private fun tempoNasFaixas(p: Map<Faixa, Double>) {
        titulo("Tempo nas faixas", junto = 64f, sub = "Metas do consenso internacional: mais de 70% no alvo, menos de 4% abaixo de ${cfg.alvoMin} e menos de 1% abaixo de ${cfg.hipoSevera} mg/dL.")
        espaco(60f)
        var x = M
        s.retangulo(M, y, CONTEUDO, 16f, CoresRelatorio.GRADE, 4f)
        Faixa.entries.forEach { f ->
            val w = CONTEUDO * ((p[f] ?: 0.0) / 100.0).toFloat()
            if (w > 0) s.retangulo(x, y, w, 16f, CoresRelatorio.faixa(f))
            x += w
        }
        y += 30
        val faixas = mapOf(
            Faixa.MUITO_BAIXA to "< ${cfg.hipoSevera}", Faixa.BAIXA to "${cfg.hipoSevera}–${cfg.alvoMin - 1}",
            Faixa.NO_ALVO to "${cfg.alvoMin}–${cfg.alvoMax}", Faixa.ALTA to "${cfg.alvoMax + 1}–${cfg.hiperSevera}", Faixa.MUITO_ALTA to "> ${cfg.hiperSevera}",
        )
        val col = CONTEUDO / 5
        Faixa.entries.forEachIndexed { i, f ->
            val cx = M + col * i
            s.retangulo(cx, y - 8, 8f, 8f, CoresRelatorio.faixa(f), 2f)
            s.texto("${f0(p[f] ?: 0.0)}%", cx + 12, y, 11f, CoresRelatorio.TEXTO, negrito = true)
            s.texto("${f.rotulo} · ${faixas[f]}", cx, y + 12, 7.5f, CoresRelatorio.SUAVE)
        }
        y += 34
    }

    private fun perfilDiario() {
        val perfil = Estatistica.perfilDiario(d.medicoes)
        if (perfil.size < 4) return
        titulo("Perfil glicêmico do dia (AGP)", junto = 200f, sub = "Todos os dias do período sobrepostos por horário. A linha é a mediana; as faixas mostram onde ficam 50% e 80% das leituras.")
        val h = 170f
        espaco(h + 30)
        val yMax = max(300.0, perfil.maxOf { it.p90 } + 20).coerceAtMost(450.0).toFloat()
        val ax = M + 28; val aw = CONTEUDO - 28; val ay = y; val ah = h
        fun px(hora: Float) = ax + hora / 24f * aw
        fun py(v: Float) = ay + ah - (v - 40f) / (yMax - 40f) * ah
        s.retangulo(ax, py(cfg.alvoMax.toFloat()), aw, py(cfg.alvoMin.toFloat()) - py(cfg.alvoMax.toFloat()), CoresRelatorio.ALVO_FUNDO)
        eixoY(ax, aw, listOf(70f, 100f, 140f, 180f, 250f, 300f, 400f).filter { it <= yMax }, ::py)
        for (hh in 0..24 step 3) {
            s.linha(px(hh.toFloat()), ay, px(hh.toFloat()), ay + ah, CoresRelatorio.GRADE, 0.4f)
            s.texto("${hh % 24}h", px(hh.toFloat()), ay + ah + 12, 7.5f, CoresRelatorio.SUAVE, alinhamento = Alinhamento.CENTRO)
        }
        fun banda(sup: (Estatistica.PontoPerfil) -> Double, inf: (Estatistica.PontoPerfil) -> Double, cor: Int) {
            val xs = perfil.map { px(it.hora + 0.5f) } + perfil.asReversed().map { px(it.hora + 0.5f) }
            val ys = perfil.map { py(sup(it).toFloat()) } + perfil.asReversed().map { py(inf(it).toFloat()) }
            s.poligono(xs.toFloatArray(), ys.toFloatArray(), cor)
        }
        banda({ it.p90 }, { it.p10 }, CoresRelatorio.alfa(CoresRelatorio.PRIMARIA, 45))
        banda({ it.p75 }, { it.p25 }, CoresRelatorio.alfa(CoresRelatorio.PRIMARIA, 100))
        s.polilinha(perfil.map { px(it.hora + 0.5f) }.toFloatArray(), perfil.map { py(it.p50.toFloat()) }.toFloatArray(), CoresRelatorio.PRIMARIA_ESCURA, 2f)
        s.linha(ax, py(cfg.alvoMin.toFloat()), ax + aw, py(cfg.alvoMin.toFloat()), CoresRelatorio.faixa(Faixa.NO_ALVO), 0.8f)
        s.linha(ax, py(cfg.alvoMax.toFloat()), ax + aw, py(cfg.alvoMax.toFloat()), CoresRelatorio.faixa(Faixa.NO_ALVO), 0.8f)
        y += h + 22
        legenda(listOf("Mediana" to CoresRelatorio.PRIMARIA_ESCURA, "50% das leituras" to CoresRelatorio.alfa(CoresRelatorio.PRIMARIA, 130),
            "80% das leituras" to CoresRelatorio.alfa(CoresRelatorio.PRIMARIA, 60), "Faixa-alvo" to CoresRelatorio.alfa(CoresRelatorio.faixa(Faixa.NO_ALVO), 90)))
        y += 18
    }

    private fun tempoNoAlvoPorDia() {
        val dias = Estatistica.porDia(d.medicoes, cfg)
        if (dias.size < 2) return
        titulo("Tempo nas faixas por dia", junto = 136f, sub = "Cada barra soma 100% das leituras do dia. A linha tracejada marca a meta de 70% no alvo.")
        val h = 110f
        espaco(h + 26)
        val ax = M + 28; val aw = CONTEUDO - 28; val ay = y
        fun py(v: Float) = ay + h - v / 100f * h
        eixoY(ax, aw, listOf(0f, 50f, 100f), ::py) { "${it.toInt()}%" }
        val w = aw / dias.size; val bw = min(w * 0.72f, 16f)
        val passo = max(1, (dias.size / 10.0).roundToInt())
        dias.forEachIndexed { i, dia ->
            val cx = ax + w * (i + 0.5f)
            var acc = 0f
            Faixa.entries.forEach { f ->
                val v = (dia.porFaixa[f] ?: 0.0).toFloat()
                if (v > 0) s.retangulo(cx - bw / 2, py(acc + v), bw, py(acc) - py(acc + v), CoresRelatorio.faixa(f))
                acc += v
            }
            if (i % passo == 0) s.texto(dia.data.format(DateTimeFormatter.ofPattern("dd/MM")), cx, ay + h + 11, 7f, CoresRelatorio.SUAVE, alinhamento = Alinhamento.CENTRO)
        }
        s.linha(ax, py(70f), ax + aw, py(70f), CoresRelatorio.TEXTO, 0.8f, tracejada = true)
        y += h + 30
    }

    private fun tabelas(r: glicemix.analise.Resumo) {
        val an = AnalisadorPadroes(d)
        // Médias por momento e por turno lado a lado
        val porMomento = d.medicoes.groupBy { it.momento }.toSortedMap().map { (m, l) ->
            val v = l.map { it.valor.toDouble() }.average()
            listOf(m.rotulo, l.size.toString(), f0(v)) to CoresRelatorio.faixa(cfg.classificar(v.roundToInt()))
        }
        val porTurno = d.medicoes.groupBy { Turno.de(it.dataHora) }.toSortedMap().map { (t, l) ->
            val v = l.map { it.valor.toDouble() }.average()
            listOf(t.rotulo, l.size.toString(), f0(v)) to CoresRelatorio.faixa(cfg.classificar(v.roundToInt()))
        }
        val meia = (CONTEUDO - 16) / 2
        val alturaTabelas = 18f + 16f * max(porMomento.size, porTurno.size)
        titulo("Médias por momento e por turno", junto = alturaTabelas)
        espaco(alturaTabelas)
        val y0 = y
        tabela(M, meia, listOf("Momento", "Medições", "Média"), listOf(0.56f, 0.22f, 0.22f), porMomento)
        val y1 = y; y = y0
        tabela(M + meia + 16, meia, listOf("Turno", "Medições", "Média"), listOf(0.56f, 0.22f, 0.22f), porTurno)
        y = max(y, y1) + 18

        // Refeições
        val resp = an.respostasRefeicoes()
        if (resp.isNotEmpty()) {
            titulo("Resposta às refeições", junto = 18f + 16f * 3, sub = "Pares de medição até 1h antes e entre 1h30 e 3h depois da refeição. A relação carboidrato/insulina é a praticada, não uma recomendação.")
            val linhas = resp.groupBy { it.refeicao.tipo }.toSortedMap().map { (t, l) ->
                val v = l.map { it.variacao.toDouble() }.average()
                val bolus = l.filter { it.bolus > 0 }
                val razao = if (bolus.isNotEmpty()) "1 UI : ${f0(bolus.sumOf { it.refeicao.carboidratos } / bolus.sumOf { it.bolus })} g" else "—"
                listOf(t.rotulo, l.size.toString(), "${f0(l.map { it.refeicao.carboidratos }.average())} g", razao, (if (v >= 0) "+" else "−") + f0(kotlin.math.abs(v)) + " mg/dL") to
                    (if (v > 50) CoresRelatorio.faixa(Faixa.ALTA) else CoresRelatorio.TEXTO)
            }
            espaco(18f + 16f * linhas.size)
            tabela(M, CONTEUDO, listOf("Refeição", "Pares", "Carboidrato médio", "Carb./insulina", "Variação 2h"), listOf(0.28f, 0.1f, 0.2f, 0.2f, 0.22f), linhas)
            y += 18
        }

        // Rotina
        titulo("Rotina no período", junto = 18f + 16f * 4)
        val rotina = listOf(
            listOf("Carboidratos registrados", "${f0(r.carboidratosPorDia)} g por dia"),
            listOf("Insulina registrada", "${f1(r.insulinaPorDia)} UI por dia"),
            listOf("Atividade física", "${f0(r.minutosAtividadePorSemana)} min por semana"),
            listOf("Glicemia mínima e máxima", "${r.min} e ${r.max} mg/dL"),
        ).map { it to CoresRelatorio.TEXTO }
        espaco(18f + 16f * rotina.size)
        tabela(M, CONTEUDO, listOf("Indicador", "Valor"), listOf(0.6f, 0.4f), rotina)
        y += 18
    }

    private fun padroes() {
        val lista = AnalisadorPadroes(d).analisar()
        if (lista.isEmpty()) return
        titulo("Padrões encontrados", junto = 80f, sub = "Observações automáticas feitas a partir dos registros. Servem como ponto de partida para a conversa com a equipe de saúde.")
        lista.forEach { p ->
            val larg = CONTEUDO - 16
            val desc = quebrar(p.descricao, larg, 9f)
            val ev = if (p.evidencia.isNotBlank()) quebrar(p.evidencia, larg, 8f) else emptyList()
            val h = 14f + 14f + desc.size * 11.5f + ev.size * 10f + (if (ev.isNotEmpty()) 4f else 0f) + 8f
            espaco(h)
            val cor = CoresRelatorio.nivel(p.nivel)
            s.retangulo(M, y, 3f, h - 8, cor, 1.5f)
            val x = M + 12
            s.texto("${p.nivel.rotulo.uppercase()} · ${p.categoria.uppercase()}", x, y + 9, 7f, cor, negrito = true)
            s.texto(p.titulo, x, y + 23, 10.5f, CoresRelatorio.TEXTO, negrito = true)
            var yy = y + 36
            desc.forEach { s.texto(it, x, yy, 9f, CoresRelatorio.TEXTO); yy += 11.5f }
            if (ev.isNotEmpty()) { yy += 3; ev.forEach { s.texto(it, x, yy, 8f, CoresRelatorio.SUAVE); yy += 10f } }
            y += h + 4
        }
    }

    private fun aviso() {
        y += 6
        paragrafo("Este relatório foi gerado automaticamente a partir dos registros feitos no Glicemix. A HbA1c estimada (GMI) é calculada pela média " +
            "das glicemias e pode diferir do exame de laboratório. Os padrões têm caráter informativo e não substituem a avaliação médica; " +
            "não altere doses de insulina ou medicamentos sem orientação da sua equipe de saúde.", 8f, CoresRelatorio.SUAVE)
    }

    // ---------- utilitários de desenho ----------

    private fun paragrafo(t: String, tam: Float, cor: Int) {
        val linhas = quebrar(t, CONTEUDO, tam)
        espaco(linhas.size * tam * 1.35f)
        linhas.forEach { y += tam * 1.35f; s.texto(it, M, y, tam, cor) }
        y += 6
    }

    private fun eixoY(ax: Float, aw: Float, valores: List<Float>, py: (Float) -> Float, fmt: (Float) -> String = { it.toInt().toString() }) {
        valores.forEach { v ->
            s.linha(ax, py(v), ax + aw, py(v), CoresRelatorio.GRADE, 0.5f)
            s.texto(fmt(v), ax - 5, py(v) + 2.5f, 7f, CoresRelatorio.SUAVE, alinhamento = Alinhamento.DIR)
        }
    }

    private fun legenda(itens: List<Pair<String, Int>>) {
        var x = M + 28
        itens.forEach { (t, c) ->
            s.retangulo(x, y - 7, 8f, 8f, c, 2f)
            s.texto(t, x + 12, y, 8f, CoresRelatorio.SUAVE)
            x += 12 + s.largura(t, 8f) + 18
        }
    }

    /** Tabela simples; a cor de cada linha vale para a última coluna. */
    private fun tabela(x: Float, largura: Float, cabecalho: List<String>, proporcoes: List<Float>, linhas: List<Pair<List<String>, Int>>) {
        val ws = proporcoes.map { it * largura }
        fun celulas(valores: List<String>, yy: Float, tam: Float, cor: Int, negrito: Boolean, corUltima: Int = cor) {
            var cx = x
            valores.forEachIndexed { i, v ->
                val ultima = i == valores.lastIndex
                val texto = cortar(v, ws[i] - 12, tam, negrito)
                if (i == 0) s.texto(texto, cx + 6, yy, tam, cor, negrito)
                else s.texto(texto, cx + ws[i] - 6, yy, tam, if (ultima) corUltima else cor, negrito || ultima, Alinhamento.DIR)
                cx += ws[i]
            }
        }
        s.retangulo(x, y, largura, 18f, CoresRelatorio.FUNDO, 3f)
        celulas(cabecalho, y + 12, 7.5f, CoresRelatorio.SUAVE, true)
        y += 18
        linhas.forEachIndexed { i, (valores, cor) ->
            celulas(valores, y + 11.5f, 8.5f, CoresRelatorio.TEXTO, false, cor)
            y += 16
            if (i < linhas.lastIndex) s.linha(x + 6, y, x + largura - 6, y, CoresRelatorio.GRADE, 0.4f)
        }
    }

    private fun cortar(t: String, largura: Float, tam: Float, negrito: Boolean): String {
        if (s.largura(t, tam, negrito) <= largura) return t
        var r = t
        while (r.length > 1 && s.largura("$r…", tam, negrito) > largura) r = r.dropLast(1)
        return "$r…"
    }

    private fun quebrar(texto: String, largura: Float, tam: Float, negrito: Boolean = false): List<String> {
        val out = mutableListOf<String>()
        texto.split("\n").forEach { paragrafo ->
            var linha = ""
            paragrafo.split(" ").filter { it.isNotEmpty() }.forEach { p ->
                val tentativa = if (linha.isEmpty()) p else "$linha $p"
                if (linha.isEmpty() || s.largura(tentativa, tam, negrito) <= largura) linha = tentativa
                else { out += linha; linha = p }
            }
            out += linha
        }
        return out
    }
}

/**
 * Grava as páginas como listas de comandos para reproduzir depois numa superfície real
 * (usado na impressão do desktop, que pede as páginas fora de ordem e mais de uma vez).
 */
class PaginasGravadas(private val medir: (String, Float, Boolean) -> Float) {
    private val paginas = mutableListOf<MutableList<(Superficie) -> Unit>>()
    val total get() = paginas.size

    fun nova(): Superficie {
        val ops = mutableListOf<(Superficie) -> Unit>()
        paginas += ops
        return object : Superficie {
            override fun texto(s: String, x: Float, y: Float, tamanho: Float, cor: Int, negrito: Boolean, alinhamento: Alinhamento) { ops += { it.texto(s, x, y, tamanho, cor, negrito, alinhamento) } }
            override fun largura(s: String, tamanho: Float, negrito: Boolean) = medir(s, tamanho, negrito)
            override fun retangulo(x: Float, y: Float, w: Float, h: Float, cor: Int, raio: Float) { ops += { it.retangulo(x, y, w, h, cor, raio) } }
            override fun linha(x1: Float, y1: Float, x2: Float, y2: Float, cor: Int, espessura: Float, tracejada: Boolean) { ops += { it.linha(x1, y1, x2, y2, cor, espessura, tracejada) } }
            override fun poligono(xs: FloatArray, ys: FloatArray, cor: Int) { ops += { it.poligono(xs, ys, cor) } }
            override fun polilinha(xs: FloatArray, ys: FloatArray, cor: Int, espessura: Float) { ops += { it.polilinha(xs, ys, cor, espessura) } }
        }
    }

    fun reproduzir(indice: Int, alvo: Superficie) = paginas[indice].forEach { it(alvo) }
}
