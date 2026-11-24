package glicemix.telas

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Schedule
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
import glicemix.lembretes.CompromissosRepo
import glicemix.model.Antecedencia
import glicemix.model.Compromisso
import glicemix.model.SituacaoCompromisso
import glicemix.model.TipoCompromisso
import glicemix.model.resumo
import java.time.LocalDate
import java.time.LocalTime

/**
 * Próximas consultas e exames, com data, hora e aviso antes. Os que já passaram aparecem primeiro, em destaque,
 * para a pessoa marcar como feitos (o que remove da lista) ou remarcar.
 */
@Composable
fun CartaoCompromissos() {
    val ctx = LocalContext.current
    val repo = remember { CompromissosRepo(ctx) }
    var lista by remember { mutableStateOf(repo.listar()) }
    var editando by remember { mutableStateOf<Compromisso?>(null) }
    val hoje = LocalDate.now()

    Cartao(titulo = "Próximas consultas e exames", subtitulo = if (lista.isEmpty()) "Agende para receber um aviso antes e não perder o retorno." else null) {
        lista.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 6.dp))
            val sit = c.situacao(hoje)
            val cor = when (sit) {
                SituacaoCompromisso.PASSOU -> MaterialTheme.colorScheme.error
                SituacaoCompromisso.HOJE, SituacaoCompromisso.EM_BREVE -> MaterialTheme.colorScheme.primary
                SituacaoCompromisso.FUTURO -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Column(Modifier.fillMaxWidth().clickable { editando = c }) {
                Text("${c.tipo.rotulo}: ${c.nome}", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(c.resumo(hoje) + if (c.local.isNotBlank()) " · ${c.local}" else "", style = MaterialTheme.typography.bodySmall, color = cor)
                if (sit == SituacaoCompromisso.PASSOU) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { repo.remover(c.id); lista = repo.listar() }) { Text("Já fiz") }
                        TextButton(onClick = { editando = c }) { Text("Remarcar") }
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { editando = Compromisso(tipo = TipoCompromisso.CONSULTA, nome = "", data = LocalDate.now().plusDays(7)) },
            modifier = Modifier.fillMaxWidth().padding(top = if (lista.isEmpty()) 0.dp else 10.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Agendar consulta ou exame")
        }
    }

    editando?.let { atual ->
        EditorCompromisso(
            inicial = atual,
            existe = lista.any { it.id == atual.id },
            aoSalvar = { repo.salvar(it); lista = repo.listar(); editando = null },
            aoRemover = { repo.remover(atual.id); lista = repo.listar(); editando = null },
            aoFechar = { editando = null },
        )
    }
}

@Composable
private fun EditorCompromisso(inicial: Compromisso, existe: Boolean, aoSalvar: (Compromisso) -> Unit, aoRemover: () -> Unit, aoFechar: () -> Unit) {
    val ctx = LocalContext.current
    var tipo by remember { mutableStateOf(inicial.tipo) }
    var nome by remember { mutableStateOf(inicial.nome) }
    var data by remember { mutableStateOf(inicial.data) }
    var hora by remember { mutableStateOf(inicial.hora) }
    var local by remember { mutableStateOf(inicial.local) }
    var antecedencia by remember { mutableStateOf(inicial.antecedencia) }
    var erro by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = aoFechar,
        title = { Text(if (existe) "Editar compromisso" else "Agendar") },
        text = {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TipoCompromisso.entries.forEach { t -> FilterChip(selected = tipo == t, onClick = { tipo = t }, label = { Text(t.rotulo) }) }
                }
                OutlinedTextField(nome, { nome = it; erro = null }, label = { Text("Com quem ou qual exame") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Compromisso.SUGESTOES.forEach { (t, n) -> SuggestionChip(onClick = { tipo = t; nome = n; erro = null }, label = { Text(n) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { DatePickerDialog(ctx, { _, a, m, d -> data = LocalDate.of(a, m + 1, d) }, data.year, data.monthValue - 1, data.dayOfMonth).show() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(data.format(fData))
                    }
                    OutlinedButton(
                        onClick = {
                            val h = hora ?: LocalTime.of(9, 0)
                            TimePickerDialog(ctx, { _, hh, mm -> hora = LocalTime.of(hh, mm) }, h.hour, h.minute, true).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Schedule, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(hora?.let { "%02d:%02d".format(it.hour, it.minute) } ?: "Hora")
                    }
                }
                if (hora != null) TextButton(onClick = { hora = null }) { Text("Sem hora marcada") }
                OutlinedTextField(local, { local = it }, label = { Text("Local (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Avisar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Antecedencia.entries.forEach { a -> FilterChip(selected = antecedencia == a, onClick = { antecedencia = a }, label = { Text(a.rotulo) }) }
                }
                erro?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nome.isBlank()) { erro = "Informe com quem ou qual exame."; return@TextButton }
                aoSalvar(inicial.copy(tipo = tipo, nome = nome.trim(), data = data, hora = hora, local = local.trim(), antecedencia = antecedencia))
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
