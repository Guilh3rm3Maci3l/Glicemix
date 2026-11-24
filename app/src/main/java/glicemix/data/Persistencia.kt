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
        is Medicao -> listOf(r.id, texto(r.dataHora), r.valor.toString(), r.momento.name, r.observacao, Etiquetas.juntar(r.tags), marca(r.marcado))
        is Refeicao -> listOf(r.id, texto(r.dataHora), r.tipo.name, r.descricao, r.carboidratos.toString(), r.observacao, Etiquetas.juntar(r.tags), marca(r.marcado), itensParaTexto(r.itens))
        is Dose -> listOf(r.id, texto(r.dataHora), r.tipo.name, r.medicamento, r.quantidade.toString(), r.unidade, r.observacao, r.refeicaoId, Etiquetas.juntar(r.tags), marca(r.marcado))
        is Atividade -> listOf(r.id, texto(r.dataHora), r.tipo, r.duracaoMin.toString(), r.intensidade.name, r.observacao, Etiquetas.juntar(r.tags), marca(r.marcado))
    }

    private const val SEP_ITEM = "¦"
    private const val SEP_CAMPO = "§"

    /** Itens da refeição num único campo: nome§gramas§carboidratos por 100 g§origem, separados por ¦. */
    fun itensParaTexto(itens: List<ItemRefeicao>): String = itens.joinToString(SEP_ITEM) {
        listOf(it.nome.replace(SEP_ITEM, " ").replace(SEP_CAMPO, " "), it.gramas.toString(), it.carboidratos100.toString(), it.origem.name).joinToString(SEP_CAMPO)
    }

    fun itensDeTexto(s: String): List<ItemRefeicao> = s.split(SEP_ITEM).filter { it.isNotBlank() }.mapNotNull { linha ->
        seguro {
            val c = linha.split(SEP_CAMPO)
            ItemRefeicao(c[0], c[1].toDouble(), c[2].toDouble(), Origem.valueOf(c[3]))
        }
    }

    private fun marca(b: Boolean) = if (b) "1" else "0"
    private fun List<String>.tags(i: Int) = Etiquetas.separar(getOrElse(i) { "" })
    private fun List<String>.marcado(i: Int) = getOrElse(i) { "0" } == "1"

    // Os campos novos ficam no fim da linha: arquivos e backups antigos continuam legíveis.

    fun medicao(c: List<String>) = Medicao(c[0], data(c[1]), c[2].toInt(), MomentoMedicao.valueOf(c[3]), c.getOrElse(4) { "" }, c.tags(5), c.marcado(6))
    fun refeicao(c: List<String>) = Refeicao(c[0], data(c[1]), TipoRefeicao.valueOf(c[2]), c[3], c[4].toDouble(), c.getOrElse(5) { "" }, c.tags(6), c.marcado(7), itensDeTexto(c.getOrElse(8) { "" }))
    fun dose(c: List<String>) = Dose(c[0], data(c[1]), TipoDose.valueOf(c[2]), c[3], c[4].toDouble(), c[5], c.getOrElse(6) { "" }, c.getOrElse(7) { "" }, c.tags(8), c.marcado(9))
    fun atividade(c: List<String>) = Atividade(c[0], data(c[1]), c[2], c[3].toInt(), Intensidade.valueOf(c[4]), c.getOrElse(5) { "" }, c.tags(6), c.marcado(7))

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
