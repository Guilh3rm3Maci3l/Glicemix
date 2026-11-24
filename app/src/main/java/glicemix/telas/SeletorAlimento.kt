@file:OptIn(ExperimentalMaterial3Api::class)

package glicemix.telas

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import glicemix.app.GlicemixViewModel
import glicemix.data.BaseAlimentos
import glicemix.model.Alimento
import glicemix.model.ItemRefeicao
import glicemix.model.Origem
import glicemix.model.Faixa
import java.util.UUID

private fun decimalOuNulo(s: String): Double? = s.trim().replace(',', '.').toDoubleOrNull()

private fun resumoCarbos(a: Alimento): String =
    if (a.carboidratos100 == null) "Sem dado de carboidratos"
    else "${num(a.carboidratos100)} g de carboidratos por 100 g · ${a.origem.rotulo}"

/**
 * Escolher um alimento (TACO, rótulo ou próprio), informar quantos gramas e ver os carboidratos na hora.
 * Três etapas: busca → quantidade → (se não achar) cadastro pelo rótulo.
 */
@Composable
fun SeletorAlimento(vm: GlicemixViewModel, aoAdicionar: (ItemRefeicao) -> Unit, aoFechar: () -> Unit) {
    var consulta by remember { mutableStateOf("") }
    var escolhido by remember { mutableStateOf<Alimento?>(null) }
    var criando by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = aoFechar, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (criando) "Novo alimento" else if (escolhido != null) "Quantidade" else "Adicionar alimento") },
                    navigationIcon = {
                        IconButton(onClick = { if (criando) criando = false else if (escolhido != null) escolhido = null else aoFechar() }) {
                            if (criando || escolhido != null) Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                            else Icon(Icons.Filled.Close, contentDescription = "Fechar")
                        }
                    },
                )
            },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize().imePadding()) {
                val a = escolhido
                when {
                    criando -> FormNovoAlimento(
                        nomeInicial = consulta,
                        aoSalvar = { novo -> vm.salvarAlimentoProprio(novo); criando = false; escolhido = novo },
                    )
                    a != null -> EtapaQuantidade(a, aoConfirmar = { item -> aoAdicionar(item); aoFechar() })
                    else -> EtapaBusca(vm, consulta, { consulta = it }, aoEscolher = { escolhido = it }, aoCriar = { criando = true })
                }
            }
        }
    }
}

