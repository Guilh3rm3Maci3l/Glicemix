package glicemix.data

import glicemix.model.*
import java.io.File
import java.util.Properties

/**
 * Persistência em arquivos CSV (um por tipo de registro) e `config.properties`.
 * Cada gravação reescreve apenas os arquivos dos tipos alterados, via arquivo temporário,
 * para que uma interrupção no meio não corrompa os dados.
 */
class PersistenciaCsv(val pasta: File) : Persistencia {

    private val atuais = linkedMapOf<String, Registro>()

    private enum class Tipo(val arquivo: String, val cabecalho: List<String>) {
        MEDICAO("medicoes.csv", listOf("id", "dataHora", "valor", "momento", "obs")),
        REFEICAO("refeicoes.csv", listOf("id", "dataHora", "tipo", "descricao", "carboidratos", "obs")),
        DOSE("doses.csv", listOf("id", "dataHora", "tipo", "medicamento", "quantidade", "unidade", "obs")),
        ATIVIDADE("atividades.csv", listOf("id", "dataHora", "tipo", "duracaoMin", "intensidade", "obs")),
    }

    private fun tipo(r: Registro) = when (r) {
        is Medicao -> Tipo.MEDICAO
        is Refeicao -> Tipo.REFEICAO
        is Dose -> Tipo.DOSE
        is Atividade -> Tipo.ATIVIDADE
    }

    init { pasta.mkdirs() }

    private fun arquivo(nome: String) = File(pasta, nome)

    override fun carregar(): Persistencia.Conteudo {
        atuais.clear()
        fun ler(t: Tipo, conv: (List<String>) -> Registro) {
            val f = arquivo(t.arquivo)
            if (!f.exists()) return
            Csv.ler(f.readText(Charsets.UTF_8)).drop(1).forEach { c -> Conversao.seguro { conv(c) }?.let { atuais[it.id] = it } }
        }
        ler(Tipo.MEDICAO, Conversao::medicao)
        ler(Tipo.REFEICAO, Conversao::refeicao)
        ler(Tipo.DOSE, Conversao::dose)
        ler(Tipo.ATIVIDADE, Conversao::atividade)
        return Persistencia.Conteudo(atuais.values.toList(), lerConfiguracao())
    }

    override fun gravar(registros: Collection<Registro>) {
        if (registros.isEmpty()) return
        val afetados = mutableSetOf<Tipo>()
        registros.forEach { r ->
            atuais[r.id]?.let { afetados += tipo(it) } // o tipo antigo, se o id já existia
            atuais[r.id] = r
            afetados += tipo(r)
        }
        afetados.forEach(::escrever)
    }

    override fun apagar(ids: Collection<String>) {
        val afetados = ids.mapNotNull { atuais.remove(it) }.map(::tipo).toSet()
        afetados.forEach(::escrever)
    }

    override fun apagarTudo() {
        atuais.clear()
        Tipo.entries.forEach(::escrever)
    }

    private fun escrever(t: Tipo) {
        val linhas = listOf(t.cabecalho) + atuais.values.filter { tipo(it) == t }.sortedBy { it.dataHora }.map(Conversao::campos)
        val tmp = arquivo("${t.arquivo}.tmp")
        tmp.writeText(linhas.joinToString("\n") { Csv.linha(it) } + "\n", Charsets.UTF_8)
        val destino = arquivo(t.arquivo)
        if (destino.exists()) destino.delete()
        tmp.renameTo(destino)
    }

    override fun gravarConfiguracao(c: Configuracao) {
        val p = Properties()
        p["nome"] = c.nome
        p["hipoSevera"] = c.hipoSevera.toString()
        p["alvoMin"] = c.alvoMin.toString()
        p["alvoMax"] = c.alvoMax.toString()
        p["hiperSevera"] = c.hiperSevera.toString()
        arquivo("config.properties").outputStream().use { p.store(it, "Glicemix") }
    }

    private fun lerConfiguracao(): Configuracao? {
        val f = arquivo("config.properties")
        if (!f.exists()) return null
        val p = Properties().apply { f.inputStream().use { load(it) } }
        val padrao = Configuracao()
        fun int(k: String, d: Int) = p.getProperty(k)?.toIntOrNull() ?: d
        return Configuracao(
            nome = p.getProperty("nome") ?: "",
            hipoSevera = int("hipoSevera", padrao.hipoSevera),
            alvoMin = int("alvoMin", padrao.alvoMin),
            alvoMax = int("alvoMax", padrao.alvoMax),
            hiperSevera = int("hiperSevera", padrao.hiperSevera),
        )
    }
}
