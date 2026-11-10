package glicemix.data

import glicemix.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Gera um mês de registros fictícios, porém plausíveis, para explorar o app.
 * Os dados incluem de propósito alguns padrões (alvorecer, pico no café da manhã,
 * efeito do exercício, fins de semana mais altos) para demonstrar a análise.
 */
object DadosExemplo {

    fun gerar(dias: Int = 30, hoje: LocalDate = LocalDate.now(), semente: Int = 42): List<Registro> {
        val rnd = Random(semente)
        val out = mutableListOf<Registro>()
        fun ruido(dp: Number): Double = (rnd.nextDouble() + rnd.nextDouble() + rnd.nextDouble() - 1.5) * 2 * dp.toDouble()
        fun t(dia: LocalDate, h: Int, m: Int, varMin: Int = 15) =
            LocalDateTime.of(dia, LocalTime.of(h, m)).plusMinutes(rnd.nextInt(-varMin, varMin + 1).toLong())
        fun med(dt: LocalDateTime, v: Double, mom: MomentoMedicao, obs: String = "") {
            out += Medicao(dataHora = dt, valor = v.roundToInt().coerceIn(40, 450), momento = mom, observacao = obs)
        }

        for (i in dias - 1 downTo 0) {
            val dia = hoje.minusDays(i.toLong())
            val progresso = 1 - i.toDouble() / dias          // 0 → 1 ao longo do mês
            val fds = dia.dayOfWeek == DayOfWeek.SATURDAY || dia.dayOfWeek == DayOfWeek.SUNDAY
            val vies = 12 - 18 * progresso + (if (fds) 28 else 0)
            val treino = dia.dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            val treinoOntem = dia.minusDays(1).dayOfWeek in setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            val ultimoDia = i == 0
            val agora = LocalDateTime.now()
            fun antesDeAgora(dt: LocalDateTime) = !ultimoDia || dt.isBefore(agora)

            // Madrugada (alguns dias) e jejum com fenômeno do alvorecer
            val madr = 98 + ruido(12) + vies * 0.4
            if (i % 3 == 0 || (treinoOntem && rnd.nextDouble() < 0.5)) {
                val hipoTardia = treinoOntem && rnd.nextDouble() < 0.55
                val v = if (hipoTardia) 58 + ruido(5) else madr
                med(t(dia, 3, 0, 20), v, MomentoMedicao.MADRUGADA, if (hipoTardia) "Acordei suado" else "")
            }
            val jejum = madr + 32 + ruido(10)
            val tJejum = t(dia, 7, 0, 20)
            if (antesDeAgora(tJejum)) med(tJejum, jejum, MomentoMedicao.JEJUM)

            // Café: subdosado de propósito → maior pico do dia
            refeicao(out, rnd, dia, 7, 20, TipoRefeicao.CAFE_DA_MANHA, listOf("Pão francês com ovo e café" to 42.0, "Tapioca com queijo" to 48.0, "Iogurte com granola e banana" to 55.0, "Cuscuz com ovo" to 50.0),
                razao = 16.0, pre = jejum, k = 3.8, ruido = ::ruido, antesDeAgora = ::antesDeAgora, t = { h, m -> t(dia, h, m, 10) })

            // Almoço
            val preAlmoco = 118 + ruido(20) + vies
            refeicao(out, rnd, dia, 12, 30, TipoRefeicao.ALMOCO, listOf("Arroz, feijão, frango e salada" to 70.0, "Macarrão à bolonhesa" to 85.0, "Arroz, feijão, carne e legumes" to 75.0, "Salada com peixe e batata" to 45.0),
                razao = 12.0, pre = preAlmoco, k = 3.4, ruido = ::ruido, antesDeAgora = ::antesDeAgora, t = { h, m -> t(dia, h, m, 15) }, medirAntes = true)

            // Lanche da tarde + eventual hipo antes
            if (rnd.nextDouble() < 0.6) {
                val dt = t(dia, 16, 0, 20)
                if (antesDeAgora(dt)) out += Refeicao(dataHora = dt, tipo = TipoRefeicao.LANCHE_TARDE, descricao = listOf("Fruta", "Torrada integral", "Barra de cereal").random(rnd), carboidratos = listOf(15.0, 20.0, 25.0).random(rnd))
            }

            // Treino às 17h30 seg/qua/sex
            if (treino) {
                val ini = t(dia, 17, 30, 10)
                if (antesDeAgora(ini.plusMinutes(90))) {
                    val pre = 150 + ruido(20) + vies * 0.5
                    val dur = listOf(40, 45, 50, 60).random(rnd)
                    val intens = if (rnd.nextDouble() < 0.35) Intensidade.INTENSA else Intensidade.MODERADA
                    med(ini.minusMinutes(10), pre, MomentoMedicao.ALEATORIA, "Antes do treino")
                    out += Atividade(dataHora = ini, tipo = if (intens == Intensidade.INTENSA) "Corrida" else listOf("Caminhada", "Musculação", "Bicicleta").random(rnd),
                        duracaoMin = dur, intensidade = intens)
                    med(ini.plusMinutes(dur + 15L), pre - 35 * intens.fator + ruido(10), MomentoMedicao.ALEATORIA, "Depois do treino")
                }
            }

            // Jantar
            val preJantar = (if (treino) 112 else 132) + ruido(20) + vies
            refeicao(out, rnd, dia, 19, 45, TipoRefeicao.JANTAR, listOf("Sopa de legumes com pão" to 40.0, "Omelete com salada" to 15.0, "Pizza (2 fatias)" to 70.0, "Arroz, ovo e legumes" to 55.0),
                razao = 13.0, pre = preJantar, k = 3.1, ruido = ::ruido, antesDeAgora = ::antesDeAgora, t = { h, m -> t(dia, h, m, 15) }, medirAntes = true)

            // Antes de dormir + basal
            val tDormir = t(dia, 22, 30, 25)
            if (antesDeAgora(tDormir)) {
                med(tDormir, 145 + ruido(25) + vies * 0.6, MomentoMedicao.ANTES_DORMIR)
                out += Dose(dataHora = t(dia, 22, 0, if (i % 6 == 0) 70 else 15), tipo = TipoDose.BASAL, medicamento = "Tresiba", quantidade = 18.0)
            }
        }
        return out
    }

