package glicemix.model

import java.time.LocalDateTime

/** Momento da medição mais provável para o horário (usado como padrão nos formulários). */
fun momentoSugerido(t: LocalDateTime): MomentoMedicao = when (t.hour) {
    in 0..4 -> MomentoMedicao.MADRUGADA
    in 5..8 -> MomentoMedicao.JEJUM
    in 21..23 -> MomentoMedicao.ANTES_DORMIR
    else -> MomentoMedicao.PRE_REFEICAO
}

/** Refeição mais provável para o horário. */
fun refeicaoSugerida(t: LocalDateTime): TipoRefeicao {
    val h = t.hour + t.minute / 60.0
    return when {
        h < 9.5 -> TipoRefeicao.CAFE_DA_MANHA
        h < 11.5 -> TipoRefeicao.LANCHE_MANHA
        h < 15 -> TipoRefeicao.ALMOCO
        h < 18.5 -> TipoRefeicao.LANCHE_TARDE
        h < 21.5 -> TipoRefeicao.JANTAR
        else -> TipoRefeicao.CEIA
    }
}

/** Valores de uso frequente, do mais para o menos usado. */
fun maisUsados(valores: List<String>): List<String> =
    valores.filter { it.isNotBlank() }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
