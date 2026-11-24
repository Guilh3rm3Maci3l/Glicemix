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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.sp
import glicemix.model.resumo
import glicemix.model.totalCarboidratos
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.navigationBarsPadding
import glicemix.model.TipoDose
import glicemix.model.TipoRefeicao
import glicemix.model.novoId
import glicemix.model.momentoSugerido
import glicemix.model.refeicaoSugerida
import java.time.LocalDateTime
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
import java.time.LocalDate

private fun decimal(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

/**
 * Formulário em tela cheia para criar ou editar qualquer tipo de registro.
 * [onExcluir] recebe o registro apagado para a tela oferecer "Desfazer".
 */
@Composable
fun EditorRegistro(e: Edicao, vm: GlicemixViewModel, onFechar: () -> Unit, onHipo: (Int) -> Unit, onExcluir: (Registro) -> Unit = {}) {
    val atual = e.registro
    val med = atual as? Medicao
    val ref = atual as? Refeicao
    val dose = atual as? Dose
    val atv = atual as? Atividade
    val inicial = remember {
        atual?.dataHora ?: LocalDateTime.now().withSecond(0).withNano(0).let { if (e.tipo == TipoRegistro.ATIVIDADE) it.minusMinutes(30) else it }
    }
    // dose ligada a esta refeição (ao editar)
    val doseLigada = remember { ref?.let { r -> vm.dosesDe(r.id).firstOrNull { it.tipo.ehBolus } } }

    var quando by remember { mutableStateOf(inicial) }
    var obs by remember { mutableStateOf(atual?.observacao ?: "") }
    var tags by remember { mutableStateOf(atual?.tags ?: emptyList()) }
    var marcado by remember { mutableStateOf(atual?.marcado ?: false) }
    var erro by remember { mutableStateOf<String?>(null) }
    var confirmarExclusao by remember { mutableStateOf(false) }
    val etiquetasDisponiveis = remember { vm.etiquetas() }

    // medição
    var valor by remember { mutableStateOf(med?.valor?.toString() ?: "") }
    var momento by remember { mutableStateOf(med?.momento ?: momentoSugerido(inicial)) }
    // refeição
    var tipoRef by remember { mutableStateOf(ref?.tipo ?: refeicaoSugerida(inicial)) }
    var descricao by remember { mutableStateOf(ref?.descricao ?: "") }
    var carbs by remember { mutableStateOf(if (ref?.itens.isNullOrEmpty()) ref?.carboidratos?.let { num(it) } ?: "" else "") }
    var itens by remember { mutableStateOf(ref?.itens ?: emptyList()) }
    var selecionando by remember { mutableStateOf(false) }
    var bolus by remember { mutableStateOf(doseLigada?.quantidade?.let { num(it) } ?: "") }
    var tipoBolus by remember { mutableStateOf(doseLigada?.tipo ?: TipoDose.ULTRARRAPIDA) }
    val insulinas = remember { vm.insulinasBolus().ifEmpty { listOf("Fiasp", "Novorapid", "Humalog", "Apidra") } }
    var insulina by remember { mutableStateOf(doseLigada?.medicamento ?: insulinas.first()) }
    var favorita by remember { mutableStateOf(ref?.let { vm.ehFavorita(it.descricao) } ?: false) }
    val modelos = remember { if (atual == null && e.tipo == TipoRegistro.REFEICAO) vm.modelosRefeicao() else emptyList() }
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
                vm.salvar(Medicao(id, quando, v, momento, obs.trim(), tags, marcado))
                onFechar()
                if (atual == null && v < vm.config.alvoMin) onHipo(v)
            }
            TipoRegistro.REFEICAO -> {
                // com alimentos pesados, o total é a soma deles; sem alimentos, vale o que foi digitado
                val c = if (itens.isNotEmpty()) itens.totalCarboidratos() else if (carbs.isBlank()) 0.0 else decimal(carbs)
                if (c == null || c < 0) { erro = "Carboidratos inválidos."; return }
                if (descricao.isBlank() && itens.isEmpty() && c == 0.0) { erro = "Informe o que comeu ou a quantidade de carboidratos."; return }
                val b = if (bolus.isBlank()) 0.0 else decimal(bolus)
                if (b == null || b < 0) { erro = "Dose de insulina inválida."; return }
                val r = Refeicao(id, quando, tipoRef, descricao.trim().ifBlank { itens.resumo() }, c, obs.trim(), tags, marcado, itens)
                // a dose fica ligada à refeição pelo id: editar ou apagar a refeição leva a dose junto
                val novaDose = if (b > 0) Dose(
                    id = doseLigada?.id ?: novoId(), dataHora = quando, tipo = tipoBolus, medicamento = insulina.trim().ifBlank { "Insulina" },
                    quantidade = b, observacao = "${tipoRef.rotulo} (${c.toInt()} g)", refeicaoId = id, tags = tags,
                ) else null
                vm.definirFavorita(r.descricao, favorita)
                if (atual == null) vm.salvarTodos(listOfNotNull(r, novaDose))
                else {
                    vm.salvar(r)
                    if (novaDose != null) vm.salvar(novaDose) else doseLigada?.let { vm.remover(it.id) }
                }
                onFechar()
            }
            TipoRegistro.DOSE -> {
                val q = decimal(quantidade)
                if (medicamento.isBlank()) { erro = "Informe o medicamento."; return }
                if (q == null || q <= 0) { erro = "A quantidade deve ser maior que zero."; return }
                vm.salvar(Dose(id, quando, tipoDose, medicamento.trim(), q, unidade.trim().ifBlank { "UI" }, obs.trim(), dose?.refeicaoId ?: "", tags, marcado))
                onFechar()
            }
            TipoRegistro.ATIVIDADE -> {
                val dmin = duracao.trim().toIntOrNull()
                if (tipoAtv.isBlank()) { erro = "Informe a atividade."; return }
                if (dmin == null || dmin !in 1..600) { erro = "Informe a duração em minutos."; return }
                vm.salvar(Atividade(id, quando, tipoAtv.trim(), dmin, intensidade, obs.trim(), tags, marcado))
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
                )
            },
            // Salvar embaixo, ao alcance do polegar
            bottomBar = {
                Button(
                    onClick = { salvar() },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp).height(52.dp),
                ) { Text("Salvar", fontWeight = FontWeight.Bold) }
            },
        ) { pad ->
            Column(
                Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
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
                        if (modelos.isNotEmpty()) {
                            Rotulo("Repetir uma refeição anterior")
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                modelos.forEach { m ->
                                    SuggestionChip(
                                        onClick = {
                                            descricao = m.descricao; itens = m.itens
                                            carbs = if (m.itens.isEmpty()) num(m.carboidratos) else ""
                                            bolus = if (m.bolus > 0) num(m.bolus) else ""
                                            if (m.insulina.isNotBlank()) insulina = m.insulina
                                            favorita = m.favorita
                                        },
                                        label = { Text((if (m.favorita) "★ " else "") + "${m.descricao} · ${num(m.carboidratos)} g" + if (m.bolus > 0) " · ${num(m.bolus)} UI" else "") },
                                    )
                                }
                            }
                        }
                        Rotulo("Refeição")
                        EscolhaChips(TipoRefeicao.entries, tipoRef, { it.rotulo }, { tipoRef = it })
                        OutlinedTextField(descricao, { descricao = it }, label = { Text("O que comeu") }, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Refeição favorita", style = MaterialTheme.typography.bodyLarge)
                                Text("Aparece primeiro em \"Repetir uma refeição anterior\".", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = favorita, onCheckedChange = { favorita = it })
                        }
                        Rotulo("Alimentos (opcional): conte pelos carboidratos da TACO ou do rótulo")
                        itens.forEachIndexed { i, item ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(item.nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${num(item.gramas)} g · ${num(Math.round(item.carboidratos * 10) / 10.0)} g de carboidratos · ${item.origem.rotulo}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(onClick = { itens = itens.filterIndexed { j, _ -> j != i } }) {
                                    Icon(Icons.Outlined.Delete, contentDescription = "Remover ${item.nome}")
                                }
                            }
                        }
                        OutlinedButton(onClick = { selecionando = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(if (itens.isEmpty()) "Adicionar alimento" else "Adicionar outro alimento")
                        }
                        if (itens.isNotEmpty()) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text("Total de carboidratos", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                Text("${num(itens.totalCarboidratos())} g", style = Numeros, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { carbs = num(itens.totalCarboidratos()); itens = emptyList() }) { Text("Digitar o total manualmente") }
                        } else {
                            CampoNumero(carbs, "Carboidratos", "g") { carbs = it }
                        }
                        HorizontalDivider()
                        Text("Insulina para esta refeição (opcional)", style = MaterialTheme.typography.titleSmall)
                        val carbosAgora = if (itens.isNotEmpty()) itens.totalCarboidratos() else (decimal(carbs) ?: 0.0)
                        val referencia = remember(carbosAgora, tipoRef) { vm.referenciaDose(tipoRef, carbosAgora, ref?.id) }
                        if (referencia != null) CartaoReferenciaDose(referencia, vm.config.alvoMin, tipoRef.rotulo)
                        CampoNumero(bolus, "Dose aplicada", "UI") { bolus = it }
                        Rotulo("Tipo de insulina")
                        EscolhaChips(listOf(TipoDose.ULTRARRAPIDA, TipoDose.RAPIDA), tipoBolus, { if (it == TipoDose.ULTRARRAPIDA) "Ultrarrápida" else "Rápida (regular)" }, { tipoBolus = it })
                        OutlinedTextField(insulina, { insulina = it }, label = { Text("Insulina") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Sugestoes(insulinas) { insulina = it }
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

                Rotulo("Circunstâncias (ajudam a explicar dias fora do padrão)")
                EscolhaEtiquetas(etiquetasDisponiveis, tags, { tags = it })

                OutlinedTextField(obs, { obs = it }, label = { Text("Observação") }, modifier = Modifier.fillMaxWidth())

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Levar para a consulta", style = MaterialTheme.typography.bodyLarge)
                        Text("Entra numa seção própria do relatório em PDF.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = marcado, onCheckedChange = { marcado = it })
                }

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

    if (selecionando) {
        SeletorAlimento(vm, aoAdicionar = { itens = itens + it }, aoFechar = { selecionando = false })
    }

    if (confirmarExclusao && atual != null) {
        AlertDialog(
            onDismissRequest = { confirmarExclusao = false },
            title = { Text("Excluir registro?") },
            text = { Text(if (doseLigada != null) "A dose de insulina ligada a esta refeição também será excluída. Você poderá desfazer logo em seguida." else "Você poderá desfazer logo em seguida.") },
            confirmButton = {
                TextButton(onClick = { confirmarExclusao = false; onExcluir(atual); onFechar() }) {
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmarExclusao = false }) { Text("Cancelar") } },
        )
    }
}

/**
 * O que a própria pessoa registrou em refeições parecidas. Não sugere dose: mostra o que foi aplicado e o resultado,
 * para ela e a equipe de saúde decidirem. Não preenche nenhum campo.
 */
@Composable
private fun CartaoReferenciaDose(r: glicemix.analise.Episodios.ReferenciaDose, alvoMin: Int, tipo: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Seu histórico com refeições parecidas", style = MaterialTheme.typography.titleSmall)
            Text(
                "${r.n} vezes (${tipo.lowercase()}, de ${num(r.carbosMin)} a ${num(r.carbosMax)} g): você aplicou de ${num(r.minimo)} a ${num(r.maximo)} UI, mediana de ${num(r.mediana)} UI.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (r.comResultado > 0) {
                Text(
                    "Glicemia 2 h depois (${r.comResultado} com medição): ${r.noAlvo} no alvo, ${r.acima} acima, ${r.abaixo} abaixo" +
                        (r.mediaDepois?.let { ", média de ${it.toInt()} mg/dL" } ?: "") + ".",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (r.abaixo > 0) {
                Text("▼ Em ${r.abaixo} " + (if (r.abaixo == 1) "dessas vezes" else "dessas vezes") + " a glicemia ficou abaixo de $alvoMin mg/dL.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Cores.faixa(glicemix.model.Faixa.BAIXA))
            }
            Text(
                "É só o que você registrou, não uma recomendação de dose. Combine qualquer ajuste com sua equipe de saúde.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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
