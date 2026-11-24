package glicemix.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class SituacaoExame(val rotulo: String) {
    SEM_DATA("Sem data do último"),
    EM_DIA("Em dia"),
    PROXIMO("Vence em breve"),
    ATRASADO("Atrasado"),
}

/**
 * Exame ou consulta de rotina do controle do diabetes, repetido a cada [intervaloMeses].
 * O intervalo é só um ponto de partida: quem define é a equipe de saúde da pessoa.
 */
data class Exame(
    val id: String = novoId(),
    val nome: String,
    val intervaloMeses: Int,
    val ultimo: LocalDate? = null,
    val ativo: Boolean = true,
) {
    /** Data prevista do próximo, ou null se ainda não se sabe quando foi o último. */
    val proximo: LocalDate? get() = ultimo?.plusMonths(intervaloMeses.toLong())

    /** Dias até o próximo (negativo = atrasado). */
    fun diasAte(hoje: LocalDate): Long? = proximo?.let { ChronoUnit.DAYS.between(hoje, it) }

    fun situacao(hoje: LocalDate): SituacaoExame {
        val d = diasAte(hoje) ?: return SituacaoExame.SEM_DATA
        return when {
            d < 0 -> SituacaoExame.ATRASADO
            d <= AVISO_DIAS -> SituacaoExame.PROXIMO
            else -> SituacaoExame.EM_DIA
        }
    }

    val periodicidade: String
        get() = when (intervaloMeses) {
            1 -> "todo mês"
            12 -> "todo ano"
            24 -> "a cada 2 anos"
            else -> "a cada $intervaloMeses meses"
        }

    companion object {
        /** Quantos dias antes do vencimento o app começa a avisar. */
        const val AVISO_DIAS = 30L

        /** Pontos de partida comuns no acompanhamento do diabetes; a pessoa ajusta com o médico. */
        val SUGESTOES = listOf(
            "Hemoglobina glicada (HbA1c)" to 3,
            "Consulta com o endocrinologista" to 3,
            "Fundo de olho (retina)" to 12,
            "Albumina na urina e creatinina (rim)" to 12,
            "Colesterol e triglicerídeos" to 12,
            "Exame dos pés" to 12,
        )

        val INTERVALOS = listOf(1, 3, 6, 12, 24)
    }
}

/** Texto curto do estado, para a lista: "Atrasado há 12 dias", "Vence em 5 dias (03/12/2026)". */
fun Exame.resumoSituacao(hoje: LocalDate): String {
    val d = diasAte(hoje) ?: return "Informe quando foi o último"
    val data = proximo!!.let { "%02d/%02d/%d".format(it.dayOfMonth, it.monthValue, it.year) }
    return when {
        d < 0 -> "Atrasado há ${-d} dia${if (-d == 1L) "" else "s"} (venceu em $data)"
        d == 0L -> "Vence hoje"
        d <= Exame.AVISO_DIAS -> "Vence em $d dia${if (d == 1L) "" else "s"} ($data)"
        else -> "Próximo em $data"
    }
}

/** Codificação simples (uma linha por exame) para guardar nas preferências do app. */
object ExamesCodec {
    private const val SEP = '\u001f'

    fun codificar(e: Exame): String =
        listOf(e.id, e.nome.replace(SEP, ' '), e.intervaloMeses.toString(), e.ultimo?.toString().orEmpty(), if (e.ativo) "1" else "0").joinToString(SEP.toString())

    fun decodificar(linha: String): Exame? = try {
        val c = linha.split(SEP)
        if (c.size < 5) null
        else Exame(
            id = c[0], nome = c[1], intervaloMeses = c[2].toInt().coerceIn(1, 120),
            ultimo = c[3].takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }, ativo = c[4] == "1",
        ).takeIf { it.id.isNotBlank() && it.nome.isNotBlank() }
    } catch (_: Exception) { null }
}
