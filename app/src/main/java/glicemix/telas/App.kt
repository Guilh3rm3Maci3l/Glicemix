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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
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
    ANALISE("Análise", Icons.Outlined.BarChart),
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
    var secao by rememberSaveable { mutableStateOf(SecaoAnalise.PADROES) }
    var escolherNovo by remember { mutableStateOf(false) }
    var edicao by remember { mutableStateOf<Edicao?>(null) }
    var avisoHipo by remember { mutableStateOf<Int?>(null) }
    val avisos = remember { SnackbarHostState() }
    val escopo = rememberCoroutineScope()

    LaunchedEffect(pedido) {
        if (pedido != null) {
            edicao = Edicao(pedido)
            aoAtender()
        }
    }

    BackHandler(enabled = aba != Aba.PAINEL) { aba = Aba.PAINEL }

    /** Exclui e oferece "Desfazer" (uma refeição leva a insulina ligada junto, e volta junto). */
    fun excluir(r: Registro) {
        val removidos = vm.excluir(r)
        escopo.launch {
            avisos.currentSnackbarData?.dismiss()
            val res = avisos.showSnackbar(
                if (removidos.size > 1) "Refeição e insulina excluídas" else "Registro excluído",
                actionLabel = "Desfazer", duration = SnackbarDuration.Long,
            )
            if (res == SnackbarResult.ActionPerformed) vm.restaurar(removidos)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (aba == Aba.PAINEL) vm.config.nome.takeIf { it.isNotBlank() }?.let { "Olá, $it" } ?: "Glicemix" else aba.rotulo,
                        fontWeight = FontWeight.Bold,
                    )
                },
                actions = {
                    // Calendário e Comparar têm seus próprios períodos
                    if (!(aba == Aba.ANALISE && (secao == SecaoAnalise.CALENDARIO || secao == SecaoAnalise.COMPARAR))) {
                        SeletorPeriodo(vm.periodo, vm::mudarPeriodo)
                    }
                },
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
        snackbarHost = { SnackbarHost(avisos) },
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
                    aoVerPadroes = { secao = SecaoAnalise.PADROES; aba = Aba.ANALISE },
                    aoNovo = { edicao = Edicao(it) },
                    aoEditar = { edicao = Edicao(tipoDe(it), it) },
                )
                Aba.REGISTROS -> TelaRegistros(vm, aoEditar = { edicao = Edicao(tipoDe(it), it) })
                Aba.ANALISE -> TelaAnalise(vm, secao, { secao = it }, aoEditar = { edicao = Edicao(tipoDe(it), it) })
                Aba.AJUSTES -> TelaAjustes(vm)
            }
        }
    }

    if (escolherNovo) {
        ModalBottomSheet(onDismissRequest = { escolherNovo = false }) {
            RegistroRapido(
                vm,
                aoFechar = { escolherNovo = false },
                aoEscolher = { t -> escolherNovo = false; edicao = Edicao(t) },
                aoHipo = { avisoHipo = it },
            )
        }
    }

    edicao?.let { e ->
        EditorRegistro(e, vm, onFechar = { edicao = null }, onHipo = { avisoHipo = it }, onExcluir = ::excluir)
    }

    avisoHipo?.let { v -> AvisoHipoglicemia(v, vm.config) { avisoHipo = null } }
}

/** Registro “de mentira” só para reaproveitar o ícone e a cor de cada tipo. */
fun exemploDe(t: TipoRegistro): Registro {
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
