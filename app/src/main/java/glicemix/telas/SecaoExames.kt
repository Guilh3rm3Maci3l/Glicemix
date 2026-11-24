package glicemix.telas

import android.app.DatePickerDialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import glicemix.app.GlicemixViewModel
import glicemix.lembretes.ExamesRepo
import glicemix.model.Exame
import glicemix.model.SituacaoExame
import glicemix.model.resumoSituacao
import java.time.LocalDate

/** Cartão de exames de rotina (HbA1c, fundo de olho…): quando foi o último, quando vence e aviso antes de vencer. */
@Composable
@Suppress("UNUSED_PARAMETER")
fun SecaoExames(vm: GlicemixViewModel) {
    val ctx = LocalContext.current
    val repo = remember { ExamesRepo(ctx) }
    var lista by remember { mutableStateOf(repo.listar()) }
    var editando by remember { mutableStateOf<Exame?>(null) }
    val hoje = LocalDate.now()

    fun salvar(e: Exame) { repo.salvar(e); lista = repo.listar() }

    Cartao(
        titulo = "Exames de rotina",
        subtitulo = "Registre quando fez cada exame e o app avisa antes de vencer. Os intervalos são sugestões: siga o que a sua equipe de saúde indicar.",
    ) {
        if (lista.isEmpty()) {
            Text("Nenhum exame ainda. Comece por uma sugestão:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        lista.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val cor = when (e.situacao(hoje)) {
                SituacaoExame.ATRASADO -> MaterialTheme.colorScheme.error
                SituacaoExame.PROXIMO -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(Modifier.fillMaxWidth()) {
                Text(e.nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("${e.resumoSituacao(hoje)} · ${e.periodicidade}", style = MaterialTheme.typography.bodySmall, color = cor)
                e.ultimo?.let { Text("Último: ${it.format(fData)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { salvar(e.copy(ultimo = LocalDate.now())) }) { Text("Fiz hoje") }
                    TextButton(onClick = { editando = e }) { Text("Editar") }
                }
            }
        }

        val faltam = Exame.SUGESTOES.filter { (nome, _) -> lista.none { it.nome == nome } }
        if (faltam.isNotEmpty()) {
            if (lista.isNotEmpty()) Text("Adicionar:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                faltam.forEach { (nome, meses) ->
                    SuggestionChip(onClick = { editando = Exame(nome = nome, intervaloMeses = meses) }, label = { Text(nome) })
                }
            }
        }
        OutlinedButton(onClick = { editando = Exame(nome = "", intervaloMeses = 12) }, modifier = Modifier.fillMaxWidth()) { Text("Outro exame") }
    }

    editando?.let { atual ->
        EditorExame(
            inicial = atual,
            existe = lista.any { it.id == atual.id },
            aoSalvar = { salvar(it); editando = null },
            aoRemover = { repo.remover(atual.id); lista = repo.listar(); editando = null },
            aoFechar = { editando = null },
        )
    }
}

@Composable
private fun EditorExame(inicial: Exame, existe: Boolean, aoSalvar: (Exame) -> Unit, aoRemover: () -> Unit, aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    var nome by remember { mutableStateOf(inicial.nome) }
    var meses by remember { mutableStateOf(inicial.intervaloMeses) }
    var ultimo by remember { mutableStateOf(inicial.ultimo) }
    var erro by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (existe) "Editar exame" else "Novo exame") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(nome, { nome = it; erro = null }, label = { Text("Exame ou consulta") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Repetir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (Exame.INTERVALOS + inicial.intervaloMeses).distinct().sorted().forEach { m ->
                        FilterChip(selected = meses == m, onClick = { meses = m }, label = { Text(Exame(nome = "x", intervaloMeses = m).periodicidade) })
                    }
                }
                Text("Quando foi o último", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(
                    onClick = {
                        val base = ultimo ?: LocalDate.now()
                        DatePickerDialog(ctx, { _, a, m, d -> ultimo = LocalDate.of(a, m + 1, d) }, base.year, base.monthValue - 1, base.dayOfMonth).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(ultimo?.format(fData) ?: "Escolher a data")
                }
                if (ultimo != null) TextButton(onClick = { ultimo = null }) { Text("Não sei / limpar a data") }
                erro?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nome.isBlank()) { erro = "Informe o nome do exame."; return@TextButton }
                if (ultimo?.isAfter(LocalDate.now()) == true) { erro = "A data do último exame não pode ser no futuro."; return@TextButton }
                aoSalvar(inicial.copy(nome = nome.trim(), intervaloMeses = meses, ultimo = ultimo))
            }) { Text("Salvar") }
        },
        dismissButton = {
            Row {
                if (existe) TextButton(onClick = aoRemover) { Text("Remover", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = aoFechar) { Text("Cancelar") }
            }
        },
    )
}
