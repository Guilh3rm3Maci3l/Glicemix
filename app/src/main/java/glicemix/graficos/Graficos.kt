package glicemix.graficos

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import glicemix.analise.Dados
import glicemix.analise.Estatistica
import glicemix.telas.Cores
import glicemix.telas.fDiaHora
import glicemix.telas.num
import glicemix.telas.ptBR
import glicemix.model.Configuracao
import glicemix.model.Faixa
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val fDiaMes = DateTimeFormatter.ofPattern("dd/MM")
private val fSemana = DateTimeFormatter.ofPattern("EEE", ptBR)

// ------------------------------------------------------------------
// Linha do tempo
// ------------------------------------------------------------------

/** Glicemia ao longo do tempo, faixa-alvo e trilha de eventos (refeições, doses, atividades). */
@Composable
fun GraficoLinhaTempo(d: Dados, inicio: LocalDateTime, fim: LocalDateTime, modifier: Modifier = Modifier, altura: Dp = 250.dp) {
    val cfg = d.config
    val ms = d.medicoes.filter { it.dataHora >= inicio }
    if (ms.isEmpty()) { SemDados(altura); return }
    val rs = d.refeicoes.filter { it.dataHora >= inicio }
    val ds = d.doses.filter { it.dataHora >= inicio }
    val ats = d.atividades.filter { it.dataHora >= inicio }
    val c = coresGrafico()
    val total = Duration.between(inicio, fim).toMinutes().toFloat().coerceAtLeast(60f)
    val yMax = max(300f, ms.maxOf { it.valor } + 20f).coerceAtMost(450f)
    val dias = ChronoUnit.DAYS.between(inicio.toLocalDate(), fim.toLocalDate()) + 1
    fun t(x: LocalDateTime) = Duration.between(inicio, x).toMinutes().toFloat()
    fun Density.area(s: Size) = Rect(32.dp.toPx(), 8.dp.toPx(), s.width - 4.dp.toPx(), s.height - 60.dp.toPx())

    MolduraGrafico(
        altura, chave = Triple(ms, inicio, cfg), modifier = modifier,
        aoTocar = { p, s ->
            val a = area(s)
            val x = Escala(0f, total, a.left, a.right); val y = Escala(40f, yMax, a.bottom, a.top)
            val lim = 26.dp.toPx()
            if (p.y <= a.bottom + 4.dp.toPx()) {
                ms.minByOrNull { val dx = x(t(it.dataHora)) - p.x; val dy = y(it.valor.toFloat().coerceAtMost(yMax)) - p.y; dx * dx + dy * dy }
                    ?.takeIf { val dx = x(t(it.dataHora)) - p.x; val dy = y(it.valor.toFloat().coerceAtMost(yMax)) - p.y; sqrt(dx * dx + dy * dy) < lim }
                    ?.let { "${it.valor} mg/dL · ${it.momento.rotulo} · ${it.dataHora.format(fDiaHora)}" + if (it.observacao.isNotBlank()) " · ${it.observacao}" else "" }
            } else {
                val eventos = rs.map { x(t(it.dataHora)) to "${it.tipo.rotulo}: ${num(it.carboidratos)} g · ${it.descricao} · ${it.dataHora.format(fDiaHora)}" } +
                    ds.map { x(t(it.dataHora)) to "${num(it.quantidade)} ${it.unidade} ${it.medicamento} (${it.tipo.rotulo}) · ${it.dataHora.format(fDiaHora)}" } +
                    ats.map { x(t(it.dataHora)) to "${it.tipo} · ${it.duracaoMin} min · ${it.intensidade.rotulo} · ${it.dataHora.format(fDiaHora)}" }
                eventos.minByOrNull { abs(it.first - p.x) }?.takeIf { abs(it.first - p.x) < lim }?.second
            }
        },
    ) { m ->
        val a = area(size)
        val x = Escala(0f, total, a.left, a.right); val y = Escala(40f, yMax, a.bottom, a.top)
        val baseTrilha = a.bottom + 8.dp.toPx()
        val fimTrilha = size.height - 20.dp.toPx()

        // fins de semana
        var dia = inicio.toLocalDate()
        while (!dia.isAfter(fim.toLocalDate())) {
            if (dia.dayOfWeek == DayOfWeek.SATURDAY || dia.dayOfWeek == DayOfWeek.SUNDAY) {
                val xa = x(t(dia.atStartOfDay())).coerceAtLeast(a.left); val xb = x(t(dia.plusDays(1).atStartOfDay())).coerceAtMost(a.right)
                if (xb > xa) drawRect(c.sombra, Offset(xa, a.top), Size(xb - xa, fimTrilha - a.top))
            }
            dia = dia.plusDays(1)
        }
        // faixa-alvo e limites
        drawRect(Cores.alvo.copy(alpha = 0.10f), Offset(a.left, y(cfg.alvoMax.toFloat())), Size(a.width, y(cfg.alvoMin.toFloat()) - y(cfg.alvoMax.toFloat())))
        gradeY(m, a, y, marcas(40f, yMax, 5), c)
        drawLine(Cores.alvo.copy(alpha = 0.6f), Offset(a.left, y(cfg.alvoMin.toFloat())), Offset(a.right, y(cfg.alvoMin.toFloat())), 1.dp.toPx())
        drawLine(Cores.alvo.copy(alpha = 0.6f), Offset(a.left, y(cfg.alvoMax.toFloat())), Offset(a.right, y(cfg.alvoMax.toFloat())), 1.dp.toPx())
        tracejada(Cores.faixa(Faixa.MUITO_BAIXA).copy(alpha = 0.6f), Offset(a.left, y(cfg.hipoSevera.toFloat())), Offset(a.right, y(cfg.hipoSevera.toFloat())))

        // eixo X
        val yRot = size.height - 8.dp.toPx()
        if (dias <= 2) {
            var h = inicio.withMinute(0).withSecond(0).withNano(0)
            while (!h.isAfter(fim)) {
                if (h.hour % 6 == 0 && !h.isBefore(inicio)) {
                    val px = x(t(h))
                    drawLine(c.grade, Offset(px, a.top), Offset(px, a.bottom), 1f)
                    texto(m, if (h.hour == 0) h.format(fDiaMes) else "${h.hour}h", px, yRot, c.texto)
                }
                h = h.plusHours(1)
            }
        } else {
            val passo = when { dias <= 7 -> 1; dias <= 14 -> 2; dias <= 31 -> 7; else -> 14 }
            var d0 = inicio.toLocalDate(); var i = 0
            while (!d0.isAfter(fim.toLocalDate())) {
                val px = x(t(d0.atStartOfDay()))
                if (px >= a.left) drawLine(c.grade, Offset(px, a.top), Offset(px, fimTrilha), 1f)
                if (i % passo == 0) texto(m, if (dias <= 7) d0.format(fSemana) else d0.format(fDiaMes), x(t(d0.atTime(12, 0))), yRot, c.texto)
                d0 = d0.plusDays(1); i++
            }
        }

        // linha e pontos
        val pts = ms.map { Offset(x(t(it.dataHora)), y(it.valor.toFloat().coerceAtMost(yMax))) }
        for (i in 1 until ms.size) {
            if (Duration.between(ms[i - 1].dataHora, ms[i].dataHora).toHours() < 8)
                drawLine(c.linha, pts[i - 1], pts[i], 1.2.dp.toPx(), cap = StrokeCap.Round)
        }
        val r = if (ms.size > 120) 2.4.dp.toPx() else 3.4.dp.toPx()
        ms.forEachIndexed { i, mm ->
            drawCircle(c.fundo, r + 1.dp.toPx(), pts[i])
            drawCircle(Cores.faixa(cfg.classificar(mm.valor)), r, pts[i])
        }

        // trilha de eventos
        drawLine(c.grade, Offset(a.left, baseTrilha - 3.dp.toPx()), Offset(a.right, baseTrilha - 3.dp.toPx()), 1f)
        val yRef = baseTrilha + 7.dp.toPx(); val yDose = baseTrilha + 19.dp.toPx(); val yAtv = baseTrilha + 29.dp.toPx()
        rs.forEach { rf ->
            val px = x(t(rf.dataHora)); val s = (3.5f + sqrt(rf.carboidratos.toFloat()).coerceAtMost(10f) * 0.4f).dp.toPx()
            val p = Path().apply { moveTo(px, yRef - s); lineTo(px + s, yRef + s * 0.7f); lineTo(px - s, yRef + s * 0.7f); close() }
            drawPath(p, Cores.refeicao)
        }
        ds.forEach { dd ->
            val px = x(t(dd.dataHora))
            if (dd.tipo.ehBolus) drawCircle(Cores.dose, 3.dp.toPx(), Offset(px, yDose))
            else {
                val s = 4.dp.toPx()
                drawPath(Path().apply { moveTo(px, yDose - s); lineTo(px + s, yDose); lineTo(px, yDose + s); lineTo(px - s, yDose); close() }, Cores.basal)
            }
        }
        ats.forEach { at ->
            val xa = x(t(at.dataHora)); val xb = max(x(t(at.fim)), xa + 3.dp.toPx())
            drawRoundRect(Cores.atividade, Offset(xa, yAtv - 3.dp.toPx()), Size(xb - xa, 6.dp.toPx()), CornerRadius(3.dp.toPx()))
        }
    }
}

