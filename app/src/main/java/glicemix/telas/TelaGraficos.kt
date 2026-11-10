@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package glicemix.telas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import glicemix.analise.AnalisadorPadroes
import glicemix.analise.Estatistica
import glicemix.analise.Turno
import glicemix.app.GlicemixViewModel
import glicemix.graficos.GraficoBarras
import glicemix.graficos.GraficoDispersao
import glicemix.graficos.GraficoHistograma
import glicemix.graficos.GraficoLinhaTempo
import glicemix.graficos.GraficoPerfilDiario
import glicemix.graficos.GraficoTempoNoAlvoDiario
import glicemix.graficos.ItemBarra
import glicemix.graficos.PontoDispersao
import glicemix.model.Faixa
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class Pagina(val rotulo: String) {
    LINHA("Linha do tempo"), PERFIL("Perfil diário"), FAIXAS("Tempo no alvo"), DISTRIBUICAO("Distribuição"),
    REFEICOES("Refeições"), MOMENTOS("Momentos"), ATIVIDADE("Atividade"),
}

@Composable
fun TelaGraficos(vm: GlicemixViewModel) {
    val d = vm.dados
    val cfg = d.config
    var pagina by rememberSaveable { mutableStateOf(Pagina.LINHA) }
    val an = remember(d) { AnalisadorPadroes(d) }

    Column(Modifier.fillMaxSize()) {
        EscolhaChips(Pagina.entries, pagina, { it.rotulo }, { pagina = it }, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (pagina) {
                Pagina.LINHA -> Cartao(titulo = "Glicemia ao longo do tempo", subtitulo = "Faixa verde = alvo. Toque num ponto ou evento.") {
                    val ini = d.medicoes.firstOrNull()?.dataHora?.toLocalDate()?.atStartOfDay() ?: LocalDate.now().minusDays(6).atStartOfDay()
                    GraficoLinhaTempo(d, ini, LocalDateTime.now().plusMinutes(30), altura = 300.dp)
                    Legenda("No alvo" to Cores.faixa(Faixa.NO_ALVO), "Alta" to Cores.faixa(Faixa.ALTA), "Baixa" to Cores.faixa(Faixa.BAIXA),
                        "Refeição" to Cores.refeicao, "Insulina" to Cores.dose, "Basal" to Cores.basal, "Atividade" to Cores.atividade)
                }

                Pagina.PERFIL -> Cartao(titulo = "Perfil glicêmico do dia (AGP)", subtitulo = "Todos os dias sobrepostos: a linha é a mediana; as faixas mostram onde ficam 50% e 80% das leituras.") {
                    GraficoPerfilDiario(remember(d) { Estatistica.perfilDiario(d.medicoes) }, cfg, altura = 280.dp)
                    Legenda("Mediana" to Cores.primaria, "50% das leituras" to Cores.primaria.copy(alpha = 0.4f), "80%" to Cores.primaria.copy(alpha = 0.18f))
                }

                Pagina.FAIXAS -> Cartao(titulo = "Tempo nas faixas por dia", subtitulo = "Cada barra soma 100% das leituras do dia. Linha tracejada: meta de 70%.") {
                    GraficoTempoNoAlvoDiario(remember(d) { Estatistica.porDia(d.medicoes, cfg) }, altura = 260.dp)
                    Legenda(*Faixa.entries.reversed().map { it.rotulo to Cores.faixa(it) }.toTypedArray())
                }

                Pagina.DISTRIBUICAO -> Cartao(titulo = "Distribuição das glicemias", subtitulo = "Leituras em intervalos de 10 mg/dL; tracejado = limites do alvo.") {
                    GraficoHistograma(d.medicoes.map { it.valor }, cfg, altura = 260.dp)
                }

                Pagina.REFEICOES -> {
                    val resp = remember(an) { an.respostasRefeicoes() }
                    Cartao(titulo = "Variação 2h após cada refeição", subtitulo = "Diferença média entre a glicemia antes e depois") {
                        GraficoBarras(resp.groupBy { it.refeicao.tipo }.toSortedMap().map { (t, l) ->
                            val v = l.map { it.variacao.toDouble() }.average()
                            ItemBarra(t.rotulo, v,
                                if (v > 50) Cores.faixa(Faixa.ALTA) else if (v < -30) Cores.faixa(Faixa.BAIXA) else Cores.faixa(Faixa.NO_ALVO),
                                "${l.size} refeições · ${l.map { it.refeicao.carboidratos }.average().roundToInt()} g de carboidrato em média")
                        }, comSinal = true)
                    }
                    val fmt = DateTimeFormatter.ofPattern("dd/MM HH:mm")
                    Cartao(titulo = "Carboidratos × elevação", subtitulo = "Cada ponto é uma refeição com medição antes e depois") {
                        GraficoDispersao(resp.filter { it.refeicao.carboidratos > 0 }.map {
                            PontoDispersao(it.refeicao.carboidratos, it.variacao.toDouble(),
                                "${it.refeicao.tipo.rotulo} ${it.refeicao.dataHora.format(fmt)} · ${it.refeicao.descricao} · " +
                                    "${it.refeicao.carboidratos.roundToInt()} g · ${it.antes.valor} → ${it.depois.valor} mg/dL" +
                                    if (it.bolus > 0) " · ${num(it.bolus)} UI" else "")
                        }, rotuloX = "Carboidratos da refeição (g)", altura = 240.dp)
                    }
                    Text(
                        if (resp.isEmpty()) "Para estes gráficos, meça a glicemia até 1h antes da refeição e outra entre 1h30 e 3h depois. O Glicemix pareia as medições automaticamente."
                        else "${resp.size} refeições pareadas com medição antes (até 1h) e depois (1h30 a 3h).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Pagina.MOMENTOS -> {
                    Cartao(titulo = "Média por momento da medição") {
                        GraficoBarras(d.medicoes.groupBy { it.momento }.toSortedMap().map { (m, l) ->
                            val v = l.map { it.valor.toDouble() }.average()
                            ItemBarra(m.rotulo, v, Cores.faixa(cfg.classificar(v.roundToInt())), "${l.size} medições")
                        })
                    }
                    Cartao(titulo = "Média por turno do dia") {
                        GraficoBarras(d.medicoes.groupBy { Turno.de(it.dataHora) }.toSortedMap().map { (t, l) ->
                            val v = l.map { it.valor.toDouble() }.average()
                            ItemBarra(t.rotulo.substringBefore(" ("), v, Cores.faixa(cfg.classificar(v.roundToInt())), "${t.rotulo} · ${l.size} medições")
                        })
                    }
                }

                Pagina.ATIVIDADE -> {
                    val resp = remember(an) { an.respostasAtividades() }
                    Cartao(titulo = "Efeito da atividade física", subtitulo = "Variação entre a medição antes e até 2h depois do exercício") {
                        GraficoBarras(resp.groupBy { it.atividade.intensidade }.toSortedMap().map { (i, l) ->
                            ItemBarra(i.rotulo, l.map { it.variacao.toDouble() }.average(), Cores.atividade, "${l.size} sessões")
                        }, comSinal = true)
                    }
                    Cartao(titulo = "Por tipo de atividade") {
                        GraficoBarras(resp.groupBy { it.atividade.tipo }.map { (t, l) ->
                            ItemBarra(t, l.map { it.variacao.toDouble() }.average(), Cores.atividade.copy(alpha = 0.75f), "${l.size} sessões")
                        }.sortedBy { it.valor }, comSinal = true)
                    }
                }
            }
        }
    }
}
