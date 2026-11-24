@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package glicemix.telas

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Medication
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxHeight
import glicemix.analise.Episodio
import glicemix.model.Faixa
import glicemix.model.Etiquetas
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import glicemix.data.Repositorio
import glicemix.model.Atividade
import glicemix.model.Configuracao
import glicemix.model.Dose
import glicemix.model.Medicao
import glicemix.model.Refeicao
import glicemix.model.Registro
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

val ptBR: Locale = Locale.forLanguageTag("pt-BR")
val fHora: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
val fData: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
val fDiaHora: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE dd/MM HH:mm", ptBR)

fun num(d: Double): String = Repositorio.num(d)

fun haQuanto(t: LocalDateTime): String {
    val d = Duration.between(t, LocalDateTime.now())
    return when {
        d.toMinutes() < 1 -> "agora"
        d.toMinutes() < 60 -> "há ${d.toMinutes()} min"
        d.toHours() < 24 -> "há ${d.toHours()} h"
        else -> t.format(DateTimeFormatter.ofPattern("dd/MM HH:mm"))
    }
}

/** Título, subtítulo e valor de um registro para listas. */
data class Descricao(val titulo: String, val subtitulo: String, val valor: String, val corValor: Color?)

fun descrever(r: Registro, cfg: Configuracao): Descricao = when (r) {
    is Medicao -> {
        val f = cfg.classificar(r.valor)
        Descricao("Glicemia", r.momento.rotulo, (if (f == Faixa.NO_ALVO) "" else simboloFaixa(f) + " ") + "${r.valor} mg/dL", Cores.faixa(f))
    }
    is Refeicao -> Descricao(r.tipo.rotulo, r.descricao.ifBlank { "Sem descrição" }, "${num(r.carboidratos)} g", null)
    is Dose -> Descricao(r.medicamento, r.tipo.rotulo, "${num(r.quantidade)} ${r.unidade}", null)
    is Atividade -> Descricao(r.tipo, "${r.intensidade.rotulo} · ${r.duracaoMin} min", "${r.duracaoMin} min", null)
}

/** Números alinhados em coluna: cada dígito ocupa a mesma largura. */
val Numeros = TextStyle(fontFeatureSettings = "tnum")

/** Símbolo da faixa: a cor nunca é a única pista (daltonismo, sol forte na tela). */
fun simboloFaixa(f: Faixa): String = when (f) {
    Faixa.MUITO_BAIXA -> "▼▼"
    Faixa.BAIXA -> "▼"
    Faixa.NO_ALVO -> "●"
    Faixa.ALTA -> "▲"
    Faixa.MUITO_ALTA -> "▲▲"
}

/** Seta e texto da tendência entre as duas últimas medições (só se forem de até 3 h de distância). */
fun tendencia(medicoes: List<Medicao>): Pair<String, String>? {
    if (medicoes.size < 2) return null
    val a = medicoes[medicoes.size - 2]; val b = medicoes.last()
    val min = Duration.between(a.dataHora, b.dataHora).toMinutes()
    if (min !in 1..180) return null
    val dif = b.valor - a.valor
    val seta = when { dif >= 30 -> "↑"; dif >= 15 -> "↗"; dif <= -30 -> "↓"; dif <= -15 -> "↘"; else -> "→" }
    return seta to (if (dif >= 0) "+$dif" else "−${-dif}") + " mg/dL desde as ${a.dataHora.format(fHora)}"
}

fun iconeDe(r: Registro): ImageVector = when (r) {
    is Medicao -> Icons.Outlined.WaterDrop
    is Refeicao -> Icons.Outlined.Restaurant
    is Dose -> if (r.tipo.ehBolus || r.tipo == glicemix.model.TipoDose.ORAL || r.tipo == glicemix.model.TipoDose.OUTRO) Icons.Outlined.Medication else Icons.Outlined.Timelapse
    is Atividade -> Icons.Outlined.FitnessCenter
}

/** Cartão branco com borda suave, usado em todas as telas. */
@Composable
fun Cartao(
    modifier: Modifier = Modifier,
    titulo: String? = null,
    subtitulo: String? = null,
    conteudo: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (titulo != null) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (subtitulo != null) {
                    Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(12.dp))
            }
            conteudo()
        }
    }
}

