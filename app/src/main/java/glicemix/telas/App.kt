@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package glicemix.telas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import glicemix.analise.Periodo
import glicemix.app.GlicemixViewModel
import glicemix.model.Atividade
import glicemix.model.Configuracao
import glicemix.model.Dose
import glicemix.model.Medicao
import glicemix.model.Refeicao
import glicemix.model.Registro
import glicemix.model.TipoDose
import glicemix.model.TipoRefeicao
import glicemix.model.MomentoMedicao
import glicemix.model.Intensidade
import java.time.LocalDateTime

enum class Aba(val rotulo: String, val icone: ImageVector) {
    PAINEL("Painel", Icons.Outlined.Home),
    REGISTROS("Registros", Icons.Outlined.CalendarMonth),
    GRAFICOS("Gráficos", Icons.Outlined.BarChart),
    PADROES("Padrões", Icons.Outlined.Lightbulb),
    AJUSTES("Ajustes", Icons.Outlined.Settings),
}

enum class TipoRegistro(val rotulo: String, val descricao: String) {
    MEDICAO("Medição", "Glicemia capilar ou do sensor"),
    REFEICAO("Refeição", "O que comeu e os carboidratos"),
    DOSE("Dose", "Insulina ou medicamento aplicado"),
    ATIVIDADE("Atividade física", "Exercício, duração e intensidade"),
}

/** Registro sendo criado (registro == null) ou editado. */
data class Edicao(val tipo: TipoRegistro, val registro: Registro? = null)

fun tipoDe(r: Registro): TipoRegistro = when (r) {
    is Medicao -> TipoRegistro.MEDICAO
    is Refeicao -> TipoRegistro.REFEICAO
    is Dose -> TipoRegistro.DOSE
    is Atividade -> TipoRegistro.ATIVIDADE
}

/**
 * @param pedido formulário pedido por uma notificação de lembrete (abre assim que o app aparece).
 */
@Composable
fun GlicemixApp(vm: GlicemixViewModel = viewModel(), pedido: TipoRegistro? = null, aoAtender: () -> Unit = {}) {
    var aba by rememberSaveable { mutableStateOf(Aba.PAINEL) }
    var escolherNovo by remember { mutableStateOf(false) }
    var edicao by remember { mutableStateOf<Edicao?>(null) }
    var avisoHipo by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(pedido) {
        if (pedido != null) {
            edicao = Edicao(pedido)
            aoAtender()
        }
    }

    BackHandler(enabled = aba != Aba.PAINEL) { aba = Aba.PAINEL }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            if (aba == Aba.PAINEL) vm.config.nome.takeIf { it.isNotBlank() }?.let { "Olá, $it" } ?: "Glicemix" else aba.rotulo,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                actions = { SeletorPeriodo(vm.periodo, vm::mudarPeriodo) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar {
                Aba.entries.forEach { a ->
                    NavigationBarItem(
                        selected = aba == a,
                        onClick = { aba = a },
                        icon = { Icon(a.icone, contentDescription = null) },
                        label = { Text(a.rotulo) },
                    )
                }
            }
        },
        floatingActionButton = {
            if (aba == Aba.PAINEL || aba == Aba.REGISTROS) {
                ExtendedFloatingActionButton(
                    onClick = { escolherNovo = true },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Registrar") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (aba) {
                Aba.PAINEL -> TelaPainel(
                    vm,
                    aoVerPadroes = { aba = Aba.PADROES },
                    aoNovo = { edicao = Edicao(it) },
                    aoEditar = { edicao = Edicao(tipoDe(it), it) },
                )
                Aba.REGISTROS -> TelaRegistros(vm, aoEditar = { edicao = Edicao(tipoDe(it), it) })
                Aba.GRAFICOS -> TelaGraficos(vm)
                Aba.PADROES -> TelaPadroes(vm)
                Aba.AJUSTES -> TelaAjustes(vm)
            }
        }
    }

    if (escolherNovo) {
        ModalBottomSheet(onDismissRequest = { escolherNovo = false }) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
                Text("Novo registro", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                TipoRegistro.entries.forEach { t ->
                    val exemplo: Registro = exemploDe(t)
                    Row(
                        Modifier.fillMaxWidth().clickable { escolherNovo = false; edicao = Edicao(t) }.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconeRegistro(exemplo, 44.dp)
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(t.rotulo, style = MaterialTheme.typography.titleMedium)
                            Text(t.descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    edicao?.let { e ->
        EditorRegistro(e, vm, onFechar = { edicao = null }, onHipo = { avisoHipo = it })
    }

    avisoHipo?.let { v -> AvisoHipoglicemia(v, vm.config) { avisoHipo = null } }
}

/** Registro “de mentira” só para reaproveitar o ícone e a cor de cada tipo. */
private fun exemploDe(t: TipoRegistro): Registro {
    val agora = LocalDateTime.now()
    return when (t) {
        TipoRegistro.MEDICAO -> Medicao(dataHora = agora, valor = 100, momento = MomentoMedicao.ALEATORIA)
        TipoRegistro.REFEICAO -> Refeicao(dataHora = agora, tipo = TipoRefeicao.ALMOCO, descricao = "", carboidratos = 0.0)
        TipoRegistro.DOSE -> Dose(dataHora = agora, tipo = TipoDose.ULTRARRAPIDA, medicamento = "", quantidade = 0.0)
        TipoRegistro.ATIVIDADE -> Atividade(dataHora = agora, tipo = "", duracaoMin = 0, intensidade = Intensidade.LEVE)
    }
}

@Composable
private fun SeletorPeriodo(atual: Periodo, onMudar: (Periodo) -> Unit) {
    var aberto by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { aberto = true }) {
            Text(atual.rotulo.replace("Últimos ", ""))
            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Escolher período")
        }
        DropdownMenu(expanded = aberto, onDismissRequest = { aberto = false }) {
            Periodo.entries.forEach { p ->
                DropdownMenuItem(text = { Text(p.rotulo) }, onClick = { aberto = false; onMudar(p) })
            }
        }
    }
}

@Composable
fun AvisoHipoglicemia(valor: Int, cfg: Configuracao, onFechar: () -> Unit) {
    val grave = valor < cfg.hipoSevera
    AlertDialog(
        onDismissRequest = onFechar,
        confirmButton = { TextButton(onClick = onFechar) { Text("Entendi") } },
        title = { Text("Glicemia de $valor mg/dL") },
        text = {
            Text(
                "Está abaixo do alvo. Regra dos 15: consuma 15 g de carboidrato de ação rápida (meio copo de suco ou refrigerante comum, " +
                    "1 colher de sopa de açúcar) e meça novamente em 15 minutos. Repita se continuar abaixo de ${cfg.alvoMin} mg/dL." +
                    if (grave) "\n\nSe houver confusão, desmaio ou dificuldade para engolir, procure atendimento de emergência (SAMU 192)." else ""
            )
        },
    )
}
