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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import glicemix.analise.Nivel
import glicemix.analise.Padrao
import glicemix.app.GlicemixViewModel

@Composable
fun TelaPadroes(vm: GlicemixViewModel) {
    val padroes = vm.padroes
    val contagem = padroes.groupingBy { it.nivel }.eachCount()
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Nivel.entries.forEach { n ->
                    val q = contagem[n] ?: 0
                    if (q > 0) ItemLegenda("$q ${nomeNivel(n, q)}", Cores.nivel(n))
                }
            }
        }
        items(padroes) { CartaoPadrao(it) }
        item {
            Text(
                "Os padrões são calculados apenas a partir do que você registrou e têm caráter informativo. Eles não substituem a avaliação médica: " +
                    "leve-os para a consulta e não altere doses de insulina ou medicamentos sem orientação da sua equipe de saúde.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun nomeNivel(n: Nivel, q: Int) = when (n) {
    Nivel.ALERTA -> if (q > 1) "alertas" else "alerta"
    Nivel.ATENCAO -> if (q > 1) "pontos de atenção" else "ponto de atenção"
    Nivel.INFO -> if (q > 1) "observações" else "observação"
    Nivel.POSITIVO -> if (q > 1) "pontos positivos" else "ponto positivo"
}

@Composable
private fun CartaoPadrao(p: Padrao) {
    val cor = Cores.nivel(p.nivel)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.padding(vertical = 14.dp).width(4.dp).fillMaxHeight().background(cor, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)))
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Etiqueta(p.nivel.rotulo, cor)
                    Spacer(Modifier.width(8.dp))
                    Text(p.categoria.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(8.dp))
                Text(p.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(p.descricao, style = MaterialTheme.typography.bodyMedium)
                if (p.evidencia.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(p.evidencia, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
