package glicemix.analise

import glicemix.model.*
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.sqrt

/** Recorte temporal dos dados que todas as telas compartilham. */
enum class Periodo(val rotulo: String, val dias: Long?) {
    D7("Últimos 7 dias", 7), D14("Últimos 14 dias", 14), D30("Últimos 30 dias", 30),
    D90("Últimos 90 dias", 90), TUDO("Todo o histórico", null);

    override fun toString() = rotulo

    fun inicio(agora: LocalDateTime = LocalDateTime.now()): LocalDateTime =
        dias?.let { agora.toLocalDate().minusDays(it - 1).atStartOfDay() } ?: LocalDateTime.MIN
}

/** Conjunto de dados já filtrado para um período. */
class Dados(
    val medicoes: List<Medicao>,
    val refeicoes: List<Refeicao>,
    val doses: List<Dose>,
    val atividades: List<Atividade>,
    val config: Configuracao,
) {
    val vazio get() = medicoes.isEmpty() && refeicoes.isEmpty() && doses.isEmpty() && atividades.isEmpty()

    companion object {
        fun de(repo: glicemix.data.Repositorio, periodo: Periodo): Dados {
            val ini = periodo.inicio()
            return Dados(
                repo.medicoes.filter { it.dataHora >= ini },
                repo.refeicoes.filter { it.dataHora >= ini },
                repo.doses.filter { it.dataHora >= ini },
                repo.atividades.filter { it.dataHora >= ini },
                repo.configuracao,
            )
        }
    }
}

data class Resumo(
    val n: Int,
    val media: Double,
    val desvio: Double,
    val min: Int,
    val max: Int,
    /** Coeficiente de variação, em %. Meta do consenso: ≤ 36%. */
    val cv: Double,
    /** Glucose Management Indicator: estimativa da HbA1c a partir da média (Bergenstal et al., 2018). */
    val gmi: Double,
    val percentualPorFaixa: Map<Faixa, Double>,
    val diasComDados: Int,
    val medicoesPorDia: Double,
    val episodiosHipo: Int,
    val carboidratosPorDia: Double,
    val insulinaPorDia: Double,
    val minutosAtividadePorSemana: Double,
) {
    val tempoNoAlvo get() = percentualPorFaixa[Faixa.NO_ALVO] ?: 0.0
    val tempoAbaixo get() = (percentualPorFaixa[Faixa.BAIXA] ?: 0.0) + (percentualPorFaixa[Faixa.MUITO_BAIXA] ?: 0.0)
    val tempoAcima get() = (percentualPorFaixa[Faixa.ALTA] ?: 0.0) + (percentualPorFaixa[Faixa.MUITO_ALTA] ?: 0.0)
}

object Estatistica {

    fun media(v: List<Double>) = if (v.isEmpty()) 0.0 else v.sum() / v.size

    fun desvio(v: List<Double>): Double {
        if (v.size < 2) return 0.0
        val m = media(v)
        return sqrt(v.sumOf { (it - m) * (it - m) } / (v.size - 1))
    }

    /** Percentil por interpolação linear (p entre 0 e 100). */
    fun percentil(ordenados: List<Double>, p: Double): Double {
        if (ordenados.isEmpty()) return Double.NaN
        if (ordenados.size == 1) return ordenados[0]
        val pos = p / 100.0 * (ordenados.size - 1)
        val i = pos.toInt().coerceIn(0, ordenados.size - 2)
        val f = pos - i
        return ordenados[i] * (1 - f) + ordenados[i + 1] * f
    }

    data class Regressao(val inclinacao: Double, val intercepto: Double, val r: Double, val n: Int) {
        val r2 get() = r * r
    }

    fun regressao(x: List<Double>, y: List<Double>): Regressao? {
        val n = minOf(x.size, y.size)
        if (n < 3) return null
        val mx = media(x); val my = media(y)
        var sxy = 0.0; var sxx = 0.0; var syy = 0.0
        for (i in 0 until n) {
            sxy += (x[i] - mx) * (y[i] - my); sxx += (x[i] - mx) * (x[i] - mx); syy += (y[i] - my) * (y[i] - my)
        }
        if (sxx == 0.0 || syy == 0.0) return null
        val b = sxy / sxx
        return Regressao(b, my - b * mx, sxy / sqrt(sxx * syy), n)
    }

