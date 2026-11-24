package glicemix.telas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import glicemix.app.GlicemixViewModel
import glicemix.model.Medicao
import glicemix.model.MomentoMedicao
import glicemix.model.momentoSugerido
import java.time.LocalDateTime

/**
 * Conteúdo da folha "Novo registro": a glicemia (o registro mais frequente) é digitada aqui mesmo,
 * com o teclado numérico já aberto: Registrar → número → Salvar. Os outros tipos ficam logo abaixo.
 */
@Composable
fun RegistroRapido(
    vm: GlicemixViewModel,
    aoFechar: () -> Unit,
    aoEscolher: (TipoRegistro) -> Unit,
    aoHipo: (Int) -> Unit,
) {
    val agora = remember { LocalDateTime.now().withSecond(0).withNano(0) }
    var valor by remember { mutableStateOf("") }
    var momento by remember { mutableStateOf(momentoSugerido(agora)) }
    var erro by remember { mutableStateOf<String?>(null) }
    val foco = remember { FocusRequester() }
    LaunchedEffect(Unit) { foco.requestFocus() }

    fun salvar() {
        val v = valor.trim().toIntOrNull()
        if (v == null || v !in 20..600) { erro = "Informe uma glicemia entre 20 e 600 mg/dL."; return }
        vm.salvar(Medicao(dataHora = agora, valor = v, momento = momento))
        aoFechar()
        if (v < vm.config.alvoMin) aoHipo(v)
    }

    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Glicemia agora", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = valor,
            onValueChange = { n -> if (n.length <= 3 && n.all { it.isDigit() }) { valor = n; erro = null } },
            label = { Text("mg/dL") },
            textStyle = TextStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"),
            singleLine = true,
            isError = erro != null,
            supportingText = erro?.let { { Text(it) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { salvar() }),
            modifier = Modifier.fillMaxWidth().focusRequester(foco),
        )
        EscolhaChips(MomentoMedicao.entries, momento, { it.rotulo }, { momento = it })
        Button(onClick = { salvar() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Salvar glicemia", fontWeight = FontWeight.Bold)
        }
        Text("Ou registre outra coisa", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        TipoRegistro.entries.forEach { t ->
            Row(
                Modifier.fillMaxWidth().clickable { aoEscolher(t) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconeRegistro(exemploDe(t), 40.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(if (t == TipoRegistro.MEDICAO) "Glicemia com mais detalhes" else t.rotulo, style = MaterialTheme.typography.titleMedium)
                    Text(t.descricao, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
