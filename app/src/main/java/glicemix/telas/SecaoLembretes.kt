@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package glicemix.telas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import glicemix.app.GlicemixViewModel
import glicemix.lembretes.Agendador
import glicemix.lembretes.Lembrete
import glicemix.lembretes.Notificacoes

/** Cartão de lembretes da tela Ajustes: lista, criação, permissões e lembrete pós-refeição. */
@Composable
fun SecaoLembretes(vm: GlicemixViewModel) {
    val ctx = LocalContext.current
    var editando by remember { mutableStateOf<Lembrete?>(null) }
    var novo by remember { mutableStateOf(false) }

    // Reavalia as permissões sempre que o app volta para a frente (ex.: depois de abrir as configurações).
    var verificacao by remember { mutableIntStateOf(0) }
    val ciclo = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(ciclo) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) verificacao++ }
        ciclo.lifecycle.addObserver(obs)
        onDispose { ciclo.lifecycle.removeObserver(obs) }
    }
    val notificacoesOk = remember(verificacao) { Notificacoes.permitidas(ctx) }
    val exatoOk = remember(verificacao) { Agendador.podeHorarioExato(ctx) }
    val pedirPermissao = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { verificacao++ }
    fun garantirPermissao() {
        if (!Notificacoes.permitidas(ctx) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            pedirPermissao.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    Cartao(titulo = "Lembretes", subtitulo = "Notificações para não esquecer medições, insulina e medicamentos. Toque na notificação para registrar.") {
        if (!notificacoesOk) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(Icons.Outlined.NotificationsOff, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(8.dp))
                Text("As notificações estão desativadas para o Glicemix.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { garantirPermissao(); if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) abrirConfigNotificacoes(ctx) }) { Text("Permitir") }
            }
        }
        if (!exatoOk) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Icon(Icons.Outlined.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text("Sem permissão de horário exato, o lembrete pode atrasar até 10 minutos.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                        ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName)))
                }) { Text("Ajustar") }
            }
        }

        if (vm.lembretes.isEmpty()) {
            Text("Nenhum lembrete. Comece por uma sugestão:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Lembrete.SUGESTOES.forEach { (titulo, horario, abrir) ->
                    SuggestionChip(
                        onClick = {
                            vm.salvarLembrete(Lembrete(titulo = titulo, hora = horario.first, minuto = horario.second, abrir = abrir))
                            garantirPermissao()
                        },
                        label = { Text("$titulo · %02d:%02d".format(horario.first, horario.second)) },
                    )
                }
            }
        }

        vm.lembretes.forEachIndexed { i, l ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier.fillMaxWidth().clickable { editando = l; novo = false }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(l.horario, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                    color = if (l.ativo) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.titulo, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    Text("${l.descricaoDias} · abre ${l.abrir.rotulo.lowercase()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = l.ativo, onCheckedChange = { vm.salvarLembrete(l.copy(ativo = it)); if (it) garantirPermissao() })
            }
        }

        OutlinedButton(onClick = { editando = Lembrete(titulo = "", hora = 8, minuto = 0); novo = true }, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text("Novo lembrete")
        }

        HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Medir 2h após cada refeição", style = MaterialTheme.typography.bodyLarge)
                Text("Ao registrar uma refeição, o Glicemix avisa a hora de medir. Isso melhora a análise das refeições.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = vm.lembrarPosRefeicao, onCheckedChange = { vm.definirPosRefeicao(it); if (it) garantirPermissao() })
        }
    }

    editando?.let { l ->
        EditorLembrete(
            inicial = l, novo = novo,
            onSalvar = { vm.salvarLembrete(it); garantirPermissao(); editando = null },
            onExcluir = { vm.removerLembrete(l); editando = null },
            onFechar = { editando = null },
        )
    }
}

private fun abrirConfigNotificacoes(ctx: android.content.Context) {
    ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
}

@Composable
private fun EditorLembrete(inicial: Lembrete, novo: Boolean, onSalvar: (Lembrete) -> Unit, onExcluir: () -> Unit, onFechar: () -> Unit) {
    val ctx = LocalContext.current
    var titulo by remember { mutableStateOf(inicial.titulo) }
    var hora by remember { mutableIntStateOf(inicial.hora) }
    var minuto by remember { mutableIntStateOf(inicial.minuto) }
    var dias by remember { mutableStateOf(inicial.dias) }
    var abrir by remember { mutableStateOf(inicial.abrir) }
    var erro by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onFechar,
        title = { Text(if (novo) "Novo lembrete" else "Editar lembrete") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(titulo, { titulo = it }, label = { Text("Lembrar de…") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Lembrete.SUGESTOES.forEach { (t, _, a) -> SuggestionChip(onClick = { titulo = t; abrir = a }, label = { Text(t) }) }
                }
                OutlinedButton(onClick = {
                    TimePickerDialog(ctx, { _, h, m -> hora = h; minuto = m }, hora, minuto, true).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("%02d:%02d".format(hora, minuto), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
                Text("Dias", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Lembrete.ORDEM.forEach { d ->
                        FilterChip(
                            selected = d in dias,
                            onClick = { dias = if (d in dias) dias - d else dias + d },
                            label = { Text(Lembrete.LETRA.getValue(d)) },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { dias = Lembrete.ORDEM.toSet() }) { Text("Todos") }
                    TextButton(onClick = { dias = Lembrete.DIAS_UTEIS }) { Text("Dias úteis") }
                }
                Text("Ao tocar na notificação, abrir", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                EscolhaChips(TipoRegistro.entries, abrir, { it.rotulo }, { abrir = it })
                erro?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (!novo) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onExcluir) { Text("Excluir lembrete", color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    titulo.isBlank() -> erro = "Escreva do que você quer ser lembrado."
                    dias.isEmpty() -> erro = "Escolha pelo menos um dia."
                    else -> onSalvar(inicial.copy(titulo = titulo.trim(), hora = hora, minuto = minuto, dias = dias, abrir = abrir, ativo = true))
                }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onFechar) { Text("Cancelar") } },
    )
}
