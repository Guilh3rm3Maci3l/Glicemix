package glicemix.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import glicemix.analise.AnalisadorPadroes
import glicemix.analise.Dados
import glicemix.analise.Estatistica
import glicemix.analise.Padrao
import glicemix.analise.Periodo
import glicemix.analise.Resumo
import glicemix.lembretes.Agendador
import glicemix.lembretes.Lembrete
import glicemix.lembretes.LembretesRepo
import glicemix.telas.TipoRegistro
import glicemix.data.DadosExemplo
import glicemix.data.Repositorio
import glicemix.model.Configuracao
import glicemix.model.Refeicao
import glicemix.model.Registro
import glicemix.model.maisUsados
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Ponte entre o núcleo compartilhado (o mesmo do app desktop) e a interface Compose.
 * Os dados ficam no banco SQLite do app (glicemix.db) e entram no backup automático do Android.
 */
class GlicemixViewModel(app: Application) : AndroidViewModel(app) {

    /** Registros no banco SQLite do app; a análise trabalha sobre a cópia em memória do repositório. */
    val repo = Repositorio(PersistenciaSqlite(app))
    private val repoLembretes = LembretesRepo(app)

    var periodo by mutableStateOf(Periodo.D14)
        private set
    var dados by mutableStateOf(Dados.de(repo, Periodo.D14))
        private set
    var resumo by mutableStateOf<Resumo?>(null)
        private set
    var padroes by mutableStateOf<List<Padrao>>(emptyList())
        private set
    var config by mutableStateOf(repo.configuracao)
        private set
    var vazio by mutableStateOf(true)
        private set

    var lembretes by mutableStateOf(repoLembretes.listar())
        private set
    var lembrarPosRefeicao by mutableStateOf(repoLembretes.lembrarPosRefeicao)
        private set

    init {
        recarregar()
        repo.aoAlterar { recarregar() }
        Agendador.agendarTodos(app) // garante os alarmes após instalar ou atualizar o app
    }

    private fun recarregar() {
        val d = Dados.de(repo, periodo)
        dados = d
        resumo = Estatistica.resumo(d)
        padroes = AnalisadorPadroes(d).analisar()
        config = repo.configuracao
        vazio = repo.todos.isEmpty()
    }

    fun mudarPeriodo(p: Periodo) {
        periodo = p
        recarregar()
    }

    // ---------- registros ----------

    fun salvar(r: Registro) = repo.salvar(r)

    /** Usado para registros novos: agenda o lembrete de medir 2h após refeições. */
    fun salvarTodos(r: List<Registro>) {
        repo.salvarTodos(r)
        r.filterIsInstance<Refeicao>().forEach { agendarPosRefeicao(it) }
    }

    fun remover(id: String) {
        repo.remover(id)
        Agendador.cancelar(getApplication<Application>(), "pos-$id")
    }

    fun salvarConfiguracao(c: Configuracao) { repo.configuracao = c }

    fun carregarExemplo() {
        repo.limparTudo()
        repo.salvarTodos(DadosExemplo.gerar())
    }

    fun apagarTudo() = repo.limparTudo()

    fun insulinasBolus() = maisUsados(repo.doses.filter { it.tipo.ehBolus }.map { it.medicamento })
    fun medicamentos() = maisUsados(repo.doses.map { it.medicamento })
    fun tiposAtividade() = maisUsados(repo.atividades.map { it.tipo })

    // ---------- lembretes ----------

    fun salvarLembrete(l: Lembrete) {
        repoLembretes.salvar(l)
        Agendador.agendar(getApplication<Application>(), l)
        lembretes = repoLembretes.listar()
    }

    fun removerLembrete(l: Lembrete) {
        repoLembretes.remover(l.id)
        Agendador.cancelar(getApplication<Application>(), l.id)
        lembretes = repoLembretes.listar()
    }

    fun definirPosRefeicao(ativo: Boolean) {
        repoLembretes.lembrarPosRefeicao = ativo
        lembrarPosRefeicao = ativo
    }

    private fun agendarPosRefeicao(r: Refeicao) {
        if (!lembrarPosRefeicao) return
        val quando = r.dataHora.plusHours(2)
        if (!quando.isAfter(LocalDateTime.now())) return
        Agendador.agendarUnico(
            getApplication<Application>(), "pos-${r.id}", "Medir glicemia pós-refeição",
            "Já se passaram 2h do ${r.tipo.rotulo.lowercase()}. Registre a glicemia para o Glicemix comparar.",
            TipoRegistro.MEDICAO, quando,
        )
    }

    // ---------- arquivos para compartilhar ----------

    private fun pastaExportacao() = File(getApplication<Application>().cacheDir, "exportacoes").apply { mkdirs() }

    /** Planilha para abrir no Excel ou Google Planilhas. */
    fun exportarCsv(): File = File(pastaExportacao(), "glicemix-${LocalDate.now()}.csv").also { repo.exportarCsv(it) }

    /** Relatório do período selecionado, em PDF. */
    fun gerarRelatorio(): File =
        gerarRelatorioPdf(dados, periodo.rotulo, File(pastaExportacao(), "glicemix-relatorio-${LocalDate.now()}.pdf"))

    /** Backup completo, para levar ao app desktop ou a outro celular. */
    fun exportarBackup(): File = File(pastaExportacao(), "glicemix-backup-${LocalDate.now()}.csv").also { repo.exportarBackup(it) }

    fun importarBackup(texto: String): Repositorio.ResultadoImportacao = repo.importarBackup(texto)
}
