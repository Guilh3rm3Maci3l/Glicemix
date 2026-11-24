package glicemix.lembretes

import android.content.Context
import glicemix.model.Antecedencia
import glicemix.model.Compromisso
import glicemix.model.CompromissosCodec
import glicemix.model.resumo
import java.time.LocalDateTime
import java.time.LocalTime

/** Consultas e exames agendados, guardados nas preferências do app; cada um agenda um aviso antes. */
class CompromissosRepo(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("glicemix", Context.MODE_PRIVATE)

    private fun linhas(): Set<String> = prefs.getStringSet(CHAVE, emptySet()).orEmpty()

    /** Do mais próximo ao mais distante; os que já passaram vêm primeiro (para a pessoa resolver). */
    fun listar(): List<Compromisso> = linhas().mapNotNull { CompromissosCodec.decodificar(it) }.sortedBy { it.quando }

    fun salvar(c: Compromisso) {
        val outros = linhas().filter { CompromissosCodec.decodificar(it)?.id != c.id }
        prefs.edit().putStringSet(CHAVE, (outros + CompromissosCodec.codificar(c)).toSet()).apply()
        agendar(c)
    }

    fun remover(id: String) {
        prefs.edit().putStringSet(CHAVE, linhas().filter { CompromissosCodec.decodificar(it)?.id != id }.toSet()).apply()
        Agendador.cancelar(ctx, idAlarme(id))
        Notificacoes.cancelar(ctx, idAlarme(id))
    }

    fun agendarTodos() = listar().forEach { agendar(it) }

    private fun agendar(c: Compromisso) {
        Agendador.cancelar(ctx, idAlarme(c.id))
        val quando = quandoAvisar(c, LocalDateTime.now()) ?: return
        val detalhe = c.resumo(quando.toLocalDate()) + if (c.local.isNotBlank()) " · ${c.local}" else ""
        Agendador.agendarUnico(ctx, idAlarme(c.id), "${c.tipo.rotulo}: ${c.nome}", detalhe, null, quando)
    }

    companion object {
        private const val CHAVE = "compromissos"
        private fun idAlarme(id: String) = "compromisso-$id"

        /**
         * Momento do aviso: [Compromisso.antecedencia] antes, às 9h. Para "No dia" com hora marcada, 2 horas antes
         * (ou às 6h, se for de manhã cedo). Se esse momento já passou, não avisa (a pessoa acabou de agendar).
         */
        fun quandoAvisar(c: Compromisso, agora: LocalDateTime): LocalDateTime? {
            val dia = c.data.minusDays(c.antecedencia.dias)
            val hora = if (c.antecedencia == Antecedencia.NO_DIA && c.hora != null) when {
                c.hora.hour >= 8 -> c.hora.minusHours(2)
                c.hora.isAfter(LocalTime.of(6, 0)) -> LocalTime.of(6, 0)
                c.hora.isAfter(LocalTime.of(0, 30)) -> c.hora.minusMinutes(30)
                else -> LocalTime.of(0, 1)
            } else LocalTime.of(9, 0)
            val alvo = dia.atTime(hora)
            return alvo.takeIf { it.isAfter(agora) && c.quando.isAfter(agora) }
        }
    }
}