// ------------------------------------------------------------------
// Perfil diário (AGP)
// ------------------------------------------------------------------

@Composable
fun GraficoPerfilDiario(perfil: List<Estatistica.PontoPerfil>, cfg: Configuracao, modifier: Modifier = Modifier, altura: Dp = 240.dp) {
    if (perfil.size < 4) { SemDados(altura); return }
    val c = coresGrafico()
    val yMax = max(300f, perfil.maxOf { it.p90.toFloat() } + 20f).coerceAtMost(450f)
    fun Density.area(s: Size) = Rect(32.dp.toPx(), 8.dp.toPx(), s.width - 6.dp.toPx(), s.height - 22.dp.toPx())
    val cor = Cores.primaria
    MolduraGrafico(
        altura, chave = perfil, modifier = modifier,
        aoTocar = { p, s ->
            val a = area(s)
            val h = Escala(0f, 24f, a.left, a.right).inversa(p.x).toInt()
            perfil.firstOrNull { it.hora == h }?.let {
                "${it.hora}h–${it.hora + 1}h · mediana ${it.p50.roundToInt()} · metade entre ${it.p25.roundToInt()} e ${it.p75.roundToInt()} · 80% entre ${it.p10.roundToInt()} e ${it.p90.roundToInt()} mg/dL (${it.n} leituras)"
            }
        },
    ) { m ->
        val a = area(size)
        val x = Escala(0f, 24f, a.left, a.right); val y = Escala(40f, yMax, a.bottom, a.top)
        drawRect(Cores.alvo.copy(alpha = 0.10f), Offset(a.left, y(cfg.alvoMax.toFloat())), Size(a.width, y(cfg.alvoMin.toFloat()) - y(cfg.alvoMax.toFloat())))
        gradeY(m, a, y, marcas(40f, yMax, 5), c)
        for (h in 0..24 step 6) {
            drawLine(c.grade, Offset(x(h.toFloat()), a.top), Offset(x(h.toFloat()), a.bottom), 1f)
            texto(m, "${h % 24}h", x(h.toFloat()), size.height - 8.dp.toPx(), c.texto)
        }
        fun faixa(sup: (Estatistica.PontoPerfil) -> Double, inf: (Estatistica.PontoPerfil) -> Double, alfa: Float) {
            val path = Path()
            perfil.forEachIndexed { i, pt -> val px = x(pt.hora + 0.5f); val py = y(sup(pt).toFloat()); if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }
            perfil.asReversed().forEach { pt -> path.lineTo(x(pt.hora + 0.5f), y(inf(pt).toFloat())) }
            path.close()
            drawPath(path, cor.copy(alpha = alfa))
        }
        faixa({ it.p90 }, { it.p10 }, 0.16f)
        faixa({ it.p75 }, { it.p25 }, 0.36f)
        val med = Path()
        perfil.forEachIndexed { i, pt -> val px = x(pt.hora + 0.5f); val py = y(pt.p50.toFloat()); if (i == 0) med.moveTo(px, py) else med.lineTo(px, py) }
        drawPath(med, Color(0xFF0A5C63), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
    }
}

// ------------------------------------------------------------------
// Tempo nas faixas por dia
// ------------------------------------------------------------------

@Composable
fun GraficoTempoNoAlvoDiario(dias: List<Estatistica.Dia>, modifier: Modifier = Modifier, altura: Dp = 220.dp) {
    if (dias.isEmpty()) { SemDados(altura); return }
    val c = coresGrafico()
    fun Density.area(s: Size) = Rect(34.dp.toPx(), 8.dp.toPx(), s.width - 4.dp.toPx(), s.height - 22.dp.toPx())
    MolduraGrafico(
        altura, chave = dias, modifier = modifier,
        aoTocar = { p, s ->
            val a = area(s)
            val i = ((p.x - a.left) / (a.width / dias.size)).toInt()
            dias.getOrNull(i)?.let { d ->
                d.data.format(DateTimeFormatter.ofPattern("EEE dd/MM", ptBR)) + " · média ${d.media.roundToInt()} mg/dL · " +
                    Faixa.entries.reversed().filter { (d.porFaixa[it] ?: 0.0) > 0 }.joinToString(" · ") { "${it.rotulo} ${(d.porFaixa[it] ?: 0.0).roundToInt()}%" }
            }
        },
    ) { m ->
        val a = area(size)
        val y = Escala(0f, 100f, a.bottom, a.top)
        gradeY(m, a, y, listOf(0f, 50f, 70f, 100f), c) { "${it.toInt()}%" }
        tracejada(Cores.alvo, Offset(a.left, y(70f)), Offset(a.right, y(70f)))
        val w = a.width / dias.size; val bw = min(w * 0.7f, 22.dp.toPx())
        val passo = max(1, (dias.size / 6.0).roundToInt())
        dias.forEachIndexed { i, d ->
            val cx = a.left + w * (i + 0.5f)
            var acc = 0f
            Faixa.entries.forEach { f ->
                val v = (d.porFaixa[f] ?: 0.0).toFloat()
                if (v > 0) { drawRect(Cores.faixa(f), Offset(cx - bw / 2, y(acc + v)), Size(bw, y(acc) - y(acc + v))); acc += v }
            }
            if (i % passo == 0) texto(m, d.data.format(fDiaMes), cx, size.height - 8.dp.toPx(), c.texto)
        }
    }
}

// ------------------------------------------------------------------
// Histograma
// ------------------------------------------------------------------

@Composable
fun GraficoHistograma(valores: List<Int>, cfg: Configuracao, modifier: Modifier = Modifier, altura: Dp = 220.dp) {
    if (valores.size < 3) { SemDados(altura); return }
    val c = coresGrafico()
    val ini = (valores.min() / 10) * 10; val fim = (valores.max() / 10 + 1) * 10
    val caixas = (ini until fim step 10).map { b -> b to valores.count { it >= b && it < b + 10 } }
    val maxC = caixas.maxOf { it.second }.toFloat()
    fun Density.area(s: Size) = Rect(30.dp.toPx(), 8.dp.toPx(), s.width - 6.dp.toPx(), s.height - 22.dp.toPx())
    MolduraGrafico(
        altura, chave = valores, modifier = modifier,
        aoTocar = { p, s ->
            val a = area(s)
            val v = Escala(ini.toFloat(), fim.toFloat(), a.left, a.right).inversa(p.x)
            caixas.firstOrNull { v >= it.first && v < it.first + 10 }?.let { (b, n) ->
                "$b–${b + 9} mg/dL · $n leitura(s) · ${(n * 100.0 / valores.size).roundToInt()}%"
            }
        },
    ) { m ->
        val a = area(size)
        val x = Escala(ini.toFloat(), fim.toFloat(), a.left, a.right); val y = Escala(0f, maxC * 1.1f, a.bottom, a.top)
        gradeY(m, a, y, marcas(0f, maxC * 1.1f, 4).filter { it % 1f == 0f }, c)
        caixas.forEach { (b, n) ->
            if (n > 0) drawRect(Cores.faixa(cfg.classificar(b + 5)), Offset(x(b.toFloat()) + 1f, y(n.toFloat())), Size(x(b + 10f) - x(b.toFloat()) - 2f, y(0f) - y(n.toFloat())))
        }
        marcas(ini.toFloat(), fim.toFloat(), 6).forEach { texto(m, it.toInt().toString(), x(it), size.height - 8.dp.toPx(), c.texto) }
        listOf(cfg.alvoMin, cfg.alvoMax).filter { it in ini..fim }.forEach {
            tracejada(c.texto, Offset(x(it.toFloat()), a.top), Offset(x(it.toFloat()), a.bottom))
        }
    }
}

// ------------------------------------------------------------------
// Barras horizontais
// ------------------------------------------------------------------

data class ItemBarra(val rotulo: String, val valor: Double, val cor: Color, val detalhe: String = "")

@Composable
fun GraficoBarras(itens: List<ItemBarra>, modifier: Modifier = Modifier, unidade: String = "mg/dL", comSinal: Boolean = false) {
    if (itens.isEmpty()) { SemDados(120.dp); return }
    val c = coresGrafico()
    val altura = (itens.size * 38 + 8).dp
    val minV = min(0.0, itens.minOf { it.valor }).toFloat(); val maxV = max(0.0, itens.maxOf { it.valor }).toFloat()
    val toque: (Density.(Offset, Size) -> String?)? =
        if (itens.none { it.detalhe.isNotEmpty() }) null
        else ({ p, s ->
            val i = ((p.y - 4.dp.toPx()) / ((s.height - 8.dp.toPx()) / itens.size)).toInt()
            itens.getOrNull(i)?.let { "${it.rotulo}: ${it.detalhe}" }
        })
    MolduraGrafico(altura, chave = itens, modifier = modifier, aoTocar = toque) { m ->
        val estilo = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
        val larg = itens.maxOf { m.measure(it.rotulo, estilo).size.width } + 10.dp.toPx()
        val folgaEsq = if (minV < 0) 64.dp.toPx() else 0f; val folgaDir = if (maxV > 0) 64.dp.toPx() else 6.dp.toPx()
        val a = Rect(larg + folgaEsq, 4.dp.toPx(), size.width - folgaDir, size.height - 4.dp.toPx())
        val x = Escala(if (minV < 0) minV else 0f, if (maxV > 0) maxV else 0f, a.left, a.right)
        val h = a.height / itens.size; val bh = min(h * 0.6f, 22.dp.toPx())
        drawLine(c.grade, Offset(x(0f), a.top), Offset(x(0f), a.bottom), 1f)
        itens.forEachIndexed { i, it ->
            val cy = a.top + h * (i + 0.5f)
            val xa = min(x(0f), x(it.valor.toFloat())); val xb = max(x(0f), x(it.valor.toFloat()))
            drawRoundRect(it.cor, Offset(xa, cy - bh / 2), Size(max(xb - xa, 2f), bh), CornerRadius(5.dp.toPx()))
            texto(m, it.rotulo, larg - 8.dp.toPx(), cy, c.texto, Alinha.DIR, 12.sp)
            val v = (if (comSinal && it.valor > 0) "+" else "") + it.valor.roundToInt() + " " + unidade
            if (it.valor >= 0) texto(m, v, xb + 5.dp.toPx(), cy, c.texto, Alinha.ESQ, 11.sp, FontWeight.Bold)
            else texto(m, v, xa - 5.dp.toPx(), cy, c.texto, Alinha.DIR, 11.sp, FontWeight.Bold)
        }
    }
}

// ------------------------------------------------------------------
// Dispersão com regressão
// ------------------------------------------------------------------

data class PontoDispersao(val x: Double, val y: Double, val detalhe: String)

@Composable
fun GraficoDispersao(pontos: List<PontoDispersao>, rotuloX: String, modifier: Modifier = Modifier, altura: Dp = 220.dp) {
    if (pontos.size < 3) { SemDados(altura); return }
    val c = coresGrafico()
    val xMax = (pontos.maxOf { it.x } * 1.1).toFloat()
    val yMin = (min(0.0, pontos.minOf { it.y }) - 10).toFloat(); val yMaxV = (pontos.maxOf { it.y } + 10).toFloat()
    val reg = Estatistica.regressao(pontos.map { it.x }, pontos.map { it.y })
    fun Density.area(s: Size) = Rect(34.dp.toPx(), 8.dp.toPx(), s.width - 8.dp.toPx(), s.height - 36.dp.toPx())
    MolduraGrafico(
        altura, chave = pontos, modifier = modifier,
        aoTocar = { p, s ->
            val a = area(s)
            val x = Escala(0f, xMax, a.left, a.right); val y = Escala(yMin, yMaxV, a.bottom, a.top)
            pontos.minByOrNull { val dx = x(it.x.toFloat()) - p.x; val dy = y(it.y.toFloat()) - p.y; dx * dx + dy * dy }
                ?.takeIf { val dx = x(it.x.toFloat()) - p.x; val dy = y(it.y.toFloat()) - p.y; sqrt(dx * dx + dy * dy) < 24.dp.toPx() }?.detalhe
        },
    ) { m ->
        val a = area(size)
        val x = Escala(0f, xMax, a.left, a.right); val y = Escala(yMin, yMaxV, a.bottom, a.top)
        gradeY(m, a, y, marcas(yMin, yMaxV, 4), c)
        drawLine(c.linha, Offset(a.left, y(0f)), Offset(a.right, y(0f)), 1f)
        marcas(0f, xMax, 5).forEach { texto(m, it.toInt().toString(), x(it), a.bottom + 10.dp.toPx(), c.texto) }
        texto(m, rotuloX, a.center.x, size.height - 8.dp.toPx(), c.texto)
        reg?.let { r ->
            tracejada(Color(0xFF0A5C63), Offset(x(0f), y(r.intercepto.toFloat())), Offset(x(xMax), y((r.intercepto + r.inclinacao * xMax).toFloat())))
        }
        pontos.forEach { drawCircle(Cores.refeicao.copy(alpha = 0.6f), 4.5.dp.toPx(), Offset(x(it.x.toFloat()), y(it.y.toFloat()))) }
    }
}

// ------------------------------------------------------------------
// Barra horizontal do tempo nas faixas (painel)
// ------------------------------------------------------------------

@Composable
fun BarraTempoNoAlvo(porFaixa: Map<Faixa, Double>, modifier: Modifier = Modifier) {
    if (porFaixa.values.sum() <= 0) { SemDados(60.dp); return }
    Column(modifier.fillMaxWidth()) {
        MolduraGrafico(22.dp, chave = porFaixa, modifier = Modifier.clip(RoundedCornerShape(6.dp))) { _ ->
            var acc = 0f
            Faixa.entries.forEach { f ->
                val w = size.width * ((porFaixa[f] ?: 0.0) / 100.0).toFloat()
                if (w > 0) drawRect(Cores.faixa(f), Offset(acc, 0f), Size(w, size.height))
                acc += w
            }
        }
    }
}
