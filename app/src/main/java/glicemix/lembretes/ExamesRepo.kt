package glicemix.lembretes

import android.content.Context
import glicemix.model.Exame
import glicemix.model.SituacaoExame
import glicemix.model.ExamesCodec
import glicemix.model.resumoSituacao
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters

/** Exames de rotina guardados nas preferências do app; cada um agenda um aviso antes de vencer. */
class ExamesRepo(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("glicemix", Context.MODE_PRIVATE)

    fun listar(): List<Exame> =
        prefs.getStringSet(CHAVE, emptySet()).orEmpty().mapNotNull { ExamesCodec.decodificar(it) }.sortedBy { it.proximo ?: LocalDate.MAX }

    fun salvar(e: Exame) {
        val outros = prefs.getStringSet(CHAVE, emptySet()).orEmpty().filter { ExamesCodec.decodificar(it)?.id != e.id }
        prefs.edit().putStringSet(CHAVE, (outros + ExamesCodec.codificar(e)).toSet()).apply()
        agendar(e)
    }

    fun remover(id: String) {
        val outros = prefs.getStringSet(CHAVE, emptySet()).orEmpty().filter { ExamesCodec.decodificar(it)?.id != id }
        prefs.edit().putStringSet(CHAVE, outros.toSet()).apply()
        Agendador.cancelar(ctx, idAlarme(id))
    }

    fun agendarTodos() = listar().forEach { agendar(it) }

    private fun agendar(e: Exame) {
        Agendador.cancelar(ctx, idAlarme(e.id))
        val quando = quandoAvisar(e, LocalDateTime.now()) ?: return
        Agendador.agendarUnico(ctx, idAlarme(e.id), "Exame: ${e.nome}", e.resumoSituacao(quando.toLocalDate()), null, quando)
    }

    companion object {
        private const val CHAVE = "exames"
        private fun idAlarme(id: String) = "exame-$id"

        /**
         * Quando avisar: [Exame.AVISO_DIAS] dias antes do vencimento, às 9h. Se essa data já passou e o exame está
         * perto de vencer ou atrasado, avisa na próxima segunda-feira às 9h (e de novo se o app for aberto depois).
         */
        fun quandoAvisar(e: Exame, agora: LocalDateTime): LocalDateTime? {
            if (!e.ativo) return null
            val proximo = e.proximo ?: return null
            val alvo = proximo.minusDays(Exame.AVISO_DIAS).atTime(9, 0)
            if (alvo.isAfter(agora)) return alvo
            if (e.situacao(agora.toLocalDate()) == SituacaoExame.EM_DIA) return null
            return agora.toLocalDate().with(TemporalAdjusters.next(DayOfWeek.MONDAY)).atTime(9, 0)
        }
    }
}
