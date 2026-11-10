package glicemix.analise

import glicemix.model.*
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Nivel(val rotulo: String) { ALERTA("Alerta"), ATENCAO("Atenção"), INFO("Observação"), POSITIVO("Ponto positivo") }

/** Um padrão encontrado nos dados, com a evidência que o sustenta. */
data class Padrao(
    val nivel: Nivel,
    val categoria: String,
    val titulo: String,
    val descricao: String,
    val evidencia: String = "",
)

/** Par de medições ao redor de uma refeição. */
data class RespostaRefeicao(
    val refeicao: Refeicao,
    val antes: Medicao,
    val depois: Medicao,
    val bolus: Double,
) {
    val variacao get() = depois.valor - antes.valor
}

data class RespostaAtividade(val atividade: Atividade, val antes: Medicao, val depois: Medicao) {
    val variacao get() = depois.valor - antes.valor
}

enum class Turno(val rotulo: String, val horas: IntRange) {
    MADRUGADA("Madrugada (0h–6h)", 0..5), MANHA("Manhã (6h–12h)", 6..11),
    TARDE("Tarde (12h–18h)", 12..17), NOITE("Noite (18h–24h)", 18..23);

    companion object { fun de(t: LocalDateTime) = entries.first { t.hour in it.horas } }
}

/**
 * Procura padrões nos dados registrados. Tudo aqui é descritivo: o objetivo é levar
 * observações para a conversa com a equipe de saúde, não substituir orientação médica.
 */
class AnalisadorPadroes(private val d: Dados) {

    private val cfg = d.config
    private fun min(a: LocalDateTime, b: LocalDateTime) = Duration.between(a, b).toMinutes()
    private fun f0(x: Double) = x.roundToInt().toString()
    private fun f1(x: Double) = "%.1f".format(x).replace('.', ',')
    private fun pct(x: Double) = "${x.roundToInt()}%"

    // ---------- pareamentos usados por padrões e gráficos ----------

    fun respostasRefeicoes(): List<RespostaRefeicao> = d.refeicoes.mapNotNull { r ->
        val antes = d.medicoes.filter { min(it.dataHora, r.dataHora) in 0..60 }.maxByOrNull { it.dataHora }
        val depois = d.medicoes.filter { min(r.dataHora, it.dataHora) in 90..180 }.minByOrNull { abs(min(r.dataHora, it.dataHora) - 120) }
        if (antes == null || depois == null) null
        else {
            val bolus = d.doses.filter { it.tipo.ehBolus && min(it.dataHora, r.dataHora) in -20..30 }.sumOf { it.quantidade }
            RespostaRefeicao(r, antes, depois, bolus)
        }
    }

    fun respostasAtividades(): List<RespostaAtividade> = d.atividades.mapNotNull { a ->
        val antes = d.medicoes.filter { min(it.dataHora, a.dataHora) in 0..60 }.maxByOrNull { it.dataHora }
        val depois = d.medicoes.filter { min(a.fim, it.dataHora) in 0..120 }.minByOrNull { it.dataHora }
        if (antes == null || depois == null) null else RespostaAtividade(a, antes, depois)
    }

    // ---------- análise ----------

    fun analisar(): List<Padrao> {
        if (d.medicoes.size < 5) return listOf(
            Padrao(Nivel.INFO, "Dados", "Poucos dados para analisar",
                "Registre ao menos algumas medições por dia durante uma ou duas semanas para que o Glicemix consiga identificar padrões.")
        )
        val achados = mutableListOf<Padrao>()
        val r = Estatistica.resumo(d)!!
        achados += controleGeral(r)
        achados += variabilidade(r)
        achados += hipoglicemiasPorTurno()
        achados += hipoNoturna()
        achados += hiperPorTurno()
        achados += fenomenoDoAlvorecer()
        achados += posPrandial()
        achados += carboidratosVsElevacao()
        achados += relacaoCarboInsulina()
        achados += efeitoAtividade()
        achados += hipoTardiaAposExercicio()
        achados += tendencia()
        achados += fimDeSemana()
        achados += regularidadeBasal()
        achados += frequencia(r)
        return achados.sortedBy { it.nivel.ordinal }
    }