@Composable
private fun EtapaBusca(vm: GlicemixViewModel, consulta: String, aoMudar: (String) -> Unit, aoEscolher: (Alimento) -> Unit, aoCriar: () -> Unit) {
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }
    val resultados = remember(consulta, vm.proprios) { if (consulta.isBlank()) emptyList() else vm.buscarAlimentos(consulta) }
    val recentes = remember(consulta, vm.proprios) { if (consulta.isBlank()) vm.alimentosRecentes() else emptyList() }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            consulta, aoMudar, singleLine = true,
            placeholder = { Text("Buscar: arroz, feijão, pão, banana…") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(foco),
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)) {
            if (consulta.isBlank()) {
                if (recentes.isNotEmpty()) {
                    item { Cabecalho("Usados recentemente") }
                    items(recentes, key = { "r-" + it.id }) { LinhaAlimento(it) { aoEscolher(it) } }
                }
                if (vm.proprios.isNotEmpty()) {
                    item { Cabecalho("Meus alimentos") }
                    items(vm.proprios, key = { "p-" + it.id }) { LinhaAlimento(it) { aoEscolher(it) } }
                }
                item {
                    Text(
                        "Digite o nome do alimento. Os valores vêm da TACO (tabela brasileira, 597 alimentos); o que não estiver lá você cadastra pelo rótulo.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                if (resultados.isEmpty()) {
                    item { Text("Nenhum alimento encontrado para “$consulta”.", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                items(resultados, key = { it.id }) { LinhaAlimento(it) { aoEscolher(it) } }
            }
            item {
                OutlinedButton(onClick = aoCriar, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Não achei: cadastrar pelo rótulo")
                }
                Text(
                    "Fonte: ${BaseAlimentos.FONTE}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun Cabecalho(texto: String) {
    Text(texto, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
}

@Composable
private fun LinhaAlimento(a: Alimento, aoToque: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = aoToque).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(a.nome, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Text(resumoCarbos(a), style = MaterialTheme.typography.bodySmall, color = if (a.carboidratos100 == null) Cores.faixa(Faixa.BAIXA) else MaterialTheme.colorScheme.onSurfaceVariant)
        if (a.aviso.isNotBlank() && a.carboidratos100 != 0.0) {
            Text(a.aviso, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = Cores.faixa(Faixa.MUITO_ALTA))
        }
    }
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun EtapaQuantidade(a: Alimento, aoConfirmar: (ItemRefeicao) -> Unit) {
    var texto by remember { mutableStateOf("") }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }
    val gramas = decimalOuNulo(texto)?.takeIf { it > 0 }
    val carbos = gramas?.let { a.carboidratosPara(it) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column {
            Text(a.nome, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(resumoCarbos(a), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (a.carboidratos100 == null) {
            AvisoCaixa("A TACO não informa os carboidratos deste alimento. Volte e cadastre-o pelo rótulo da embalagem: o app não vai assumir zero.", Cores.faixa(Faixa.BAIXA))
        } else {
            if (a.aviso.isNotBlank() && a.carboidratos100 != 0.0) AvisoCaixa(a.aviso, Cores.faixa(Faixa.MUITO_ALTA))
            OutlinedTextField(
                texto, { n -> if (n.all { it.isDigit() || it == ',' || it == '.' } && n.length <= 6) texto = n },
                label = { Text("Quantos gramas você vai comer?") }, suffix = { Text("g") },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                textStyle = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
                modifier = Modifier.fillMaxWidth().focusRequester(foco),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val porcao = a.porcaoGramas
                if (porcao != null) SuggestionChip(onClick = { texto = num(porcao).replace(",", ".") }, label = { Text("${a.porcaoNome.ifBlank { "1 porção" }} · ${num(porcao)} g") })
                listOf(50, 100, 150, 200).forEach { g -> SuggestionChip(onClick = { texto = g.toString() }, label = { Text("$g g") }) }
            }
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Carboidratos", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(
                        if (carbos == null) "—" else "${num(Math.round(carbos * 10) / 10.0)} g",
                        style = Numeros, fontSize = 40.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    if (gramas != null) Text("${num(gramas)} g × ${num(a.carboidratos100)} g por 100 g", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
            Text(
                "Pese o alimento como você vai comer (já cozido, sem casca, sem caroço). Os valores da TACO são para a parte comestível.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = { aoConfirmar(ItemRefeicao(a.nome, gramas!!, a.carboidratos100, a.origem)) },
                enabled = gramas != null, modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Adicionar à refeição", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun AvisoCaixa(texto: String, cor: androidx.compose.ui.graphics.Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = cor.copy(alpha = 0.12f), modifier = Modifier.fillMaxWidth()) {
        Text("⚠ $texto", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = cor, modifier = Modifier.padding(12.dp))
    }
}

/** Cadastro pelo rótulo (por 100 g ou por porção) ou de uma receita própria. */
@Composable
private fun FormNovoAlimento(nomeInicial: String, aoSalvar: (Alimento) -> Unit) {
    var nome by remember { mutableStateOf(nomeInicial.trim().replaceFirstChar { it.uppercase() }) }
    var porPorcao by remember { mutableStateOf(true) }
    var origem by remember { mutableStateOf(Origem.ROTULO) }
    var carbos by remember { mutableStateOf("") }
    var peso by remember { mutableStateOf("") }
    var nomePorcao by remember { mutableStateOf("") }
    var erro by remember { mutableStateOf<String?>(null) }

    fun salvar() {
        val c = decimalOuNulo(carbos); val p = decimalOuNulo(peso)
        if (nome.isBlank()) { erro = "Dê um nome ao alimento."; return }
        if (c == null || c < 0) { erro = "Informe os carboidratos."; return }
        if (porPorcao && (p == null || p <= 0)) { erro = "Informe quantos gramas tem a porção."; return }
        val por100 = if (porPorcao) c * 100.0 / p!! else c
        if (por100 > 100.0) { erro = "Isso dá mais de 100 g de carboidratos em 100 g de alimento. Confira os números do rótulo."; return }
        aoSalvar(
            Alimento(
                id = "proprio-" + UUID.randomUUID(), nome = nome.trim(), grupo = "Meus alimentos",
                carboidratos100 = Math.round(por100 * 10) / 10.0, origem = origem,
                porcaoNome = if (porPorcao) nomePorcao.trim().ifBlank { "1 porção" } else "",
                porcaoGramas = if (porPorcao) p else null,
            )
        )
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(nome, { nome = it }, label = { Text("Nome (ex.: Biscoito X)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("De onde vem o número?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EscolhaChips(listOf(Origem.ROTULO, Origem.PROPRIO), origem, { if (it == Origem.ROTULO) "Rótulo da embalagem" else "Receita ou prato meu" }, { origem = it })
        Text("O rótulo informa", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        EscolhaChips(listOf(true, false), porPorcao, { if (it) "Por porção" else "Por 100 g" }, { porPorcao = it })
        if (porPorcao) {
            OutlinedTextField(nomePorcao, { nomePorcao = it }, label = { Text("Nome da porção (ex.: 1 pacote, 3 biscoitos)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            CampoDec(peso, "Peso da porção", "g") { peso = it }
            CampoDec(carbos, "Carboidratos na porção", "g") { carbos = it }
        } else {
            CampoDec(carbos, "Carboidratos em 100 g", "g") { carbos = it }
        }
        erro?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Text(
            "Use o campo “Carboidratos” (total) do rótulo, não “açúcares”. Confira se a porção do rótulo é a de alimento pronto para comer.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Button(onClick = { salvar() }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Salvar alimento", fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun CampoDec(valor: String, rotulo: String, sufixo: String, aoMudar: (String) -> Unit) {
    OutlinedTextField(
        valor, { n -> if (n.all { it.isDigit() || it == ',' || it == '.' } && n.length <= 7) aoMudar(n) },
        label = { Text(rotulo) }, suffix = { Text(sufixo) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth(),
    )
}
