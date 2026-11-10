package glicemix.data

import glicemix.model.*
import java.io.File
import java.time.format.DateTimeFormatter

/**
 * Guarda todos os registros em memória (a análise lê daqui) e delega a gravação
 * a uma [Persistencia]: CSV no desktop, SQLite no Android.
 */
class Repositorio(private val persistencia: Persistencia) {

    /** Desktop: arquivos CSV na pasta `~/.glicemix` (ou na propriedade de sistema `glicemix.dir`). */
    constructor(pasta: File = pastaPadrao()) : this(PersistenciaCsv(pasta))

    /** Pasta dos arquivos quando a persistência é em CSV (desktop). */
    val pasta: File get() = (persistencia as? PersistenciaCsv)?.pasta ?: File(".")

    private val _medicoes = mutableListOf<Medicao>()
    private val _refeicoes = mutableListOf<Refeicao>()
    private val _doses = mutableListOf<Dose>()
    private val _atividades = mutableListOf<Atividade>()

    val medicoes: List<Medicao> get() = _medicoes.sortedBy { it.dataHora }
    val refeicoes: List<Refeicao> get() = _refeicoes.sortedBy { it.dataHora }
    val doses: List<Dose> get() = _doses.sortedBy { it.dataHora }
    val atividades: List<Atividade> get() = _atividades.sortedBy { it.dataHora }

    val todos: List<Registro> get() = (_medicoes + _refeicoes + _doses + _atividades).sortedBy { it.dataHora }

    private var _configuracao = Configuracao()
    var configuracao: Configuracao
        get() = _configuracao
        set(valor) { _configuracao = valor; persistencia.gravarConfiguracao(valor); notificar() }

    private val ouvintes = mutableListOf<() -> Unit>()
    fun aoAlterar(ouvinte: () -> Unit) { ouvintes += ouvinte }
    private fun notificar() = ouvintes.forEach { it() }

    init {
        val c = persistencia.carregar()
        c.registros.forEach(::adicionarNaMemoria)
        c.configuracao?.let { _configuracao = it }
    }

    private fun adicionarNaMemoria(r: Registro) {
        when (r) {
            is Medicao -> _medicoes += r
            is Refeicao -> _refeicoes += r
            is Dose -> _doses += r
            is Atividade -> _atividades += r
        }
    }

    private fun removerDaMemoria(id: String): Boolean =
        _medicoes.removeAll { it.id == id } or _refeicoes.removeAll { it.id == id } or
            _doses.removeAll { it.id == id } or _atividades.removeAll { it.id == id }

    // ---------- operações ----------

    fun salvar(r: Registro) = salvarTodos(listOf(r))

    /** Insere ou substitui (pelo id) vários registros de uma vez. */
    fun salvarTodos(registros: Collection<Registro>) {
        if (registros.isEmpty()) return
        registros.forEach { removerDaMemoria(it.id); adicionarNaMemoria(it) }
        persistencia.gravar(registros)
        notificar()
    }

    fun remover(id: String) {
        if (removerDaMemoria(id)) {
            persistencia.apagar(listOf(id))
            notificar()
        }
    }

    fun limparTudo() {
        _medicoes.clear(); _refeicoes.clear(); _doses.clear(); _atividades.clear()
        persistencia.apagarTudo()
        notificar()
    }

    /** Exporta todos os registros numa única planilha, útil para levar à consulta. */
    fun exportarCsv(destino: File) {
        val f = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val h = DateTimeFormatter.ofPattern("HH:mm")
        val linhas = mutableListOf(Csv.linha(listOf("Data", "Hora", "Tipo", "Detalhe", "Valor", "Unidade", "Observação")))
        todos.forEach { r ->
            val (tipo, det, valor, un) = when (r) {
                is Medicao -> Quad("Glicemia", r.momento.rotulo, r.valor.toString(), "mg/dL")
                is Refeicao -> Quad("Refeição", "${r.tipo.rotulo}: ${r.descricao}", num(r.carboidratos), "g carb.")
                is Dose -> Quad("Dose", "${r.tipo.rotulo} - ${r.medicamento}", num(r.quantidade), r.unidade)
                is Atividade -> Quad("Atividade", "${r.tipo} (${r.intensidade.rotulo})", r.duracaoMin.toString(), "min")
            }
            linhas += Csv.linha(listOf(r.dataHora.format(f), r.dataHora.format(h), tipo, det, valor.replace('.', ','), un, r.observacao))
        }
        destino.writeText("﻿" + linhas.joinToString("\n"), Charsets.UTF_8)
    }

