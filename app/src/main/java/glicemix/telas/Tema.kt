package glicemix.telas

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import glicemix.analise.Nivel
import glicemix.model.Atividade
import glicemix.model.Dose
import glicemix.model.Faixa
import glicemix.model.Medicao
import glicemix.model.Refeicao
import glicemix.model.Registro

/** Mesma paleta do app desktop, para os dois apps “falarem a mesma língua”. */
object Cores {
    val primaria = Color(0xFF0E7C86)
    val refeicao = Color(0xFF7A5AF8)
    val dose = Color(0xFF2E90FA)
    val basal = Color(0xFF1849A9)
    val atividade = Color(0xFFDD2590)
    val alvo = Color(0xFF1E9E61)

    fun faixa(f: Faixa): Color = when (f) {
        Faixa.MUITO_BAIXA -> Color(0xFFA11B12)
        Faixa.BAIXA -> Color(0xFFE5483B)
        Faixa.NO_ALVO -> Color(0xFF1E9E61)
        Faixa.ALTA -> Color(0xFFF2A22C)
        Faixa.MUITO_ALTA -> Color(0xFFB8580E)
    }

    fun nivel(n: Nivel): Color = when (n) {
        Nivel.ALERTA -> Color(0xFFD92D20)
        Nivel.ATENCAO -> Color(0xFFF79009)
        Nivel.INFO -> Color(0xFF2E90FA)
        Nivel.POSITIVO -> Color(0xFF12B76A)
    }

    fun tipo(r: Registro): Color = when (r) {
        is Medicao -> primaria
        is Refeicao -> refeicao
        is Dose -> if (r.tipo.ehBolus) dose else basal
        is Atividade -> atividade
    }
}

private val Claro = lightColorScheme(
    primary = Color(0xFF0E7C86),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEDEC),
    onPrimaryContainer = Color(0xFF00363A),
    secondaryContainer = Color(0xFFDCE8E9),
    onSecondaryContainer = Color(0xFF1D3436),
    background = Color(0xFFF3F5F8),
    onBackground = Color(0xFF1D2733),
    surface = Color(0xFFF3F5F8),
    onSurface = Color(0xFF1D2733),
    surfaceVariant = Color(0xFFE8ECF1),
    onSurfaceVariant = Color(0xFF667085),
    outline = Color(0xFFC4CBD4),
    outlineVariant = Color(0xFFE3E8EF),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F9FB),
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF0F2F5),
    surfaceContainerHighest = Color(0xFFE8ECF1),
)

private val Escuro = darkColorScheme(
    primary = Color(0xFF5BD5CD),
    onPrimary = Color(0xFF00373A),
    primaryContainer = Color(0xFF0A5C63),
    onPrimaryContainer = Color(0xFFCDEDEC),
    secondaryContainer = Color(0xFF2A3D40),
    onSecondaryContainer = Color(0xFFDCE8E9),
    background = Color(0xFF0F1417),
    onBackground = Color(0xFFE4E8EC),
    surface = Color(0xFF0F1417),
    onSurface = Color(0xFFE4E8EC),
    surfaceVariant = Color(0xFF242D33),
    onSurfaceVariant = Color(0xFF9AA4B2),
    outline = Color(0xFF46515A),
    outlineVariant = Color(0xFF283137),
    surfaceContainerLowest = Color(0xFF171E22),
    surfaceContainerLow = Color(0xFF151B1F),
    surfaceContainer = Color(0xFF171E22),
    surfaceContainerHigh = Color(0xFF1E262B),
    surfaceContainerHighest = Color(0xFF263036),
)

@Composable
fun GlicemixTema(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Escuro else Claro, content = content)
}