    fun gmi(mediaMgDl: Double) = 3.31 + 0.02392 * mediaMgDl

    fun resumo(d: Dados): Resumo? {
        val ms = d.medicoes
        if (ms.isEmpty()) return null
        val vals = ms.map { it.valor.toDouble() }
        val m = media(vals)
        val dp = desvio(vals)
        val porFaixa = Faixa.entries.associateWith { f -> ms.count { d.config.classificar(it.valor) == f } * 100.0 / ms.size }

        val diasRegistro = (ms.map { it.dataHora.toLocalDate() } + d.refeicoes.map { it.dataHora.toLocalDate() } +
            d.doses.map { it.dataHora.toLocalDate() }).toSet()
        val diasMed = ms.map { it.dataHora.toLocalDate() }.toSet().size
        val dias = diasRegistro.size.coerceAtLeast(1)
        val semanas = (spanDias(diasRegistro) / 7.0).coerceAtLeast(1.0)

        return Resumo(
            n = ms.size, media = m, desvio = dp,
            min = ms.minOf { it.valor }, max = ms.maxOf { it.valor },
            cv = if (m > 0) dp / m * 100 else 0.0,
            gmi = gmi(m),
            percentualPorFaixa = porFaixa,
            diasComDados = diasMed,
            medicoesPorDia = ms.size.toDouble() / diasMed.coerceAtLeast(1),
            episodiosHipo = episodiosHipo(ms, d.config).size,
            carboidratosPorDia = d.refeicoes.sumOf { it.carboidratos } / dias,
            insulinaPorDia = d.doses.filter { it.unidade.equals("UI", true) }.sumOf { it.quantidade } / dias,
            minutosAtividadePorSemana = d.atividades.sumOf { it.duracaoMin } / semanas,
        )
    }

    private fun spanDias(dias: Set<LocalDate>): Long =
        if (dias.isEmpty()) 1 else java.time.temporal.ChronoUnit.DAYS.between(dias.min(), dias.max()) + 1

    /**
     * Agrupa leituras abaixo do alvo que ocorrem com menos de 60 min de distância
     * num único episódio de hipoglicemia.
     */
    fun episodiosHipo(ms: List<Medicao>, cfg: Configuracao): List<List<Medicao>> {
        val baixas = ms.filter { it.valor < cfg.alvoMin }.sortedBy { it.dataHora }
        val episodios = mutableListOf<MutableList<Medicao>>()
        for (b in baixas) {
            val ult = episodios.lastOrNull()?.last()
            if (ult != null && java.time.Duration.between(ult.dataHora, b.dataHora).toMinutes() <= 60) episodios.last() += b
            else episodios += mutableListOf(b)
        }
        return episodios
    }

    /** Perfil glicêmico ambulatorial: percentis por hora do dia (0..23). */
    data class PontoPerfil(val hora: Int, val n: Int, val p10: Double, val p25: Double, val p50: Double, val p75: Double, val p90: Double)

    fun perfilDiario(ms: List<Medicao>): List<PontoPerfil> {
        // Usa uma janela de ±1h em torno de cada hora para suavizar dados capilares esparsos.
        return (0..23).mapNotNull { h ->
            val v = ms.filter {
                val dh = Math.floorMod(it.dataHora.hour - h + 12, 24) - 12
                dh in -1..1
            }.map { it.valor.toDouble() }.sorted()
            if (v.size < 3) null
            else PontoPerfil(h, v.size, percentil(v, 10.0), percentil(v, 25.0), percentil(v, 50.0), percentil(v, 75.0), percentil(v, 90.0))
        }
    }

    data class Dia(val data: LocalDate, val media: Double, val porFaixa: Map<Faixa, Double>, val n: Int)

    fun porDia(ms: List<Medicao>, cfg: Configuracao): List<Dia> =
        ms.groupBy { it.dataHora.toLocalDate() }.toSortedMap().map { (dia, lista) ->
            Dia(dia, lista.map { it.valor.toDouble() }.average(),
                Faixa.entries.associateWith { f -> lista.count { cfg.classificar(it.valor) == f } * 100.0 / lista.size },
                lista.size)
        }
}