    private fun controleGeral(r: Resumo): List<Padrao> {
        val out = mutableListOf<Padrao>()
        val tir = r.tempoNoAlvo
        val ev = "Média ${f0(r.media)} mg/dL · GMI ${f1(r.gmi)}% · ${r.n} medições"
        out += when {
            tir >= 70 -> Padrao(Nivel.POSITIVO, "Controle geral", "Tempo no alvo de ${pct(tir)}",
                "${pct(tir)} das medições ficaram entre ${cfg.alvoMin} e ${cfg.alvoMax} mg/dL, acima da meta usual de 70%.", ev)
            tir >= 50 -> Padrao(Nivel.ATENCAO, "Controle geral", "Tempo no alvo de ${pct(tir)}",
                "${pct(tir)} das medições ficaram no alvo. A meta usual é de pelo menos 70%; os padrões abaixo mostram onde estão as maiores perdas.", ev)
            else -> Padrao(Nivel.ALERTA, "Controle geral", "Tempo no alvo de apenas ${pct(tir)}",
                "Menos da metade das medições ficou entre ${cfg.alvoMin} e ${cfg.alvoMax} mg/dL. Vale revisar o plano com sua equipe de saúde.", ev)
        }
        val abaixo = r.tempoAbaixo
        val muitoAbaixo = r.percentualPorFaixa[Faixa.MUITO_BAIXA] ?: 0.0
        if (abaixo > 4 || muitoAbaixo > 1) out += Padrao(Nivel.ALERTA, "Hipoglicemia", "Muitas leituras abaixo do alvo",
            "${pct(abaixo)} das leituras ficaram abaixo de ${cfg.alvoMin} mg/dL (meta: menos de 4%), sendo ${pct(muitoAbaixo)} abaixo de ${cfg.hipoSevera} mg/dL (meta: menos de 1%).",
            "${r.episodiosHipo} " + (if (r.episodiosHipo == 1) "episódio" else "episódios") + " de hipoglicemia no período")
        if (r.gmi >= 8.0) out += Padrao(Nivel.ATENCAO, "Controle geral", "HbA1c estimada elevada",
            "A média de ${f0(r.media)} mg/dL corresponde a um GMI de ${f1(r.gmi)}%. O GMI é uma estimativa e pode diferir da hemoglobina glicada de laboratório.")
        return out
    }

    private fun variabilidade(r: Resumo): List<Padrao> = when {
        r.n < 10 -> emptyList()
        r.cv > 36 -> listOf(Padrao(Nivel.ATENCAO, "Variabilidade", "Glicemia muito oscilante",
            "O coeficiente de variação está em ${f1(r.cv)}% (meta: até 36%). Oscilações grandes aumentam o risco de hipoglicemia mesmo com média boa.",
            "Desvio padrão de ${f0(r.desvio)} mg/dL"))
        else -> listOf(Padrao(Nivel.POSITIVO, "Variabilidade", "Variabilidade dentro da meta",
            "O coeficiente de variação está em ${f1(r.cv)}%, dentro da meta de até 36%."))
    }

