package glicemix.lembretes

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import glicemix.app.BancoDados
import glicemix.telas.TipoRegistro
import glicemix.model.novoId
import java.time.DayOfWeek
import java.time.LocalDateTime

/** Lembrete recorrente: horário, dias da semana e o tipo de registro a abrir ao tocar. */
data class Lembrete(
    val id: String = novoId(),
    val titulo: String,
    val hora: Int,
    val minuto: Int,
    val dias: Set<DayOfWeek> = DayOfWeek.entries.toSet(),
    val abrir: TipoRegistro = TipoRegistro.MEDICAO,
    val ativo: Boolean = true,
) {
    val horario: String get() = "%02d:%02d".format(hora, minuto)

    /** Próximo disparo estritamente depois de [apos], ou null se nenhum dia foi escolhido. */
    fun proxima(apos: LocalDateTime): LocalDateTime? {
        if (dias.isEmpty()) return null
        for (i in 0..7) {
            val dia = apos.toLocalDate().plusDays(i.toLong())
            if (dia.dayOfWeek in dias) {
                val t = dia.atTime(hora, minuto)
                if (t.isAfter(apos)) return t
            }
        }
        return null
    }

    val descricaoDias: String
        get() = when {
            dias.size == 7 -> "Todos os dias"
            dias == DIAS_UTEIS -> "Dias úteis"
            dias == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Fins de semana"
            dias.isEmpty() -> "Nenhum dia"
            else -> ORDEM.filter { it in dias }.joinToString(", ") { NOME_CURTO.getValue(it) }
        }

    companion object {
        /** Semana começando no domingo, como no calendário brasileiro. */
        val ORDEM = listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY)
        val LETRA = mapOf(DayOfWeek.SUNDAY to "D", DayOfWeek.MONDAY to "S", DayOfWeek.TUESDAY to "T", DayOfWeek.WEDNESDAY to "Q",
            DayOfWeek.THURSDAY to "Q", DayOfWeek.FRIDAY to "S", DayOfWeek.SATURDAY to "S")
        val NOME_CURTO = mapOf(DayOfWeek.SUNDAY to "dom", DayOfWeek.MONDAY to "seg", DayOfWeek.TUESDAY to "ter", DayOfWeek.WEDNESDAY to "qua",
            DayOfWeek.THURSDAY to "qui", DayOfWeek.FRIDAY to "sex", DayOfWeek.SATURDAY to "sáb")
        val DIAS_UTEIS = setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)

        /** Sugestões para criar lembretes com um toque. */
        val SUGESTOES = listOf(
            Triple("Medir glicemia em jejum", 7 to 0, TipoRegistro.MEDICAO),
            Triple("Medir antes de dormir", 22 to 30, TipoRegistro.MEDICAO),
            Triple("Aplicar insulina basal", 22 to 0, TipoRegistro.DOSE),
            Triple("Tomar medicamento", 8 to 0, TipoRegistro.DOSE),
        )
    }
}

/** Guarda os lembretes na tabela `lembretes` do banco e a preferência do lembrete pós-refeição. */
class LembretesRepo(ctx: Context) {
    private val banco = BancoDados.de(ctx)
    private val prefs = ctx.getSharedPreferences("glicemix", Context.MODE_PRIVATE)

    fun listar(): List<Lembrete> {
        val lista = mutableListOf<Lembrete>()
        banco.readableDatabase.query("lembretes", null, null, null, null, null, "hora, minuto").use { c ->
            while (c.moveToNext()) {
                try {
                    lista += Lembrete(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        titulo = c.getString(c.getColumnIndexOrThrow("titulo")),
                        hora = c.getInt(c.getColumnIndexOrThrow("hora")),
                        minuto = c.getInt(c.getColumnIndexOrThrow("minuto")),
                        dias = c.getString(c.getColumnIndexOrThrow("dias")).split(",").filter { it.isNotBlank() }
                            .map { DayOfWeek.of(it.trim().toInt()) }.toSet(),
                        abrir = TipoRegistro.valueOf(c.getString(c.getColumnIndexOrThrow("abrir"))),
                        ativo = c.getInt(c.getColumnIndexOrThrow("ativo")) == 1,
                    )
                } catch (e: Exception) { /* linha inválida: ignora */ }
            }
        }
        return lista
    }

    fun salvar(l: Lembrete) {
        val v = ContentValues().apply {
            put("id", l.id); put("titulo", l.titulo); put("hora", l.hora); put("minuto", l.minuto)
            put("dias", l.dias.joinToString(",") { it.value.toString() }); put("abrir", l.abrir.name); put("ativo", if (l.ativo) 1 else 0)
        }
        banco.writableDatabase.insertWithOnConflict("lembretes", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun remover(id: String) {
        banco.writableDatabase.delete("lembretes", "id = ?", arrayOf(id))
    }

    var lembrarPosRefeicao: Boolean
        get() = prefs.getBoolean("pos_refeicao", true)
        set(v) { prefs.edit().putBoolean("pos_refeicao", v).apply() }
}
