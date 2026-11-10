package glicemix.graficos

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Cores do tema usadas pelos gráficos (lidas fora do Canvas). */
class CoresGrafico(val grade: Color, val texto: Color, val linha: Color, val fundo: Color, val sombra: Color)

@Composable
fun coresGrafico(): CoresGrafico = MaterialTheme.colorScheme.let {
    CoresGrafico(grade = it.outlineVariant, texto = it.onSurfaceVariant, linha = it.outline, fundo = it.surfaceContainerLowest, sombra = it.surfaceContainerHigh)
}

/** Escala linear de domínio para pixels, com inversa para tratar toques. */
class Escala(private val d0: Float, private val d1: Float, private val r0: Float, private val r1: Float) {
    operator fun invoke(v: Float): Float = r0 + (v - d0) / (d1 - d0) * (r1 - r0)
    fun inversa(p: Float): Float = d0 + (p - r0) / (r1 - r0) * (d1 - d0)
}

/** Valores “redondos” para as marcas de eixo. */
fun marcas(min: Float, max: Float, alvo: Int = 5): List<Float> {
    val bruto = (max - min) / alvo
    if (bruto <= 0f) return listOf(min)
    val mag = 10.0.pow(floor(log10(bruto.toDouble()))).toFloat()
    val passo = listOf(1f, 2f, 2.5f, 5f, 10f).map { it * mag }.first { it >= bruto }
    val ini = ceil(min / passo) * passo
    return generateSequence(ini) { it + passo }.takeWhile { it <= max + passo * 1e-3f }.toList()
}

enum class Alinha { ESQ, CENTRO, DIR }

fun DrawScope.texto(
    m: TextMeasurer, s: String, x: Float, y: Float, cor: Color,
    h: Alinha = Alinha.CENTRO, tamanho: TextUnit = 10.sp, peso: FontWeight = FontWeight.Normal,
) {
    val r = m.measure(s, TextStyle(color = cor, fontSize = tamanho, fontWeight = peso))
    val dx = when (h) {
        Alinha.ESQ -> 0f
        Alinha.CENTRO -> -r.size.width / 2f
        Alinha.DIR -> -r.size.width.toFloat()
    }
    drawText(r, topLeft = Offset(x + dx, y - r.size.height / 2f))
}

fun DrawScope.gradeY(m: TextMeasurer, area: Rect, y: Escala, valores: List<Float>, c: CoresGrafico, fmt: (Float) -> String = { it.toInt().toString() }) {
    valores.forEach { v ->
        val py = y(v)
        drawLine(c.grade, Offset(area.left, py), Offset(area.right, py), strokeWidth = 1f)
        texto(m, fmt(v), area.left - 5.dp.toPx(), py, c.texto, Alinha.DIR)
    }
}

fun DrawScope.tracejada(cor: Color, a: Offset, b: Offset) {
    drawLine(cor, a, b, strokeWidth = 1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(9f, 7f)))
}

/**
 * Moldura comum: desenha no Canvas e, se houver tratador de toque, mostra abaixo
 * o detalhe do elemento tocado.
 */
@Composable
fun MolduraGrafico(
    altura: Dp,
    chave: Any?,
    modifier: Modifier = Modifier,
    aoTocar: (Density.(Offset, Size) -> String?)? = null,
    desenhar: DrawScope.(TextMeasurer) -> Unit,
) {
    val medidor = rememberTextMeasurer()
    var detalhe by remember(chave) { mutableStateOf<String?>(null) }
    val toque by rememberUpdatedState(aoTocar)
    Column(modifier.fillMaxWidth()) {
        var mod = Modifier.fillMaxWidth().height(altura)
        if (aoTocar != null) {
            mod = mod.pointerInput(chave) {
                detectTapGestures(onTap = { p ->
                    val f = toque
                    if (f != null) detalhe = f(this, p, Size(size.width.toFloat(), size.height.toFloat()))
                })
            }
        }
        Canvas(mod) { desenhar(medidor) }
        if (aoTocar != null) {
            Text(
                detalhe ?: "Toque no gráfico para ver detalhes",
                style = MaterialTheme.typography.bodySmall,
                color = if (detalhe == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun SemDados(altura: Dp = 160.dp, texto: String = "Sem dados suficientes para este gráfico no período.") {
    Box(Modifier.fillMaxWidth().height(altura), contentAlignment = Alignment.Center) {
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
