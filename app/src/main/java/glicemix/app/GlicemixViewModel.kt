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
import glicemix.data.BaseAlimentos
import glicemix.data.DadosExemplo
import glicemix.data.Repositorio
import glicemix.analise.Episodios
import glicemix.model.Alimento
import glicemix.model.Configuracao
import glicemix.model.ItemRefeicao
import glicemix.model.Origem
import glicemix.model.Dose
import glicemix.model.Etiquetas
import glicemix.model.TipoRefeicao
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
    private val repoProprios = AlimentosProprios(app)

    /** A TACO embarcada em assets/taco.tsv (carregada na primeira busca). */
    val taco: BaseAlimentos by lazy {
        BaseAlimentos.deTsv(app.assets.open("taco.tsv").bufferedReader(Charsets.UTF_8).use { it.readText() })
    }
    var proprios by mutableStateOf(repoProprios.listar())
        private set

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

    /**
     * Exclui o registro e, se for uma refeição, as doses ligadas a ela (não ficam órfãs).
     * Devolve tudo o que foi removido, para a tela oferecer "Desfazer".
     */
    fun excluir(r: Registro): List<Registro> {
        val ligadas = if (r is Refeicao) repo.doses.filter { it.refeicaoId == r.id } else emptyList()
        (listOf(r) + ligadas).forEach { remover(it.id) }
        return listOf(r) + ligadas
    }

    fun restaurar(registros: List<Registro>) = salvarTodos(registros)

    /** Doses ligadas a uma refeição. */
    fun dosesDe(refeicaoId: String): List<Dose> = repo.doses.filter { it.refeicaoId == refeicaoId }

    /** Refeição já feita antes, para repetir com um toque (carboidratos e insulina costumam ser os mesmos). */
    data class ModeloRefeicao(val descricao: String, val tipo: TipoRefeicao, val carboidratos: Double, val insulina: String, val bolus: Double, val itens: List<ItemRefeicao> = emptyList())

    /** Refeições distintas (pela descrição), da mais recente para a mais antiga. */
    fun modelosRefeicao(limite: Int = 10): List<ModeloRefeicao> {
        val eps = Episodios.de(repo.refeicoes, repo.doses, repo.medicoes)
        return eps.asReversed()
            .filter { it.refeicao.descricao.isNotBlank() }
            .distinctBy { it.refeicao.descricao.trim().lowercase() }
            .take(limite)
            .map { e ->
                val dose = e.doses.firstOrNull { it.tipo.ehBolus }
                ModeloRefeicao(e.refeicao.descricao.trim(), e.refeicao.tipo, e.refeicao.carboidratos, dose?.medicamento ?: "", dose?.quantidade ?: 0.0, e.refeicao.itens)
            }
    }

    // ---------- alimentos e contagem de carboidratos ----------

    /** Busca na TACO e nos alimentos da própria pessoa (estes primeiro). */
    fun buscarAlimentos(consulta: String): List<Alimento> = taco.buscar(consulta, proprios)

    /** Alimentos usados nas últimas refeições, do mais recente ao mais antigo, para repetir com um toque. */
    fun alimentosRecentes(limite: Int = 12): List<Alimento> =
        repo.refeicoes.asReversed().flatMap { it.itens.asReversed() }.distinctBy { it.nome }.take(limite).map { i ->
            val daTaco = if (i.origem == Origem.TACO) taco.alimentos.firstOrNull { it.nome == i.nome } else null
            Alimento(
                id = "recente-${i.nome}", nome = i.nome, grupo = "Recentes", carboidratos100 = i.carboidratos100,
                origem = i.origem, aviso = daTaco?.aviso.orEmpty(),
                porcaoNome = proprios.firstOrNull { it.nome == i.nome }?.porcaoNome.orEmpty(),
                porcaoGramas = proprios.firstOrNull { it.nome == i.nome }?.porcaoGramas,
            )
        }

    fun salvarAlimentoProprio(a: Alimento) { repoProprios.salvar(a); proprios = repoProprios.listar() }
    fun removerAlimentoProprio(id: String) { repoProprios.remover(id); proprios = repoProprios.listar() }

    /** O que o histórico mostra para refeições parecidas (consulta, não recomendação de dose). */
    fun referenciaDose(tipo: TipoRefeicao, carboidratos: Double, ignorarRefeicao: String? = null): Episodios.ReferenciaDose? {
        val eps = Episodios.de(repo.refeicoes, repo.doses, repo.medicoes).filter { it.refeicao.id != ignorarRefeicao }
        return Episodios.referenciaDose(eps, tipo, carboidratos, repo.configuracao)
    }

    /** Circunstâncias sugeridas mais as que já foram usadas. */
    fun etiquetas(): List<String> =
        (Etiquetas.SUGERIDAS + repo.todos.flatMap { it.tags }).distinct()

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
