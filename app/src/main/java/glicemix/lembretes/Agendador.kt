package glicemix.lembretes

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import glicemix.app.MainActivity
import com.example.glicemixii.R
import glicemix.telas.TipoRegistro
import java.time.LocalDateTime
import java.time.ZoneId

/** Programa os alarmes dos lembretes no AlarmManager. */
object Agendador {
    const val ACAO_DISPARAR = "glicemix.lembrete.DISPARAR"
    const val ACAO_ADIAR = "glicemix.lembrete.ADIAR"
    const val EXTRA_ID = "glicemix.id"
    const val EXTRA_TITULO = "glicemix.titulo"
    const val EXTRA_TEXTO = "glicemix.texto"
    const val EXTRA_ABRIR = "glicemix.abrir"
    const val EXTRA_RECORRENTE = "glicemix.recorrente"
    private const val JANELA_MS = 10 * 60 * 1000L

    fun agendarTodos(ctx: Context) = LembretesRepo(ctx).listar().forEach { agendar(ctx, it) }

    fun agendar(ctx: Context, l: Lembrete) {
        cancelar(ctx, l.id)
        if (!l.ativo) return
        val quando = l.proxima(LocalDateTime.now()) ?: return
        programar(ctx, l.id, l.titulo, "Toque para registrar no Glicemix", l.abrir, quando, recorrente = true)
    }

    /** Lembrete de uma vez só (pós-refeição, adiamento). */
    fun agendarUnico(ctx: Context, id: String, titulo: String, texto: String, abrir: TipoRegistro, quando: LocalDateTime) =
        programar(ctx, id, titulo, texto, abrir, quando, recorrente = false)

    fun cancelar(ctx: Context, id: String) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(pendente(ctx, id, "", "", TipoRegistro.MEDICAO, false))
    }

    fun podeHorarioExato(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ctx.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    }

    private fun pendente(ctx: Context, id: String, titulo: String, texto: String, abrir: TipoRegistro, recorrente: Boolean): PendingIntent {
        val i = Intent(ctx, ReceptorLembrete::class.java).apply {
            action = ACAO_DISPARAR
            putExtra(EXTRA_ID, id)
            putExtra(EXTRA_TITULO, titulo)
            putExtra(EXTRA_TEXTO, texto)
            putExtra(EXTRA_ABRIR, abrir.name)
            putExtra(EXTRA_RECORRENTE, recorrente)
        }
        return PendingIntent.getBroadcast(ctx, id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    @SuppressLint("MissingPermission")
    private fun programar(ctx: Context, id: String, titulo: String, texto: String, abrir: TipoRegistro, quando: LocalDateTime, recorrente: Boolean) {
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val ms = quando.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pendente(ctx, id, titulo, texto, abrir, recorrente)
        if (podeHorarioExato(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        else am.setWindow(AlarmManager.RTC_WAKEUP, ms, JANELA_MS, pi) // sem permissão: até 10 min de tolerância
    }
}

object Notificacoes {
    private const val CANAL = "lembretes"

    fun permitidas(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun criarCanal(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val canal = NotificationChannel(CANAL, "Lembretes", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Lembretes de medição, insulina e medicamentos"
        }
        nm.createNotificationChannel(canal)
    }

    @SuppressLint("MissingPermission")
    fun mostrar(ctx: Context, id: String, titulo: String, texto: String, abrir: String) {
        if (!permitidas(ctx)) return
        criarCanal(ctx)
        val abrirApp = Intent(ctx, MainActivity::class.java).apply {
            action = "glicemix.ABRIR.$id"   // ação única para não reaproveitar o PendingIntent de outro lembrete
            putExtra(Agendador.EXTRA_ABRIR, abrir)
            putExtra(Agendador.EXTRA_ID, id)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val piAbrir = PendingIntent.getActivity(ctx, id.hashCode(), abrirApp, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val adiar = Intent(ctx, ReceptorLembrete::class.java).apply {
            action = Agendador.ACAO_ADIAR
            putExtra(Agendador.EXTRA_ID, id)
            putExtra(Agendador.EXTRA_TITULO, titulo)
            putExtra(Agendador.EXTRA_TEXTO, texto)
            putExtra(Agendador.EXTRA_ABRIR, abrir)
        }
        val piAdiar = PendingIntent.getBroadcast(ctx, id.hashCode() + 1, adiar, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CANAL)
            .setSmallIcon(R.drawable.ic_notificacao)
            .setColor(0xFF0E7C86.toInt())
            .setContentTitle(titulo)
            .setContentText(texto)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(piAbrir)
            .addAction(0, "Registrar", piAbrir)
            .addAction(0, "Adiar 15 min", piAdiar)
            .build()
        NotificationManagerCompat.from(ctx).notify(id.hashCode(), n)
    }

    fun cancelar(ctx: Context, id: String) = NotificationManagerCompat.from(ctx).cancel(id.hashCode())
}

/** Recebe os alarmes: mostra a notificação e já programa o próximo disparo. */
class ReceptorLembrete : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra(Agendador.EXTRA_ID) ?: return
        val titulo = intent.getStringExtra(Agendador.EXTRA_TITULO) ?: "Glicemix"
        val texto = intent.getStringExtra(Agendador.EXTRA_TEXTO) ?: ""
        val abrir = intent.getStringExtra(Agendador.EXTRA_ABRIR) ?: TipoRegistro.MEDICAO.name
        when (intent.action) {
            Agendador.ACAO_DISPARAR -> {
                Notificacoes.mostrar(ctx, id, titulo, texto, abrir)
                if (intent.getBooleanExtra(Agendador.EXTRA_RECORRENTE, false)) {
                    LembretesRepo(ctx).listar().firstOrNull { it.id == id }?.let { Agendador.agendar(ctx, it) }
                }
            }
            Agendador.ACAO_ADIAR -> {
                Notificacoes.cancelar(ctx, id)
                val tipo = TipoRegistro.entries.firstOrNull { it.name == abrir } ?: TipoRegistro.MEDICAO
                Agendador.agendarUnico(ctx, "adiado-$id", titulo, texto, tipo, LocalDateTime.now().plusMinutes(15))
            }
        }
    }
}

/** Alarmes se perdem ao reiniciar o celular, mudar o fuso ou atualizar o app: reprograma todos. */
class ReceptorSistema : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Agendador.agendarTodos(ctx)
    }
}
