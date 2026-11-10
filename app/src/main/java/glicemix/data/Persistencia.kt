package glicemix.data

import glicemix.model.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Onde os dados ficam gravados. O [Repositorio] mantém tudo em memória para a análise
 * e chama estes métodos a cada alteração.
 *  - Desktop: [PersistenciaCsv] (arquivos CSV legíveis no Excel)
 *  - Android: SQLite (PersistenciaSqlite, no módulo do app)
 */
interface Persistencia {
    data class Conteudo(val registros: List<Registro>, val configuracao: Configuracao?)

    /** Lê tudo o que está gravado (na abertura do app). */
    fun carregar(): Conteudo

    /** Insere ou substitui (pelo id) os registros, numa única operação. */
    fun gravar(registros: Collection<Registro>)

    fun apagar(ids: Collection<String>)

    fun apagarTudo()

    fun gravarConfiguracao(c: Configuracao)
}

/** Conversão registro ⇄ lista de textos, usada nos arquivos CSV e no backup. */
object Conversao {
    private val fmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun data(s: String): LocalDateTime = LocalDateTime.parse(s, fmt)
    fun texto(t: LocalDateTime): String = t.format(fmt)

    fun campos(r: Registro): List<String> = when (r) {
        is Medicao -> listOf(r.id, texto(r.dataHora), r.valor.toString(), r.momento.name, r.observacao)
        is Refeicao -> listOf(r.id, texto(r.dataHora), r.tipo.name, r.descricao, r.carboidratos.toString(), r.observacao)
        is Dose -> listOf(r.id, texto(r.dataHora), r.tipo.name, r.medicamento, r.quantidade.toString(), r.unidade, r.observacao)
        is Atividade -> listOf(r.id, texto(r.dataHora), r.tipo, r.duracaoMin.toString(), r.intensidade.name, r.observacao)
    }

    fun medicao(c: List<String>) = Medicao(c[0], data(c[1]), c[2].toInt(), MomentoMedicao.valueOf(c[3]), c.getOrElse(4) { "" })
    fun refeicao(c: List<String>) = Refeicao(c[0], data(c[1]), TipoRefeicao.valueOf(c[2]), c[3], c[4].toDouble(), c.getOrElse(5) { "" })
    fun dose(c: List<String>) = Dose(c[0], data(c[1]), TipoDose.valueOf(c[2]), c[3], c[4].toDouble(), c[5], c.getOrElse(6) { "" })
    fun atividade(c: List<String>) = Atividade(c[0], data(c[1]), c[2], c[3].toInt(), Intensidade.valueOf(c[4]), c.getOrElse(5) { "" })

    /** Executa a conversão; em caso de linha inválida devolve null (e registra o motivo). */
    fun <T> seguro(bloco: () -> T): T? = try { bloco() } catch (e: Exception) { System.err.println("Glicemix: linha ignorada (${e.message})"); null }
}

/** Persistência em memória, útil para testes. */
class PersistenciaMemoria : Persistencia {
    private val dados = linkedMapOf<String, Registro>()
    private var cfg: Configuracao? = null
    var gravacoes = 0
        private set

    override fun carregar() = Persistencia.Conteudo(dados.values.toList(), cfg)
    override fun gravar(registros: Collection<Registro>) { registros.forEach { dados[it.id] = it }; gravacoes++ }
    override fun apagar(ids: Collection<String>) { ids.forEach { dados.remove(it) }; gravacoes++ }
    override fun apagarTudo() { dados.clear(); gravacoes++ }
    override fun gravarConfiguracao(c: Configuracao) { cfg = c }
}
