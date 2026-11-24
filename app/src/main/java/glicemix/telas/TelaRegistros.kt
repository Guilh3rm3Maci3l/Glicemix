@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package glicemix.telas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Arrangement
import glicemix.analise.Episodios
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import glicemix.app.GlicemixViewModel
import glicemix.model.Atividade
import glicemix.model.Dose
import glicemix.model.Medicao
import glicemix.model.Refeicao
import glicemix.model.Registro
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class Filtro(val rotulo: String) { TODOS("Todos"), MEDICOES("Medições"), REFEICOES("Refeições"), DOSES("Doses"), ATIVIDADES("Atividades"), MARCADOS("★ Consulta") }

private val fCabecalho = DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM", ptBR)

@Composable
fun TelaRegistros(vm: GlicemixViewModel, aoEditar: (Registro) -> Unit) {
    val d = vm.dados
    var filtro by rememberSaveable { mutableStateOf(Filtro.TODOS) }
    var busca by rememberSaveable { mutableStateOf("") }
    var etiqueta by rememberSaveable { mutableStateOf<String?>(null) }

    val episodios = remember(d) { Episodios.de(d) }
    val emEpisodios = remember(episodios) { Episodios.dosesEmEpisodios(episodios) }
    val porRefeicao = remember(episodios) { episodios.associateBy { it.refeicao.id } }
    val etiquetasUsadas = remember(d) { (d.medicoes + d.refeicoes + d.doses + d.atividades).flatMap { it.tags }.distinct().sorted() }

    val lista = remember(d, filtro, busca, etiqueta, emEpisodios) {
        val q = busca.trim().lowercase()
        // a dose ligada a uma refeição aparece dentro dela, não repetida na lista
        (d.medicoes + d.refeicoes + d.doses.filter { it.id !in emEpisodios } + d.atividades)
            .filter { r ->
                when (filtro) {
                    Filtro.TODOS -> true
                    Filtro.MEDICOES -> r is Medicao
                    Filtro.REFEICOES -> r is Refeicao
                    Filtro.DOSES -> r is Dose
                    Filtro.ATIVIDADES -> r is Atividade
                    Filtro.MARCADOS -> r.marcado
                }
            }
            .filter { etiqueta == null || etiqueta in it.tags }
            .filter { q.isEmpty() || textoBusca(it).contains(q) }
            .sortedByDescending { it.dataHora }
    }
    val porDia = remember(lista) { lista.groupBy { it.dataHora.toLocalDate() } }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            OutlinedTextField(
                busca, { busca = it },
                placeholder = { Text("Buscar alimento, medicamento, observação…") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            EscolhaChips(Filtro.entries, filtro, { it.rotulo }, { filtro = it }, Modifier.padding(top = 8.dp))
            if (etiquetasUsadas.isNotEmpty()) {
                EscolhaChips(listOf<String?>(null) + etiquetasUsadas, etiqueta, { it?.let { t -> "#$t" } ?: "Todas as circunstâncias" }, { etiqueta = it }, Modifier.padding(top = 4.dp))
            }
            Text("${lista.size} registro(s) · ${vm.periodo.rotulo.lowercase()}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
        }
        if (lista.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Nenhum registro encontrado", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (busca.isNotBlank() || filtro != Filtro.TODOS || etiqueta != null) "Tire algum filtro ou mude o período no topo da tela."
                    else "Use o botão Registrar para adicionar a primeira glicemia, refeição, dose ou atividade.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                porDia.forEach { (dia, itens) ->
                    stickyHeader(key = "dia-$dia") { CabecalhoDia(dia, itens.size) }
                    items(itens, key = { it.id }) { r ->
                        Column(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
                            val ep = (r as? Refeicao)?.let { porRefeicao[it.id] }
                            if (ep != null) LinhaEpisodio(ep, d.config, Modifier.clickable { aoEditar(r) }.padding(horizontal = 16.dp))
                            else LinhaRegistro(r, d.config, Modifier.clickable { aoEditar(r) }.padding(horizontal = 16.dp))
                            HorizontalDivider(Modifier.padding(start = 66.dp), color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CabecalhoDia(dia: LocalDate, n: Int) {
    val hoje = LocalDate.now()
    val nome = when (dia) {
        hoje -> "Hoje"
        hoje.minusDays(1) -> "Ontem"
        else -> dia.format(fCabecalho).replaceFirstChar { it.uppercase() }
    }
    Text(
        "$nome · $n",
        style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun textoBusca(r: Registro): String = when (r) {
    is Medicao -> "${r.valor} ${r.momento.rotulo} ${r.observacao}"
    is Refeicao -> "${r.tipo.rotulo} ${r.descricao} ${r.observacao}"
    is Dose -> "${r.medicamento} ${r.tipo.rotulo} ${r.observacao}"
    is Atividade -> "${r.tipo} ${r.intensidade.rotulo} ${r.observacao}"
}.plus(" " + r.tags.joinToString(" ")).lowercase()