    private data class Quad(val a: String, val b: String, val c: String, val d: String)

    // ---------- backup (para levar dados entre o desktop e o celular) ----------

    /** Texto do backup: um único CSV com todos os registros e as faixas-alvo. */
    fun backupTexto(): String {
        val linhas = mutableListOf(Csv.linha(listOf(MARCA_BACKUP, "1")))
        todos.forEach { r ->
            val tipo = when (r) { is Medicao -> "M"; is Refeicao -> "R"; is Dose -> "D"; is Atividade -> "A" }
            linhas += Csv.linha(listOf(tipo) + Conversao.campos(r))
        }
        with(configuracao) {
            linhas += Csv.linha(listOf("C", nome, hipoSevera.toString(), alvoMin.toString(), alvoMax.toString(), hiperSevera.toString()))
        }
        return linhas.joinToString("\n") + "\n"
    }

    fun exportarBackup(destino: File) = destino.writeText(backupTexto(), Charsets.UTF_8)

    data class ResultadoImportacao(val novos: Int, val atualizados: Int, val iguais: Int, val ignorados: Int) {
        val total get() = novos + atualizados + iguais
    }

    /**
     * Mescla um backup aos dados atuais. Registros são casados pelo identificador:
     * importar o mesmo arquivo duas vezes não duplica nada.
     * @param importarConfiguracao também substitui as faixas-alvo pelas do backup.
     */
    fun importarBackup(texto: String, importarConfiguracao: Boolean = false): ResultadoImportacao {
        val linhas = Csv.ler(texto.removePrefix("﻿"))
        require(linhas.firstOrNull()?.firstOrNull() == MARCA_BACKUP) {
            "Este arquivo não é um backup do Glicemix. Use a opção “Exportar backup” do outro aparelho."
        }
        val atuais = todos.associateBy { it.id }
        var novos = 0; var atualizados = 0; var iguais = 0; var ignorados = 0
        val recebidos = mutableListOf<Registro>()
        var config: Configuracao? = null
        for (c in linhas.drop(1)) {
            val resto = c.drop(1)
            val r: Registro? = when (c.firstOrNull()) {
                "M" -> Conversao.seguro { Conversao.medicao(resto) }
                "R" -> Conversao.seguro { Conversao.refeicao(resto) }
                "D" -> Conversao.seguro { Conversao.dose(resto) }
                "A" -> Conversao.seguro { Conversao.atividade(resto) }
                "C" -> { config = Conversao.seguro { Configuracao(resto[0], resto[1].toInt(), resto[2].toInt(), resto[3].toInt(), resto[4].toInt()) }; continue }
                else -> null
            }
            if (r == null) { ignorados++; continue }
            when (atuais[r.id]) {
                null -> novos++
                r -> { iguais++; continue }
                else -> atualizados++
            }
            recebidos += r
        }
        if (recebidos.isNotEmpty()) {
            recebidos.forEach { removerDaMemoria(it.id); adicionarNaMemoria(it) }
            persistencia.gravar(recebidos)
        }
        val c = config
        if (importarConfiguracao && c != null) configuracao = c else notificar()
        return ResultadoImportacao(novos, atualizados, iguais, ignorados)
    }

    companion object {
        const val MARCA_BACKUP = "GLICEMIX-BACKUP"

        fun pastaPadrao(): File =
            System.getProperty("glicemix.dir")?.let { File(it) }
                ?: File(System.getProperty("user.home"), ".glicemix")

        fun num(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else "%.1f".format(java.util.Locale.ROOT, d).replace('.', ',')
    }
}
