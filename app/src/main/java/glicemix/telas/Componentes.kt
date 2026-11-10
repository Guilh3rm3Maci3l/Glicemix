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
    is Medicao -> Descricao("Glicemia", r.momento.rotulo, "${r.valor} mg/dL", Cores.faixa(cfg.classificar(r.valor)))
    is Refeicao -> Descricao(r.tipo.rotulo, r.descricao.ifBlank { "Sem descrição" }, "${num(r.carboidratos)} g", null)
    is Dose -> Descricao(r.medicamento, r.tipo.rotulo, "${num(r.quantidade)} ${r.unidade}", null)
    is Atividade -> Descricao(r.tipo, "${r.intensidade.rotulo} · ${r.duracaoMin} min", "${r.duracaoMin} min", null)
}

fun iconeDe(r: Registro): ImageVector = when (r) {
    is Medicao -> Icons.Outlined.WaterDrop
    is Refeicao -> Icons.Outlined.Restaurant
    is Dose -> Icons.Outlined.Medication
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
                Text(titulo.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(valor, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = cor ?: MaterialTheme.colorScheme.onSurface)
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
        color = cor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
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
            Text(d.titulo, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (if (mostrarHora) r.dataHora.format(fHora) + " · " else "") + d.subtitulo +
                    if (r.observacao.isNotBlank()) " · ${r.observacao}" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(d.valor, fontWeight = FontWeight.Bold, color = d.corValor ?: MaterialTheme.colorScheme.onSurface)
    }
}
