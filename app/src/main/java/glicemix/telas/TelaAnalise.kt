@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package glicemix.telas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import glicemix.analise.Dados
import glicemix.analise.Episodios
import glicemix.analise.Estatistica
import glicemix.analise.Resumo
import glicemix.app.GlicemixViewModel
import glicemix.graficos.GraficoLinhaTempo
import glicemix.model.Faixa
import glicemix.model.Medicao
import glicemix.model.Refeicao
import glicemix.model.Registro
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as NomeData
import kotlin.math.abs
import kotlin.math.roundToInt

/** "O que está acontecendo?" em quatro olhares: padrões, gráficos, calendário e comparação. */
enum class SecaoAnalise(val rotulo: String) { PADROES("Padrões"), GRAFICOS("Gráficos"), CALENDARIO("Calendário"), COMPARAR("Comparar") }

@Composable
fun TelaAnalise(vm: GlicemixViewModel, secao: SecaoAnalise, aoMudar: (SecaoAnalise) -> Unit, aoEditar: (Registro) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        EscolhaChips(SecaoAnalise.entries, secao, { it.rotulo }, aoMudar, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Box(Modifier.weight(1f)) {
            when (secao) {
                SecaoAnalise.PADROES -> TelaPadroes(vm)
                SecaoAnalise.GRAFICOS -> TelaGraficos(vm)
                SecaoAnalise.CALENDARIO -> TelaCalendario(vm, aoEditar)
                SecaoAnalise.COMPARAR -> TelaComparar(vm)
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Calendário: cada dia colorido pelo tempo no alvo; tocar abre o dia
// ---------------------------------------------------------------------------------------------

private fun corDoDia(tir: Double): Color = when {
    tir >= 70 -> Cores.faixa(Faixa.NO_ALVO)
    tir >= 50 -> Cores.faixa(Faixa.ALTA)
    else -> Cores.faixa(Faixa.BAIXA)
}

@Composable
fun TelaCalendario(vm: GlicemixViewModel, aoEditar: (Registro) -> Unit) {
    val cfg = vm.config
    var mesesAtras by rememberSaveable { mutableStateOf(0) }
    var diaEscolhido by rememberSaveable { mutableStateOf<String?>(null) }
    val mes = YearMonth.now().minusMonths(mesesAtras.toLong())
    // vm.dados muda a cada alteração nos registros: serve de chave para recalcular
    val porDia = remember(vm.dados, cfg) { Estatistica.porDia(vm.repo.medicoes, cfg).associateBy { it.data } }
    val comHipo = remember(vm.dados, cfg) { vm.repo.medicoes.filter { it.valor < cfg.alvoMin }.map { it.dataHora.toLocalDate() }.toSet() }
    val dia = diaEscolhido?.let { LocalDate.parse(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Cartao {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { mesesAtras++; diaEscolhido = null }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Mês anterior") }
                Text(
                    mes.month.getDisplayName(NomeData.FULL, ptBR).replaceFirstChar { it.uppercase() } + " de ${mes.year}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { mesesAtras--; diaEscolhido = null }, enabled = mesesAtras > 0) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Próximo mês") }
            }
            Row(Modifier.fillMaxWidth()) {
                listOf("D", "S", "T", "Q", "Q", "S", "S").forEach {
                    Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val vazios = mes.atDay(1).dayOfWeek.value % 7 // domingo = 0
            val celulas = vazios + mes.lengthOfMonth()
            val linhas = (celulas + 6) / 7
            for (l in 0 until linhas) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0 until 7) {
                        val n = l * 7 + c - vazios + 1
                        if (n < 1 || n > mes.lengthOfMonth()) { Spacer(Modifier.weight(1f)); continue }
                        val data = mes.atDay(n)
                        val d = porDia[data]
                        val tir = d?.porFaixa?.get(Faixa.NO_ALVO)
                        val hipo = data in comHipo
                        val escolhido = data == dia
                        val desc = buildString {
                            append(data.format(DateTimeFormatter.ofPattern("d 'de' MMMM", ptBR)))
                            if (tir == null) append(": sem medições") else append(": ${tir.roundToInt()}% no alvo, ${d?.n} medições")
                            if (hipo) append(", com hipoglicemia")
                        }
                        Column(
                            Modifier.weight(1f).aspectRatio(0.8f)
                                .background(
                                    if (tir != null) corDoDia(tir).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                    RoundedCornerShape(8.dp),
                                )
                                .border(if (escolhido) 2.dp else 0.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                                .clickable { diaEscolhido = data.toString() }
                                .semantics { contentDescription = desc }
                                .padding(2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                        ) {
                            Text("$n", style = Numeros, fontWeight = if (escolhido) FontWeight.ExtraBold else FontWeight.Medium, fontSize = 14.sp,
                                color = if (escolhido) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(if (tir != null) "${tir.roundToInt()}%" else "", style = Numeros, fontSize = 12.sp, color = if (tir != null) corDoDia(tir) else Color.Transparent)
                            Text(if (hipo) "▼" else " ", fontSize = 12.sp, color = Cores.faixa(Faixa.BAIXA))
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Legenda("70% ou mais no alvo" to Cores.faixa(Faixa.NO_ALVO), "50–69%" to Cores.faixa(Faixa.ALTA), "Menos de 50%" to Cores.faixa(Faixa.BAIXA))
            Text("▼ = houve glicemia abaixo de ${cfg.alvoMin} mg/dL no dia. O número é o % do dia no alvo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }

        if (dia == null) {
            Text("Toque num dia para ver o gráfico e os registros dele.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            DetalheDia(vm, dia, aoEditar)
        }
    }
}

@Composable
private fun DetalheDia(vm: GlicemixViewModel, dia: LocalDate, aoEditar: (Registro) -> Unit) {
    val cfg = vm.config
    val d = remember(vm.dados, dia) { Dados.entre(vm.repo, dia.atStartOfDay(), dia.plusDays(1).atStartOfDay()) }
    val r = remember(d) { Estatistica.resumo(d) }
    val titulo = dia.format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", ptBR)).replaceFirstChar { it.uppercase() }
    Cartao(titulo = titulo, subtitulo = r?.let { "${it.n} medições · média ${it.media.roundToInt()} mg/dL · ${it.tempoNoAlvo.roundToInt()}% no alvo" } ?: "Sem medições neste dia") {
        if (d.medicoes.isNotEmpty()) {
            GraficoLinhaTempo(d, dia.atStartOfDay(), dia.plusDays(1).atStartOfDay(), altura = 240.dp)
            Spacer(Modifier.height(8.dp))
        }
        val episodios = remember(d) { Episodios.de(d) }
        val emEpisodios = remember(episodios) { Episodios.dosesEmEpisodios(episodios) }
        val itens = (d.medicoes + d.refeicoes + d.doses.filter { it.id !in emEpisodios } + d.atividades).sortedBy { it.dataHora }
        if (itens.isEmpty()) Text("Nada registrado neste dia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        itens.forEach { reg ->
            val ep = (reg as? Refeicao)?.let { rf -> episodios.firstOrNull { it.refeicao.id == rf.id } }
            if (ep != null) LinhaEpisodio(ep, cfg, Modifier.clickable { aoEditar(reg) })
            else LinhaRegistro(reg, cfg, Modifier.clickable { aoEditar(reg) })
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Comparar: período atual × período imediatamente anterior, do mesmo tamanho
// ---------------------------------------------------------------------------------------------

private data class LinhaComparacao(
    val rotulo: String, val anterior: Double, val atual: Double, val formato: (Double) -> String,
    /** true: menor é melhor; false: maior é melhor; null: sem juízo de valor. */
    val menorMelhor: Boolean?, val limiar: Double,
)

@Composable
fun TelaComparar(vm: GlicemixViewModel) {
    var dias by rememberSaveable { mutableStateOf(14) }
    val hoje = LocalDate.now()
    val iniAtual = hoje.minusDays(dias - 1L)
    val iniAnterior = iniAtual.minusDays(dias.toLong())
    val fmt = DateTimeFormatter.ofPattern("dd/MM")
    val (atual, anterior) = remember(vm.dados, dias) {
        Estatistica.resumo(Dados.entre(vm.repo, iniAtual.atStartOfDay(), hoje.plusDays(1).atStartOfDay())) to
            Estatistica.resumo(Dados.entre(vm.repo, iniAnterior.atStartOfDay(), iniAtual.atStartOfDay()))
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EscolhaChips(listOf(7, 14, 30), dias, { "$it dias" }, { dias = it })
        if (atual == null || anterior == null) {
            Cartao(titulo = "Ainda não dá para comparar") {
                Text(
                    "Para comparar, é preciso ter medições nos dois períodos: ${iniAtual.format(fmt)} a ${hoje.format(fmt)} e ${iniAnterior.format(fmt)} a ${iniAtual.minusDays(1).format(fmt)}.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }
        val linhas = listOf(
            LinhaComparacao("Tempo no alvo", anterior.tempoNoAlvo, atual.tempoNoAlvo, { "${it.roundToInt()}%" }, false, 2.0),
            LinhaComparacao("Tempo abaixo do alvo", anterior.tempoAbaixo, atual.tempoAbaixo, { "%.1f%%".format(it).replace('.', ',') }, true, 0.5),
            LinhaComparacao("Tempo acima do alvo", anterior.tempoAcima, atual.tempoAcima, { "${it.roundToInt()}%" }, true, 2.0),
            LinhaComparacao("Média", anterior.media, atual.media, { "${it.roundToInt()} mg/dL" }, null, 5.0),
            LinhaComparacao("HbA1c estimada (GMI)", anterior.gmi, atual.gmi, { "%.1f%%".format(it).replace('.', ',') }, true, 0.1),
            LinhaComparacao("Variabilidade (CV)", anterior.cv, atual.cv, { "${it.roundToInt()}%" }, true, 2.0),
            LinhaComparacao("Hipoglicemias", anterior.episodiosHipo.toDouble(), atual.episodiosHipo.toDouble(), { it.roundToInt().toString() }, true, 0.5),
            LinhaComparacao("Medições por dia", anterior.medicoesPorDia, atual.medicoesPorDia, { "%.1f".format(it).replace('.', ',') }, false, 0.3),
        )
        Cartao(titulo = "Últimos $dias dias × $dias dias antes") {
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1.3f))
                Text("${iniAnterior.format(fmt)}–${iniAtual.minusDays(1).format(fmt)}", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${iniAtual.format(fmt)}–${hoje.format(fmt)}", Modifier.weight(1f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            linhas.forEachIndexed { i, l ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val dif = l.atual - l.anterior
                val igual = abs(dif) < l.limiar
                val melhorou = l.menorMelhor?.let { if (it) dif < 0 else dif > 0 }
                val cor = when {
                    igual || melhorou == null -> MaterialTheme.colorScheme.onSurfaceVariant
                    melhorou -> Cores.faixa(Faixa.NO_ALVO)
                    else -> Cores.faixa(Faixa.BAIXA)
                }
                val seta = if (igual) "＝" else if (dif > 0) "▲" else "▼"
                val veredito = when { igual -> "igual"; melhorou == null -> ""; melhorou -> "melhorou"; else -> "piorou" }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).semantics { contentDescription = "${l.rotulo}: antes ${l.formato(l.anterior)}, agora ${l.formato(l.atual)}, $veredito" }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1.3f)) {
                        Text(l.rotulo, style = MaterialTheme.typography.bodyMedium)
                        Text("$seta $veredito".trim(), style = MaterialTheme.typography.bodySmall, color = cor, fontWeight = FontWeight.SemiBold)
                    }
                    Text(l.formato(l.anterior), Modifier.weight(1f), textAlign = TextAlign.End, style = Numeros, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(l.formato(l.atual), Modifier.weight(1f), textAlign = TextAlign.End, style = Numeros, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text(
            "Períodos curtos oscilam bastante com poucas medições. Use a comparação como ponto de partida para a conversa com a equipe de saúde, não como diagnóstico.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
