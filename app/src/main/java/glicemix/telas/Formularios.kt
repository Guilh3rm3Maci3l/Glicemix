@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package glicemix.telas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.horizontalScroll
import glicemix.app.GlicemixViewModel
import glicemix.model.Atividade
import glicemix.model.Dose
import glicemix.model.Intensidade
import glicemix.model.Medicao
import glicemix.model.MomentoMedicao
import glicemix.model.Refeicao
import glicemix.model.Registro
import glicemix.model.TipoDose
import glicemix.model.TipoRefeicao
import glicemix.model.momentoSugerido
import glicemix.model.novoId
import glicemix.model.refeicaoSugerida
import java.time.LocalDate
import java.time.LocalDateTime

private fun decimal(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

/** Formulário em tela cheia para criar ou editar qualquer tipo de registro. */
@Composable
fun EditorRegistro(e: Edicao, vm: GlicemixViewModel, onFechar: () -> Unit, onHipo: (Int) -> Unit) {
    val atual = e.registro
    val med = atual as? Medicao
    val ref = atual as? Refeicao
    val dose = atual as? Dose
    val atv = atual as? Atividade
    val inicial = remember {
        atual?.dataHora ?: LocalDateTime.now().withSecond(0).withNano(0).let { if (e.tipo == TipoRegistro.ATIVIDADE) it.minusMinutes(30) else it }
    }

    var quando by remember { mutableStateOf(inicial) }
    var obs by remember { mutableStateOf(atual?.observacao ?: "") }
    var erro by remember { mutableStateOf<String?>(null) }
    var confirmarExclusao by remember { mutableStateOf(false) }

    // medição
    var valor by remember { mutableStateOf(med?.valor?.toString() ?: "") }
    var momento by remember { mutableStateOf(med?.momento ?: momentoSugerido(inicial)) }
    // refeição
    var tipoRef by remember { mutableStateOf(ref?.tipo ?: refeicaoSugerida(inicial)) }
    var descricao by remember { mutableStateOf(ref?.descricao ?: "") }
    var carbs by remember { mutableStateOf(ref?.carboidratos?.let { num(it) } ?: "") }
    var bolus by remember { mutableStateOf("") }
    val insulinas = remember { vm.insulinasBolus().ifEmpty { listOf("Fiasp", "Novorapid", "Humalog", "Apidra") } }
    var insulina by remember { mutableStateOf(insulinas.first()) }
    // dose
    var tipoDose by remember { mutableStateOf(dose?.tipo ?: TipoDose.ULTRARRAPIDA) }
    val medicamentos = remember { (vm.medicamentos() + listOf("Fiasp", "Novorapid", "Humalog", "Tresiba", "Lantus", "Toujeo", "NPH", "Metformina")).distinct() }
    var medicamento by remember { mutableStateOf(dose?.medicamento ?: vm.medicamentos().firstOrNull() ?: "") }
    var quantidade by remember { mutableStateOf(dose?.quantidade?.let { num(it) } ?: "") }
    var unidade by remember { mutableStateOf(dose?.unidade ?: "UI") }
    // atividade
    val tiposAtv = remember { (vm.tiposAtividade() + listOf("Caminhada", "Corrida", "Musculação", "Bicicleta", "Natação", "Futebol", "Yoga", "Dança")).distinct() }
    var tipoAtv by remember { mutableStateOf(atv?.tipo ?: tiposAtv.first()) }
    var duracao by remember { mutableStateOf(atv?.duracaoMin?.toString() ?: "30") }
    var intensidade by remember { mutableStateOf(atv?.intensidade ?: Intensidade.MODERADA) }

    fun salvar() {
        val id = atual?.id ?: novoId()
        when (e.tipo) {
            TipoRegistro.MEDICAO -> {
                val v = valor.trim().toIntOrNull()
                if (v == null || v !in 20..600) { erro = "Informe uma glicemia entre 20 e 600 mg/dL."; return }
                vm.salvar(Medicao(id, quando, v, momento, obs.trim()))
                onFechar()
                if (atual == null && v < vm.config.alvoMin) onHipo(v)
            }
            TipoRegistro.REFEICAO -> {
                val c = if (carbs.isBlank()) 0.0 else decimal(carbs)
                if (c == null || c < 0) { erro = "Carboidratos inválidos."; return }
                if (descricao.isBlank() && c == 0.0) { erro = "Informe o que comeu ou a quantidade de carboidratos."; return }
                val b = if (bolus.isBlank()) 0.0 else decimal(bolus)
                if (b == null || b < 0) { erro = "Dose de insulina inválida."; return }
                val r = Refeicao(id, quando, tipoRef, descricao.trim(), c, obs.trim())
                val lista = mutableListOf<Registro>(r)
                if (atual == null && b > 0) lista += Dose(
                    dataHora = quando, tipo = TipoDose.ULTRARRAPIDA, medicamento = insulina.trim().ifBlank { "Insulina" },
                    quantidade = b, observacao = "${tipoRef.rotulo} (${c.toInt()} g)",
                )
                if (atual == null) vm.salvarTodos(lista) else vm.salvar(r)
                onFechar()
            }
            TipoRegistro.DOSE -> {
                val q = decimal(quantidade)
                if (medicamento.isBlank()) { erro = "Informe o medicamento."; return }
                if (q == null || q <= 0) { erro = "A quantidade deve ser maior que zero."; return }
                vm.salvar(Dose(id, quando, tipoDose, medicamento.trim(), q, unidade.trim().ifBlank { "UI" }, obs.trim()))
                onFechar()
            }
            TipoRegistro.ATIVIDADE -> {
                val dmin = duracao.trim().toIntOrNull()
                if (tipoAtv.isBlank()) { erro = "Informe a atividade."; return }
                if (dmin == null || dmin !in 1..600) { erro = "Informe a duração em minutos."; return }
                vm.salvar(Atividade(id, quando, tipoAtv.trim(), dmin, intensidade, obs.trim()))
                onFechar()
            }
        }
    }

    Dialog(onDismissRequest = onFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text((if (atual == null) "Nova " else "Editar ") + e.tipo.rotulo.lowercase()) },
                    navigationIcon = { IconButton(onClick = onFechar) { Icon(Icons.Filled.Close, contentDescription = "Fechar") } },
                    actions = { TextButton(onClick = { salvar() }) { Text("Salvar", fontWeight = FontWeight.Bold) } },
                )
            },
        ) { pad ->
            Column(
                Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CampoDataHora(quando, rotulo = if (e.tipo == TipoRegistro.ATIVIDADE) "Início" else "Data e hora") { quando = it }

                when (e.tipo) {
                    TipoRegistro.MEDICAO -> {
                        CampoNumero(valor, "Glicemia", "mg/dL", decimal = false) { valor = it }
                        Rotulo("Momento")
                        EscolhaChips(MomentoMedicao.entries, momento, { it.rotulo }, { momento = it })
                    }
                    TipoRegistro.REFEICAO -> {
                        Rotulo("Refeição")
                        EscolhaChips(TipoRefeicao.entries, tipoRef, { it.rotulo }, { tipoRef = it })
                        OutlinedTextField(descricao, { descricao = it }, label = { Text("O que comeu") }, modifier = Modifier.fillMaxWidth())
                        CampoNumero(carbs, "Carboidratos", "g") { carbs = it }
                        if (atual == null) {
                            HorizontalDivider()
                            Text("Insulina para esta refeição (opcional)", style = MaterialTheme.typography.titleSmall)
                            CampoNumero(bolus, "Dose aplicada", "UI") { bolus = it }
                            OutlinedTextField(insulina, { insulina = it }, label = { Text("Insulina") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Sugestoes(insulinas) { insulina = it }
                        }
                    }
                    TipoRegistro.DOSE -> {
                        Rotulo("Tipo")
                        EscolhaChips(TipoDose.entries, tipoDose, { it.rotulo }, {
                            tipoDose = it
                            if (it == TipoDose.ORAL && unidade == "UI") unidade = "mg"
                        })
                        OutlinedTextField(medicamento, { medicamento = it }, label = { Text("Medicamento") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Sugestoes(medicamentos) { medicamento = it }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            CampoNumero(quantidade, "Quantidade", null, Modifier.weight(1f)) { quantidade = it }
                            OutlinedTextField(unidade, { unidade = it }, label = { Text("Unidade") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Sugestoes(listOf("UI", "mg", "comp.")) { unidade = it }
                    }
                    TipoRegistro.ATIVIDADE -> {
                        OutlinedTextField(tipoAtv, { tipoAtv = it }, label = { Text("Atividade") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Sugestoes(tiposAtv) { tipoAtv = it }
                        CampoNumero(duracao, "Duração", "min", decimal = false) { duracao = it }
                        Rotulo("Intensidade")
                        EscolhaChips(Intensidade.entries, intensidade, { it.rotulo }, { intensidade = it })
                    }
                }

                OutlinedTextField(obs, { obs = it }, label = { Text("Observação") }, modifier = Modifier.fillMaxWidth())

                erro?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }

                if (atual != null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { confirmarExclusao = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Excluir registro")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmarExclusao && atual != null) {
        AlertDialog(
            onDismissRequest = { confirmarExclusao = false },
            title = { Text("Excluir registro?") },
            text = { Text("Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = { confirmarExclusao = false; vm.remover(atual.id); onFechar() }) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarExclusao = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Rotulo(texto: String) {
    Text(texto, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CampoNumero(
    valor: String, rotulo: String, sufixo: String?, modifier: Modifier = Modifier.fillMaxWidth(),
    decimal: Boolean = true, onMudar: (String) -> Unit,
) {
    OutlinedTextField(
        value = valor,
        onValueChange = { novo -> if (novo.all { it.isDigit() || (decimal && (it == ',' || it == '.')) }) onMudar(novo) },
        label = { Text(rotulo) },
        suffix = if (sufixo != null) { { Text(sufixo) } } else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun Sugestoes(opcoes: List<String>, onEscolher: (String) -> Unit) {
    if (opcoes.isEmpty()) return
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opcoes.take(10).forEach { o -> SuggestionChip(onClick = { onEscolher(o) }, label = { Text(o) }) }
    }
}

/** Data e hora com os seletores nativos do Android. */
@Composable
private fun CampoDataHora(valor: LocalDateTime, rotulo: String, onMudar: (LocalDateTime) -> Unit) {
    val ctx = LocalContext.current
    Column {
        Rotulo(rotulo)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    DatePickerDialog(ctx, { _, a, m, d -> onMudar(LocalDate.of(a, m + 1, d).atTime(valor.toLocalTime())) },
                        valor.year, valor.monthValue - 1, valor.dayOfMonth).show()
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(valor.format(fData))
            }
            OutlinedButton(
                onClick = {
                    TimePickerDialog(ctx, { _, h, mi -> onMudar(valor.withHour(h).withMinute(mi)) }, valor.hour, valor.minute, true).show()
                },
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Outlined.Schedule, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(valor.format(fHora))
            }
        }
    }
}
