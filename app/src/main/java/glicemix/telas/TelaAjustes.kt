@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package glicemix.telas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import glicemix.app.GlicemixViewModel
import glicemix.model.Configuracao
import java.io.File

/** Mensagem simples exibida num diálogo (resultado de importação, relatório pronto, erros). */
private data class Aviso(val titulo: String, val texto: String, val arquivoPdf: File? = null)

@Composable
fun TelaAjustes(vm: GlicemixViewModel) {
    val ctx = LocalContext.current
    var confirmar by remember { mutableStateOf<String?>(null) } // "exemplo" | "apagar"
    var aviso by remember { mutableStateOf<Aviso?>(null) }

    val seletorBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        aviso = try {
            val texto = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: throw IllegalArgumentException("Não foi possível ler o arquivo.")
            val r = vm.importarBackup(texto)
            Aviso("Importação concluída",
                "${r.novos} registro(s) novo(s)\n${r.atualizados} atualizado(s)\n${r.iguais} já existia(m)" +
                    if (r.ignorados > 0) "\n${r.ignorados} linha(s) ignorada(s)" else "")
        } catch (e: IllegalArgumentException) {
            Aviso("Arquivo inválido", e.message ?: "Este arquivo não é um backup do Glicemix.")
        } catch (e: Exception) {
            Aviso("Não foi possível importar", e.message ?: e.toString())
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SecaoLembretes(vm)

        Cartao(titulo = "Levar para a consulta", subtitulo = "O relatório usa o período escolhido no topo da tela (${vm.periodo.rotulo.lowercase()}).") {
            Button(onClick = {
                aviso = try {
                    val pdf = vm.gerarRelatorio()
                    Aviso("Relatório pronto", "O relatório em PDF do período foi gerado. Você pode abri-lo ou enviá-lo ao seu médico.", pdf)
                } catch (e: Exception) {
                    Aviso("Não foi possível gerar o relatório", e.message ?: e.toString())
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Description, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Relatório em PDF")
            }
            OutlinedButton(onClick = { compartilhar(ctx, vm.exportarCsv(), "text/csv", "Glicemix – meus registros") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Outlined.TableChart, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Planilha (Excel / Google Planilhas)")
            }
        }

        Cartao(titulo = "Backup e app desktop", subtitulo = "Leve seus registros entre o celular e o computador. Importar o mesmo arquivo de novo não duplica nada.") {
            Button(onClick = { seletorBackup.launch(arrayOf("text/*", "application/octet-stream", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Importar backup")
            }
            OutlinedButton(onClick = { compartilhar(ctx, vm.exportarBackup(), "text/csv", "Glicemix – backup") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Exportar backup")
            }
            Text(
                "No computador: Configurações › Exportar backup para o celular. Envie o arquivo por e-mail, WhatsApp, Drive ou cabo USB e importe aqui.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp),
            )
        }

        CartaoFaixas(vm)

        Cartao(titulo = "Dados", subtitulo = "Os registros ficam somente neste aparelho e entram no backup automático do Android.") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { confirmar = "exemplo" }) { Text("Dados de exemplo") }
                Button(
                    onClick = { confirmar = "apagar" },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text("Apagar tudo") }
            }
        }

        Text(
            "Glicemix ${versao(ctx)} · os padrões e estimativas são informativos e não substituem orientação médica.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    confirmar?.let { acao ->
        AlertDialog(
            onDismissRequest = { confirmar = null },
            title = { Text(if (acao == "apagar") "Apagar todos os dados?" else "Carregar dados de exemplo?") },
            text = {
                Text(
                    if (acao == "apagar") "Todas as medições, refeições, doses e atividades serão apagadas permanentemente."
                    else "Serão carregados 30 dias de registros fictícios. Tudo o que já estiver salvo será SUBSTITUÍDO."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (acao == "apagar") vm.apagarTudo() else vm.carregarExemplo()
                    confirmar = null
                }) { Text("Continuar") }
            },
            dismissButton = { TextButton(onClick = { confirmar = null }) { Text("Cancelar") } },
        )
    }

    aviso?.let { a ->
        val pdf = a.arquivoPdf
        AlertDialog(
            onDismissRequest = { aviso = null },
            title = { Text(a.titulo) },
            text = { Text(a.texto) },
            confirmButton = {
                if (pdf != null) TextButton(onClick = { aviso = null; compartilhar(ctx, pdf, "application/pdf", "Glicemix – relatório") }) { Text("Enviar") }
                else TextButton(onClick = { aviso = null }) { Text("OK") }
            },
            dismissButton = {
                if (pdf != null) TextButton(onClick = { aviso = null; abrir(ctx, pdf, "application/pdf") }) { Text("Abrir") }
            },
        )
    }
}

@Composable
private fun CartaoFaixas(vm: GlicemixViewModel) {
    val ctx = LocalContext.current
    val c = vm.config
    var nome by remember(c) { mutableStateOf(c.nome) }
    var hipo by remember(c) { mutableStateOf(c.hipoSevera.toString()) }
    var min by remember(c) { mutableStateOf(c.alvoMin.toString()) }
    var max by remember(c) { mutableStateOf(c.alvoMax.toString()) }
    var hiper by remember(c) { mutableStateOf(c.hiperSevera.toString()) }
    var erro by remember { mutableStateOf<String?>(null) }

    Cartao(titulo = "Perfil e faixas-alvo", subtitulo = "Padrões do consenso internacional de Tempo no Alvo. Ajuste se sua equipe definiu metas diferentes.") {
        OutlinedTextField(nome, { nome = it }, label = { Text("Seu nome (aparece no relatório)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            CampoLimite(min, "Alvo mínimo", Modifier.weight(1f)) { min = it }
            CampoLimite(max, "Alvo máximo", Modifier.weight(1f)) { max = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
            CampoLimite(hipo, "Hipo grave abaixo de", Modifier.weight(1f)) { hipo = it }
            CampoLimite(hiper, "Hiper grave acima de", Modifier.weight(1f)) { hiper = it }
        }
        erro?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
            Button(onClick = {
                val n = Configuracao(nome.trim(), hipo.toIntOrNull() ?: -1, min.toIntOrNull() ?: -1, max.toIntOrNull() ?: -1, hiper.toIntOrNull() ?: -1)
                if (!(n.hipoSevera in 30..80 && n.hipoSevera < n.alvoMin && n.alvoMin < n.alvoMax && n.alvoMax < n.hiperSevera && n.hiperSevera <= 400)) {
                    erro = "Os limites precisam estar em ordem crescente: hipo grave < alvo mínimo < alvo máximo < hiper grave."
                } else {
                    erro = null
                    vm.salvarConfiguracao(n)
                    Toast.makeText(ctx, "Configurações salvas", Toast.LENGTH_SHORT).show()
                }
            }) { Text("Salvar") }
            TextButton(onClick = {
                val p = Configuracao(nome = nome)
                hipo = p.hipoSevera.toString(); min = p.alvoMin.toString(); max = p.alvoMax.toString(); hiper = p.hiperSevera.toString()
            }) { Text("Restaurar padrões") }
        }
    }
}

@Composable
private fun CampoLimite(valor: String, rotulo: String, modifier: Modifier, onMudar: (String) -> Unit) {
    OutlinedTextField(
        valor, { v -> if (v.all { it.isDigit() } && v.length <= 3) onMudar(v) },
        label = { Text(rotulo) }, suffix = { Text("mg/dL") }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

private fun uriDe(ctx: Context, arquivo: File) = FileProvider.getUriForFile(ctx, ctx.packageName + ".arquivos", arquivo)

/** Abre a folha de compartilhamento (e-mail, WhatsApp, Drive…). */
private fun compartilhar(ctx: Context, arquivo: File, tipo: String, assunto: String) {
    val envio = Intent(Intent.ACTION_SEND).apply {
        type = tipo
        putExtra(Intent.EXTRA_STREAM, uriDe(ctx, arquivo))
        putExtra(Intent.EXTRA_SUBJECT, assunto)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(envio, "Enviar ${arquivo.name}"))
}

private fun abrir(ctx: Context, arquivo: File, tipo: String) {
    val ver = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uriDe(ctx, arquivo), tipo)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try { ctx.startActivity(ver) } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, "Nenhum leitor de PDF instalado. Use Enviar para salvar no Drive.", Toast.LENGTH_LONG).show()
    }
}

private fun versao(ctx: Context): String =
    try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