    private fun hipoglicemiasPorTurno(): List<Padrao> {
        val eps = Estatistica.episodiosHipo(d.medicoes, cfg)
        if (eps.size < 2) return emptyList()
        val porTurno = eps.groupingBy { Turno.de(it.first().dataHora) }.eachCount()
        val (turno, qtd) = porTurno.maxBy { it.value }
        if (qtd < 2 || qtd.toDouble() / eps.size < 0.4) return emptyList()
        val horas = eps.filter { Turno.de(it.first().dataHora) == turno }.map { it.first().dataHora.hour }
        return listOf(Padrao(Nivel.ALERTA, "Hipoglicemia", "Hipoglicemias concentradas no turno: ${turno.rotulo.substringBefore(" (")}",
            "$qtd de ${eps.size} episódios de hipoglicemia aconteceram na ${turno.rotulo.lowercase()}, geralmente por volta das ${horas.sorted()[horas.size / 2]}h. " +
                "Observe o que costuma anteceder esse horário (doses, refeições puladas, exercício).",
            "Horários: " + horas.sorted().joinToString(", ") { "${it}h" }))
    }

    private fun hipoNoturna(): List<Padrao> {
        val noturnas = d.medicoes.filter { it.dataHora.hour < 6 && it.valor < cfg.alvoMin }
        if (noturnas.isEmpty()) return emptyList()
        return listOf(Padrao(Nivel.ALERTA, "Hipoglicemia", "Hipoglicemia durante a madrugada",
            (if (noturnas.size == 1) "Foi registrada 1 leitura" else "Foram registradas ${noturnas.size} leituras") + " abaixo de ${cfg.alvoMin} mg/dL entre 0h e 6h. Hipoglicemias noturnas podem passar despercebidas; converse com sua equipe sobre a dose basal e a ceia.",
            "Menor valor: ${noturnas.minOf { it.valor }} mg/dL"))
    }

    private fun hiperPorTurno(): List<Padrao> {
        val porTurno = d.medicoes.groupBy { Turno.de(it.dataHora) }.filterValues { it.size >= 4 }
        if (porTurno.size < 2) return emptyList()
        val acima = porTurno.mapValues { (_, l) -> l.count { it.valor > cfg.alvoMax } * 100.0 / l.size }
        val (pior, p) = acima.maxBy { it.value }
        val media = porTurno.mapValues { (_, l) -> l.map { it.valor.toDouble() }.average() }
        if (p < 35) return emptyList()
        return listOf(Padrao(Nivel.ATENCAO, "Hiperglicemia", "Glicemias altas com mais frequência na ${pior.rotulo.substringBefore(" (").lowercase()}",
            "${pct(p)} das medições nesse turno ficaram acima de ${cfg.alvoMax} mg/dL.",
            media.entries.sortedBy { it.key.ordinal }.joinToString(" · ") { "${it.key.rotulo.substringBefore(" (")}: ${f0(it.value)} mg/dL" }))
    }

    private fun fenomenoDoAlvorecer(): List<Padrao> {
        val porDia = d.medicoes.groupBy { it.dataHora.toLocalDate() }
        val diffs = porDia.values.mapNotNull { dia ->
            val madr = dia.filter { it.dataHora.hour in 2..4 }.minByOrNull { it.valor }
            val jejum = dia.filter { it.dataHora.hour in 6..9 && (it.momento == MomentoMedicao.JEJUM || it.dataHora.hour <= 8) }.minByOrNull { it.dataHora }
            if (madr != null && jejum != null) (jejum.valor - madr.valor).toDouble() else null
        }
        if (diffs.size < 3) return emptyList()
        val m = diffs.average()
        val frac = diffs.count { it >= 20 }.toDouble() / diffs.size
        if (m < 20 || frac < 0.5) return emptyList()
        return listOf(Padrao(Nivel.INFO, "Madrugada", "Possível fenômeno do alvorecer",
            "Em ${pct(frac * 100)} dos dias com medição na madrugada, a glicemia de jejum subiu em média ${f0(m)} mg/dL em relação às 3h, sem refeição no intervalo. É um padrão comum, causado por hormônios matinais.",
            "${diffs.size} noites comparadas"))
    }

    private fun posPrandial(): List<Padrao> {
        val resp = respostasRefeicoes()
        if (resp.size < 3) return emptyList()
        val out = mutableListOf<Padrao>()
        val porTipo = resp.groupBy { it.refeicao.tipo }.filterValues { it.size >= 2 }
        val medias = porTipo.mapValues { (_, l) -> l.map { it.variacao.toDouble() }.average() }
        val ev = medias.entries.sortedBy { it.key.ordinal }.joinToString(" · ") { "${it.key.rotulo}: ${sinal(it.value)} mg/dL" }
        val pior = medias.maxByOrNull { it.value }
        if (pior != null && pior.value > 50) {
            out += Padrao(Nivel.ATENCAO, "Alimentação", "Maior elevação após: ${pior.key.rotulo.lowercase()}",
                "Cerca de 2h após o ${pior.key.rotulo.lowercase()}, a glicemia sobe em média ${f0(pior.value)} mg/dL. Elevações acima de 50 mg/dL sugerem que a composição dessa refeição ou o momento da dose merecem atenção.", ev)
        } else if (medias.isNotEmpty() && medias.values.all { it <= 40 }) {
            out += Padrao(Nivel.POSITIVO, "Alimentação", "Respostas às refeições controladas",
                "Em todas as refeições comparadas, a elevação 2h depois ficou abaixo de 40 mg/dL em média.", ev)
        }
        val altosApos = resp.count { it.depois.valor > cfg.alvoMax }
        if (altosApos.toDouble() / resp.size > 0.5) out += Padrao(Nivel.ATENCAO, "Alimentação", "Pós-refeição frequentemente acima do alvo",
            "$altosApos de ${resp.size} medições pós-refeição passaram de ${cfg.alvoMax} mg/dL.")
        return out
    }

    private fun carboidratosVsElevacao(): List<Padrao> {
        val resp = respostasRefeicoes().filter { it.refeicao.carboidratos > 0 }
        val reg = Estatistica.regressao(resp.map { it.refeicao.carboidratos }, resp.map { it.variacao.toDouble() }) ?: return emptyList()
        if (reg.n < 6 || reg.r < 0.3 || reg.inclinacao <= 0) return emptyList()
        return listOf(Padrao(Nivel.INFO, "Alimentação", "Carboidratos explicam boa parte da elevação",
            "Nos seus dados, cada 10 g de carboidrato estão associados a cerca de ${sinal(reg.inclinacao * 10)} mg/dL na glicemia 2h após a refeição. Refeições com mais carboidrato tendem a gerar picos proporcionalmente maiores.",
            "Correlação r = ${"%.2f".format(reg.r).replace('.', ',')} em ${reg.n} refeições"))
    }

    private fun relacaoCarboInsulina(): List<Padrao> {
        val resp = respostasRefeicoes().filter { it.bolus > 0 && it.refeicao.carboidratos > 0 }
        if (resp.size < 4) return emptyList()
        val porTipo = resp.groupBy { it.refeicao.tipo }.filterValues { it.size >= 2 }
        if (porTipo.isEmpty()) return emptyList()
        val linhas = porTipo.entries.sortedBy { it.key.ordinal }.map { (t, l) ->
            val razao = l.sumOf { it.refeicao.carboidratos } / l.sumOf { it.bolus }
            val delta = l.map { it.variacao.toDouble() }.average()
            Triple(t, razao, delta)
        }
        val ev = linhas.joinToString(" · ") { "${it.first.rotulo}: 1 UI / ${f0(it.second)} g (${sinal(it.third)} mg/dL)" }
        val desigual = linhas.size >= 2 && (linhas.maxOf { it.third } - linhas.minOf { it.third }) > 40
        return listOf(Padrao(Nivel.INFO, "Insulina", "Relação carboidrato/insulina praticada",
            "Esta é a proporção entre carboidratos registrados e insulina de refeição aplicada em cada refeição, junto da variação média da glicemia 2h depois." +
                (if (desigual) " A resposta varia bastante entre refeições, o que pode indicar necessidades diferentes ao longo do dia." else "") +
                " Use como subsídio para conversar com sua equipe; não altere doses por conta própria.",
            ev))
    }

    private fun efeitoAtividade(): List<Padrao> {
        val resp = respostasAtividades()
        if (resp.size < 2) return emptyList()
        val m = resp.map { it.variacao.toDouble() }.average()
        val porIntensidade = resp.groupBy { it.atividade.intensidade }.mapValues { (_, l) -> l.map { it.variacao.toDouble() }.average() }
        val ev = porIntensidade.entries.sortedBy { it.key.ordinal }.joinToString(" · ") { "${it.key.rotulo}: ${sinal(it.value)} mg/dL" }
        return listOf(
            if (m < -10) Padrao(Nivel.POSITIVO, "Atividade física", "Exercício reduz sua glicemia em média ${f0(-m)} mg/dL",
                "Comparando a medição antes e até 2h depois de ${resp.size} atividades, a glicemia caiu em média ${f0(-m)} mg/dL.", ev)
            else Padrao(Nivel.INFO, "Atividade física", "Efeito do exercício pouco evidente",
                "Em ${resp.size} atividades com medição antes e depois, a variação média foi de ${sinal(m)} mg/dL. Exercícios intensos podem elevar a glicemia temporariamente.", ev)
        )
    }

    private fun hipoTardiaAposExercicio(): List<Padrao> {
        val relevantes = d.atividades.filter { it.duracaoMin >= 30 && it.intensidade != Intensidade.LEVE }
        if (relevantes.isEmpty()) return emptyList()
        val tardias = relevantes.mapNotNull { a ->
            d.medicoes.firstOrNull { min(a.fim, it.dataHora) in 120..(18 * 60) && it.valor < cfg.alvoMin }?.let { a to it }
        }
        if (tardias.size < 2) return emptyList()
        val horasDepois = tardias.map { min(it.first.fim, it.second.dataHora) / 60.0 }.average()
        return listOf(Padrao(Nivel.ALERTA, "Atividade física", "Hipoglicemia tardia após exercício",
            "Depois de ${tardias.size} de ${relevantes.size} sessões moderadas ou intensas houve hipoglicemia entre 2h e 18h mais tarde (em média ${f0(horasDepois)}h depois). O efeito do exercício pode durar horas; um lanche ou ajuste combinado com a equipe pode ajudar.",
            tardias.joinToString(" · ") { "${it.first.tipo} → ${it.second.valor} mg/dL" }))
    }

    private fun tendencia(): List<Padrao> {
        val dias = Estatistica.porDia(d.medicoes, cfg)
        if (dias.size < 10) return emptyList()
        val metade = dias.size / 2
        val a = dias.take(metade); val b = dias.drop(metade)
        val ma = a.map { it.media }.average(); val mb = b.map { it.media }.average()
        val ta = a.map { it.porFaixa[Faixa.NO_ALVO] ?: 0.0 }.average(); val tb = b.map { it.porFaixa[Faixa.NO_ALVO] ?: 0.0 }.average()
        val ev = "Média ${f0(ma)} → ${f0(mb)} mg/dL · Tempo no alvo ${pct(ta)} → ${pct(tb)}"
        return when {
            tb - ta >= 8 || (ma - mb >= 10 && tb >= ta - 2) -> listOf(Padrao(Nivel.POSITIVO, "Tendência", "Controle melhorando",
                "Comparando a primeira e a segunda metade do período, a média caiu de ${f0(ma)} para ${f0(mb)} mg/dL e o tempo no alvo foi de ${pct(ta)} para ${pct(tb)}.", ev))
            ta - tb >= 8 || (mb - ma >= 10 && tb <= ta + 2) -> listOf(Padrao(Nivel.ATENCAO, "Tendência", "Controle piorando",
                "Na segunda metade do período, a média subiu de ${f0(ma)} para ${f0(mb)} mg/dL e o tempo no alvo foi de ${pct(ta)} para ${pct(tb)}.", ev))
            else -> listOf(Padrao(Nivel.INFO, "Tendência", "Controle estável", "Não houve mudança importante entre a primeira e a segunda metade do período.", ev))
        }
    }

    private fun fimDeSemana(): List<Padrao> {
        val (fds, util) = d.medicoes.partition { it.dataHora.dayOfWeek == DayOfWeek.SATURDAY || it.dataHora.dayOfWeek == DayOfWeek.SUNDAY }
        if (fds.size < 5 || util.size < 5) return emptyList()
        val mf = fds.map { it.valor.toDouble() }.average(); val mu = util.map { it.valor.toDouble() }.average()
        if (abs(mf - mu) < 15) return emptyList()
        val maior = if (mf > mu) "nos fins de semana" else "nos dias úteis"
        return listOf(Padrao(Nivel.INFO, "Rotina", "Glicemia mais alta $maior",
            "A média nos fins de semana foi ${f0(mf)} mg/dL, contra ${f0(mu)} mg/dL nos dias úteis. Mudanças de rotina (horários, alimentação, atividade) costumam explicar essa diferença."))
    }

    private fun regularidadeBasal(): List<Padrao> {
        val basais = d.doses.filter { it.tipo == TipoDose.BASAL }
        if (basais.size < 5) return emptyList()
        val minutos = basais.map { it.dataHora.hour * 60.0 + it.dataHora.minute }
        val dp = Estatistica.desvio(minutos)
        if (dp < 60) return listOf(Padrao(Nivel.POSITIVO, "Insulina", "Insulina basal em horário regular",
            "A dose basal foi aplicada com variação típica de ${f0(dp)} minutos no horário."))
        return listOf(Padrao(Nivel.ATENCAO, "Insulina", "Horário da insulina basal irregular",
            "O horário da dose basal varia em média ${f1(dp / 60)}h de um dia para o outro. Manter um horário fixo deixa o efeito da insulina mais previsível."))
    }

    private fun frequencia(r: Resumo): List<Padrao> =
        if (r.medicoesPorDia < 3) listOf(Padrao(Nivel.INFO, "Dados", "Poucas medições por dia",
            "Você registrou em média ${f1(r.medicoesPorDia)} medições por dia. Medir antes e 2h depois das refeições permite análises muito mais precisas."))
        else emptyList()

    private fun sinal(x: Double) = (if (x >= 0) "+" else "−") + f0(abs(x))
}
