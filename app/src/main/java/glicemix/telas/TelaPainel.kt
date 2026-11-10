@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package glicemix.telas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import glicemix.analise.Nivel
import glicemix.analise.Padrao
import glicemix.app.GlicemixViewModel
import glicemix.graficos.BarraTempoNoAlvo
import glicemix.graficos.GraficoLinhaTempo
import glicemix.model.Faixa
import glicemix.model.Registro
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

@Composable
fun TelaPainel(vm: GlicemixViewModel, aoVerPadroes: () -> Unit, aoNovo: (TipoRegistro) -> Unit, aoEditar: (Registro) -> Unit) {
    val d = vm.dados
    val cfg = d.config
    val r = vm.resumo
    var janela by rememberSaveable { mutableStateOf(3) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (vm.vazio) {
            Cartao(titulo = "Bem-vindo ao Glicemix", subtitulo = "Registre medições, refeições, doses e atividades para acompanhar seu controle e descobrir padrões.") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { aoNovo(TipoRegistro.MEDICAO) }) { Text("Registrar medição") }
                    OutlinedButton(onClick = { vm.carregarExemplo() }) { Text("Dados de exemplo") }
                }
            }
        }

        // Última glicemia em destaque
        val ult = d.medicoes.lastOrNull()
        Cartao {
            if (ult == null) {
                Text("Nenhuma medição no período", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val cor = Cores.faixa(cfg.classificar(ult.valor))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { aoEditar(ult) }) {
                    Column(Modifier.weight(1f)) {
                        Text("ÚLTIMA GLICEMIA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("${ult.valor}", fontSize = 44.sp, fontWeight = FontWeight.Bold, color = cor)
                            Spacer(Modifier.width(6.dp))
                            Text("mg/dL", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 9.dp))
                        }
                        Text("${haQuanto(ult.dataHora)} · ${ult.momento.rotulo}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Etiqueta(cfg.classificar(ult.valor).rotulo, cor)
                }
            }
        }

        // Indicadores
        if (r != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Indicador("Média", "${r.media.roundToInt()}", "mg/dL", "± ${r.desvio.roundToInt()} · ${r.n} medições", null, Modifier.weight(1f))
                Indicador("HbA1c estimada", "%.1f".format(r.gmi), "%", "GMI, pela média",
                    if (r.gmi < 7) Cores.faixa(Faixa.NO_ALVO) else if (r.gmi < 8) Cores.faixa(Faixa.ALTA) else Cores.faixa(Faixa.MUITO_ALTA), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Indicador("Tempo no alvo", "${r.tempoNoAlvo.roundToInt()}", "%", "Meta > 70%",
                    if (r.tempoNoAlvo >= 70) Cores.faixa(Faixa.NO_ALVO) else if (r.tempoNoAlvo >= 50) Cores.faixa(Faixa.ALTA) else Cores.faixa(Faixa.BAIXA), Modifier.weight(1f))
                Indicador("Variabilidade", "%.0f".format(r.cv), "% CV", "Meta ≤ 36%",
                    if (r.cv <= 36) Cores.faixa(Faixa.NO_ALVO) else Cores.faixa(Faixa.ALTA), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Indicador("Hipoglicemias", "${r.episodiosHipo}", if (r.episodiosHipo == 1) "episódio" else "episódios",
                    "%.1f%% < ${cfg.alvoMin}".format(r.tempoAbaixo), if (r.episodiosHipo == 0) Cores.faixa(Faixa.NO_ALVO) else Cores.faixa(Faixa.BAIXA), Modifier.weight(1f))
                Indicador("Medições/dia", "%.1f".format(r.medicoesPorDia), "", "${r.diasComDados} dias com dados", null, Modifier.weight(1f))
            }

            Cartao(titulo = "Tempo nas faixas", subtitulo = "Meta: mais de 70% no alvo e menos de 4% abaixo") {
                BarraTempoNoAlvo(r.percentualPorFaixa)
                Spacer(Modifier.height(10.dp))
                Faixa.entries.reversed().forEach { f ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(Cores.faixa(f), RoundedCornerShape(3.dp)))
                        Spacer(Modifier.width(8.dp))
                        Text(f.rotulo, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("${(r.percentualPorFaixa[f] ?: 0.0).roundToInt()}%", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Linha do tempo recente
        if (d.medicoes.isNotEmpty()) {
            Cartao(titulo = "Glicemia recente") {
                EscolhaChips(listOf(1, 3, 7), janela, { if (it == 1) "24 horas" else "$it dias" }, { janela = it })
                Spacer(Modifier.height(8.dp))
                val fim = LocalDateTime.now().plusMinutes(30)
                val ini = if (janela == 1) LocalDateTime.now().minusHours(24) else LocalDate.now().minusDays(janela - 1L).atStartOfDay()
                GraficoLinhaTempo(d, ini, fim)
                Legenda("Refeição" to Cores.refeicao, "Insulina" to Cores.dose, "Basal" to Cores.basal, "Atividade" to Cores.atividade)
            }
        }

        // Padrões principais
        val principais = vm.padroes.filter { it.nivel != Nivel.POSITIVO }.take(3).ifEmpty { vm.padroes.take(3) }
        if (principais.isNotEmpty()) {
            Cartao(titulo = "Principais padrões") {
                principais.forEach { LinhaPadrao(it); Spacer(Modifier.height(10.dp)) }
                TextButton(onClick = aoVerPadroes) { Text("Ver análise completa") }
            }
        }

        // Hoje
        val hoje = (d.medicoes + d.refeicoes + d.doses + d.atividades)
            .filter { it.dataHora.toLocalDate() == LocalDate.now() }.sortedByDescending { it.dataHora }
        Cartao(titulo = "Hoje") {
            if (hoje.isEmpty()) Text("Nada registrado hoje ainda.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            hoje.take(15).forEach { LinhaRegistro(it, cfg, Modifier.clickable { aoEditar(it) }) }
        }
    }
}

@Composable
fun LinhaPadrao(p: Padrao) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().background(Cores.nivel(p.nivel), RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(p.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text(p.descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
