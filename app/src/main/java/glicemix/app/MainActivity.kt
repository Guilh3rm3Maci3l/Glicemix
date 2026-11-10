package glicemix.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import glicemix.lembretes.Agendador
import glicemix.lembretes.Notificacoes
import glicemix.telas.GlicemixApp
import glicemix.telas.GlicemixTema
import glicemix.telas.TipoRegistro

class MainActivity : ComponentActivity() {

    /** Formulário a abrir quando o app é aberto por uma notificação de lembrete. */
    private val pedido = mutableStateOf<TipoRegistro?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) tratar(intent)
        setContent {
            GlicemixTema {
                GlicemixApp(pedido = pedido.value, aoAtender = { pedido.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        tratar(intent)
    }

    private fun tratar(i: Intent?) {
        val nome = i?.getStringExtra(Agendador.EXTRA_ABRIR) ?: return
        i.getStringExtra(Agendador.EXTRA_ID)?.let { Notificacoes.cancelar(this, it) }
        pedido.value = TipoRegistro.entries.firstOrNull { it.name == nome }
    }
}