/** Indicador numérico com faixa colorida à esquerda. */
@Composable
fun Indicador(titulo: String, valor: String, unidade: String, legenda: String, cor: Color?, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row {
            Box(
                Modifier.padding(top = 14.dp).width(4.dp).height(52.dp)
                    .background(cor ?: Color.Transparent, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
            )
            Column(Modifier.padding(start = 10.dp, end = 12.dp, top = 12.dp, bottom = 12.dp)) {
                Text(titulo.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(valor, style = Numeros, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = cor ?: MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(4.dp))
                    Text(unidade, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                Text(legenda, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Fileira rolável de chips de escolha única. */
@Composable
fun <T> EscolhaChips(opcoes: List<T>, selecionado: T?, rotulo: (T) -> String, onEscolher: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opcoes.forEach { o ->
            FilterChip(selected = o == selecionado, onClick = { onEscolher(o) }, label = { Text(rotulo(o)) })
        }
    }
}

@Composable
fun IconeRegistro(r: Registro, tamanho: Dp = 38.dp) {
    val cor = Cores.tipo(r)
    Box(
        Modifier.size(tamanho).clip(CircleShape).background(cor.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(iconeDe(r), contentDescription = null, tint = cor, modifier = Modifier.size(tamanho * 0.55f))
    }
}

@Composable
fun ItemLegenda(texto: String, cor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(cor))
        Spacer(Modifier.width(5.dp))
        Text(texto, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun Legenda(vararg itens: Pair<String, Color>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        itens.forEach { (t, c) -> ItemLegenda(t, c) }
    }
}

@Composable
fun Etiqueta(texto: String, cor: Color) {
    Text(
        texto,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(cor.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 3.dp),
        color = cor, fontSize = 12.sp, fontWeight = FontWeight.Bold,
    )
}

/** Linha de registro usada no painel (Hoje) e na lista de registros. */
@Composable
fun LinhaRegistro(r: Registro, cfg: Configuracao, modifier: Modifier = Modifier, mostrarHora: Boolean = true) {
    val d = descrever(r, cfg)
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconeRegistro(r)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (r.marcado) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Star, contentDescription = "Marcado para a consulta", tint = Cores.faixa(Faixa.ALTA), modifier = Modifier.size(16.dp))
                }
            }
            Text(
                (if (mostrarHora) r.dataHora.format(fHora) + " · " else "") + d.subtitulo +
                    (if (r.tags.isNotEmpty()) " · " + r.tags.joinToString(" ") { "#$it" } else "") +
                    if (r.observacao.isNotBlank()) " · ${r.observacao}" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(d.valor, style = Numeros, fontWeight = FontWeight.Bold, color = d.corValor ?: MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Refeição com o que veio junto: insulina e o que a glicemia fez depois.
 * Ex.: "Almoço · 60 g · 6 UI Fiasp" e, embaixo, "112 → 198 mg/dL (+86) em 2 h".
 */
@Composable
fun LinhaEpisodio(ep: Episodio, cfg: Configuracao, modifier: Modifier = Modifier, mostrarHora: Boolean = true) {
    val r = ep.refeicao
    val insulina = ep.doses.joinToString(" + ") { "${num(it.quantidade)} ${it.unidade} ${it.medicamento}" }
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconeRegistro(r)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.tipo.rotulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                if (r.marcado) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Star, contentDescription = "Marcado para a consulta", tint = Cores.faixa(Faixa.ALTA), modifier = Modifier.size(16.dp))
                }
            }
            Text(
                listOfNotNull(
                    if (mostrarHora) r.dataHora.format(fHora) else null,
                    r.descricao.takeIf { it.isNotBlank() },
                    if (r.tags.isNotEmpty()) r.tags.joinToString(" ") { "#$it" } else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val v = ep.variacao
            if (ep.antes != null && ep.depois != null && v != null) {
                val cor = Cores.faixa(cfg.classificar(ep.depois.valor))
                Text(
                    "${ep.antes.valor} → ${ep.depois.valor} mg/dL (${if (v >= 0) "+$v" else "−${-v}"}) em 2 h",
                    style = MaterialTheme.typography.bodySmall.merge(Numeros), fontWeight = FontWeight.SemiBold, color = cor,
                )
            } else {
                Text("Sem par de medições para ver o efeito", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text("${num(r.carboidratos)} g", style = Numeros, fontWeight = FontWeight.Bold)
            if (insulina.isNotEmpty()) Text(insulina, style = MaterialTheme.typography.bodySmall, color = Cores.dose, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Seleção múltipla de circunstâncias (doença, estresse, álcool…). */
@Composable
fun EscolhaEtiquetas(opcoes: List<String>, selecionadas: List<String>, onMudar: (List<String>) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        opcoes.forEach { o ->
            val ativo = o in selecionadas
            FilterChip(selected = ativo, onClick = { onMudar(if (ativo) selecionadas - o else selecionadas + o) }, label = { Text(o) })
        }
    }
}

/** Mini gráfico das últimas leituras, com a faixa-alvo ao fundo. */
@Composable
fun MiniLinha(valores: List<Int>, cfg: Configuracao, modifier: Modifier = Modifier) {
    if (valores.size < 2) return
    val corLinha = MaterialTheme.colorScheme.onSurfaceVariant
    val corAlvo = Cores.faixa(Faixa.NO_ALVO).copy(alpha = 0.16f)
    val resumo = "Últimas ${valores.size} medições, de ${valores.first()} a ${valores.last()} mg/dL"
    Canvas(modifier.semantics { contentDescription = resumo }) {
        val min = minOf(valores.min(), cfg.alvoMin) - 10f
        val max = maxOf(valores.max(), cfg.alvoMax) + 10f
        fun py(v: Float) = size.height - (v - min) / (max - min) * size.height
        drawRect(corAlvo, topLeft = Offset(0f, py(cfg.alvoMax.toFloat())), size = androidx.compose.ui.geometry.Size(size.width, py(cfg.alvoMin.toFloat()) - py(cfg.alvoMax.toFloat())))
        val passo = size.width / (valores.size - 1)
        val caminho = Path()
        valores.forEachIndexed { i, v -> if (i == 0) caminho.moveTo(0f, py(v.toFloat())) else caminho.lineTo(i * passo, py(v.toFloat())) }
        drawPath(caminho, corLinha, style = Stroke(width = 2.dp.toPx()))
        valores.forEachIndexed { i, v -> drawCircle(Cores.faixa(cfg.classificar(v)), radius = 3.5.dp.toPx(), center = Offset(i * passo, py(v.toFloat()))) }
    }
}
