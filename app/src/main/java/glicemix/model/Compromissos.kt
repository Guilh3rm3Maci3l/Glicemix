package glicemix.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit

enum class TipoCompromisso(val rotulo: String) {
    CONSULTA("Consulta"),
    EXAME("Exame");

    override fun toString() = rotulo
}

/** Quanto tempo antes o app avisa. */
enum class Antecedencia(val rotulo: String, val dias: Long) {
    NO_DIA("No dia", 0),
    UM_DIA("1 dia antes", 1),
    TRES_DIAS("3 dias antes", 3),
    UMA_SEMANA("1 semana antes", 7);

    override fun toString() = rotulo
}

enum class SituacaoCompromisso { PASSOU, HOJE, EM_BREVE, FUTURO }

/** Consulta ou exame agendado (data e, se houver, hora), com aviso antes. */
data class Compromisso(
    val id: String = novoId(),
    val tipo: TipoCompromisso,
    val nome: String,
    val data: LocalDate,
    val hora: LocalTime? = null,
    val local: String = "",
    val antecedencia: Antecedencia = Antecedencia.UM_DIA,
) {
    val quando: LocalDateTime get() = data.atTime(hora ?: LocalTime.of(23, 59))

    fun diasAte(hoje: LocalDate): Long = ChronoUnit.DAYS.between(hoje, data)

    fun situacao(hoje: LocalDate): SituacaoCompromisso {
        val d = diasAte(hoje)
        return when {
            d < 0 -> SituacaoCompromisso.PASSOU
            d == 0L -> SituacaoCompromisso.HOJE
            d <= 7 -> SituacaoCompromisso.EM_BREVE
            else -> SituacaoCompromisso.FUTURO
        }
    }

    companion object {
        /** Exames e consultas comuns no acompanhamento do diabetes, para preencher o nome com um toque. */
        val SUGESTOES = listOf(
            TipoCompromisso.CONSULTA to "Endocrinologista",
            TipoCompromisso.EXAME to "Hemoglobina glicada (HbA1c)",
            TipoCompromisso.EXAME to "Fundo de olho (retina)",
            TipoCompromisso.EXAME to "Albumina na urina e creatinina",
            TipoCompromisso.EXAME to "Colesterol e triglicerídeos",
            TipoCompromisso.CONSULTA to "Oftalmologista",
            TipoCompromisso.CONSULTA to "Nutricionista",
        )
    }
}

private fun dm(d: LocalDate) = "%02d/%02d".format(d.dayOfMonth, d.monthValue)
private val DIAS_SEMANA = listOf("seg", "ter", "qua", "qui", "sex", "sáb", "dom")

/** "Hoje às 14:30", "Amanhã", "Em 5 dias (sex 28/11)", "Passou há 3 dias (21/11)". */
fun Compromisso.resumo(hoje: LocalDate): String {
    val d = diasAte(hoje)
    val h = hora?.let { " às %02d:%02d".format(it.hour, it.minute) } ?: ""
    val data = "${DIAS_SEMANA[this.data.dayOfWeek.value - 1]} ${dm(this.data)}"
    return when {
        d < 0 -> "Passou há ${-d} dia${if (-d == 1L) "" else "s"} (${dm(this.data)})"
        d == 0L -> "Hoje$h"
        d == 1L -> "Amanhã$h"
        d <= 14 -> "Em $d dias ($data)$h"
        else -> "${"%02d/%02d/%d".format(this.data.dayOfMonth, this.data.monthValue, this.data.year)}$h"
    }
}

/** Uma linha por compromisso, para guardar nas preferências do app. */
object CompromissosCodec {
    private const val SEP = '\u001f'
    private fun limpo(s: String) = s.replace(SEP, ' ').replace('\n', ' ')

    fun codificar(c: Compromisso): String = listOf(
        c.id, c.tipo.name, limpo(c.nome), c.data.toString(), c.hora?.toString().orEmpty(), limpo(c.local), c.antecedencia.name,
    ).joinToString(SEP.toString())

    fun decodificar(linha: String): Compromisso? = try {
        val p = linha.split(SEP)
        if (p.size < 7) null
        else Compromisso(
            id = p[0], tipo = TipoCompromisso.valueOf(p[1]), nome = p[2], data = LocalDate.parse(p[3]),
            hora = p[4].takeIf { it.isNotBlank() }?.let { LocalTime.parse(it) }, local = p[5], antecedencia = Antecedencia.valueOf(p[6]),
        ).takeIf { it.id.isNotBlank() && it.nome.isNotBlank() }
    } catch (_: Exception) { null }
}
