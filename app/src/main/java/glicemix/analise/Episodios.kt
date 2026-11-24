package glicemix.analise

import glicemix.model.*
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.abs

/**
 * Uma refeição com o que veio junto: insulina aplicada e o que a glicemia fez em seguida.
 * É a unidade que responde "comi isso, apliquei aquilo, deu no que?".
 */
data class Episodio(
    val refeicao: Refeicao,
    val doses: List<Dose>,
    val antes: Medicao?,
    val depois: Medicao?,
) {
    val bolus: Double get() = doses.filter { it.unidade.equals("UI", true) }.sumOf { it.quantidade }
    val variacao: Int? get() = if (antes != null && depois != null) depois.valor - antes.valor else null
    val temResultado get() = variacao != null
}

object Episodios {

    private fun min(a: LocalDateTime, b: LocalDateTime) = Duration.between(a, b).toMinutes()

    /**
     * Monta os episódios. A dose pertence à refeição pelo vínculo (`refeicaoId`); doses antigas, sem vínculo,
     * são associadas pelo horário (de 20 min antes a 30 min depois), como a análise de padrões já fazia.
     */
    fun de(refeicoes: List<Refeicao>, doses: List<Dose>, medicoes: List<Medicao>): List<Episodio> {
        val porVinculo = doses.filter { it.refeicaoId.isNotBlank() }.groupBy { it.refeicaoId }
        val soltas = doses.filter { it.refeicaoId.isBlank() && it.tipo.ehBolus }
        val usadas = mutableSetOf<String>()
        return refeicoes.sortedBy { it.dataHora }.map { r ->
            val vinculadas = porVinculo[r.id].orEmpty()
            val porHorario = if (vinculadas.isNotEmpty()) emptyList()
            else soltas.filter { it.id !in usadas && min(it.dataHora, r.dataHora) in -20..30 }
            usadas += porHorario.map { it.id }
            val antes = medicoes.filter { min(it.dataHora, r.dataHora) in 0..60 }.maxByOrNull { it.dataHora }
            val depois = medicoes.filter { min(r.dataHora, it.dataHora) in 90..180 }.minByOrNull { abs(min(r.dataHora, it.dataHora) - 120) }
            Episodio(r, vinculadas + porHorario, antes, depois)
        }
    }

    fun de(d: Dados) = de(d.refeicoes, d.doses, d.medicoes)

    /** Ids das doses que já aparecem dentro de um episódio (a lista de registros não as repete). */
    fun dosesEmEpisodios(eps: List<Episodio>): Set<String> = eps.flatMap { it.doses }.map { it.id }.toSet()

    /**
     * O que o histórico da própria pessoa mostra para refeições parecidas (mesmo tipo de refeição e carboidratos
     * próximos). É uma consulta, não uma recomendação: reúne as doses aplicadas e o que a glicemia fez depois.
     */
    data class ReferenciaDose(
        val n: Int,
        val minimo: Double,
        val mediana: Double,
        val maximo: Double,
        val comResultado: Int,
        val noAlvo: Int,
        val acima: Int,
        val abaixo: Int,
        val mediaDepois: Double?,
        val carbosMin: Double,
        val carbosMax: Double,
    )

    /** Precisa de ao menos [minimo] episódios parecidos com insulina; senão devolve null (pouca base para mostrar). */
    fun referenciaDose(
        episodios: List<Episodio>, tipo: TipoRefeicao, carboidratos: Double, cfg: Configuracao,
        agora: LocalDateTime = LocalDateTime.now(), minimo: Int = 3,
    ): ReferenciaDose? {
        if (carboidratos <= 0) return null
        val margem = maxOf(10.0, carboidratos * 0.25)
        val parecidos = episodios.filter {
            it.refeicao.tipo == tipo && it.bolus > 0 && abs(it.refeicao.carboidratos - carboidratos) <= margem &&
                it.refeicao.dataHora.isAfter(agora.minusDays(120))
        }
        if (parecidos.size < minimo) return null
        val doses = parecidos.map { it.bolus }.sorted()
        val mediana = if (doses.size % 2 == 1) doses[doses.size / 2] else (doses[doses.size / 2 - 1] + doses[doses.size / 2]) / 2
        val depois = parecidos.mapNotNull { it.depois }
        return ReferenciaDose(
            n = parecidos.size, minimo = doses.first(), mediana = mediana, maximo = doses.last(),
            comResultado = depois.size,
            noAlvo = depois.count { cfg.classificar(it.valor) == Faixa.NO_ALVO },
            acima = depois.count { it.valor > cfg.alvoMax },
            abaixo = depois.count { it.valor < cfg.alvoMin },
            mediaDepois = depois.takeIf { it.isNotEmpty() }?.map { it.valor.toDouble() }?.average(),
            carbosMin = parecidos.minOf { it.refeicao.carboidratos }, carbosMax = parecidos.maxOf { it.refeicao.carboidratos },
        )
    }
}