    private fun refeicao(
        out: MutableList<Registro>, rnd: Random, dia: LocalDate, h: Int, m: Int, tipo: TipoRefeicao,
        cardapio: List<Pair<String, Double>>, razao: Double, pre: Double, k: Double,
        ruido: (Number) -> Double, antesDeAgora: (LocalDateTime) -> Boolean,
        t: (Int, Int) -> LocalDateTime, medirAntes: Boolean = false,
    ) {
        val inicio = t(h, m)
        if (!antesDeAgora(inicio)) return
        val (desc, carbBase) = cardapio.random(rnd)
        val carbs = (carbBase + ruido(6)).coerceAtLeast(5.0).roundToInt().toDouble()
        val bolus = (carbs / razao * 2).roundToInt() / 2.0 + (if (pre > 180) 1.0 else 0.0)
        if (medirAntes) out += Medicao(dataHora = inicio.minusMinutes(10), valor = pre.roundToInt().coerceIn(40, 450), momento = MomentoMedicao.PRE_REFEICAO)
        out += Dose(dataHora = inicio.minusMinutes(10), tipo = TipoDose.ULTRARRAPIDA, medicamento = "Fiasp", quantidade = bolus, observacao = "${carbs.toInt()} g de carboidrato")
        out += Refeicao(dataHora = inicio, tipo = tipo, descricao = desc, carboidratos = carbs)
        val pos = pre + 25 + carbs * k - bolus * 42 + ruido(18)
        val tPos = inicio.plusMinutes(120L + rnd.nextInt(-15, 16))
        if (antesDeAgora(tPos)) out += Medicao(dataHora = tPos, valor = pos.roundToInt().coerceIn(40, 450), momento = MomentoMedicao.POS_REFEICAO)
    }
}
