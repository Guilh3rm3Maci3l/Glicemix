package glicemix.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import glicemix.data.Conversao
import glicemix.data.Persistencia
import glicemix.model.Atividade
import glicemix.model.Configuracao
import glicemix.model.Dose
import glicemix.model.Intensidade
import glicemix.model.Medicao
import glicemix.model.MomentoMedicao
import glicemix.model.Refeicao
import glicemix.model.Registro
import glicemix.model.TipoDose
import glicemix.model.TipoRefeicao

/**
 * Banco SQLite do app (arquivo `glicemix.db` na pasta privada).
 * Uma tabela por tipo de registro, uma para a configuração e uma para os lembretes.
 * Datas são gravadas como texto ISO (2026-11-10T08:30:00), que ordena corretamente.
 */
class BancoDados private constructor(ctx: Context) : SQLiteOpenHelper(ctx, NOME, null, VERSAO) {

    companion object {
        const val NOME = "glicemix.db"
        const val VERSAO = 1

        @Volatile private var instancia: BancoDados? = null

        /** Uma única conexão para o app inteiro (telas, alarmes e reinício do celular). */
        fun de(ctx: Context): BancoDados =
            instancia ?: synchronized(this) { instancia ?: BancoDados(ctx.applicationContext).also { instancia = it } }

        val ESQUEMA = listOf(
            """CREATE TABLE medicoes (
                id TEXT PRIMARY KEY NOT NULL,
                data_hora TEXT NOT NULL,
                valor INTEGER NOT NULL,
                momento TEXT NOT NULL,
                observacao TEXT NOT NULL DEFAULT ''
            )""",
            "CREATE INDEX idx_medicoes_data ON medicoes(data_hora)",
            """CREATE TABLE refeicoes (
                id TEXT PRIMARY KEY NOT NULL,
                data_hora TEXT NOT NULL,
                tipo TEXT NOT NULL,
                descricao TEXT NOT NULL DEFAULT '',
                carboidratos REAL NOT NULL DEFAULT 0,
                observacao TEXT NOT NULL DEFAULT ''
            )""",
            "CREATE INDEX idx_refeicoes_data ON refeicoes(data_hora)",
            """CREATE TABLE doses (
                id TEXT PRIMARY KEY NOT NULL,
                data_hora TEXT NOT NULL,
                tipo TEXT NOT NULL,
                medicamento TEXT NOT NULL,
                quantidade REAL NOT NULL,
                unidade TEXT NOT NULL DEFAULT 'UI',
                observacao TEXT NOT NULL DEFAULT ''
            )""",
            "CREATE INDEX idx_doses_data ON doses(data_hora)",
            """CREATE TABLE atividades (
                id TEXT PRIMARY KEY NOT NULL,
                data_hora TEXT NOT NULL,
                tipo TEXT NOT NULL,
                duracao_min INTEGER NOT NULL,
                intensidade TEXT NOT NULL,
                observacao TEXT NOT NULL DEFAULT ''
            )""",
            "CREATE INDEX idx_atividades_data ON atividades(data_hora)",
            """CREATE TABLE configuracao (
                chave TEXT PRIMARY KEY NOT NULL,
                valor TEXT NOT NULL
            )""",
            """CREATE TABLE lembretes (
                id TEXT PRIMARY KEY NOT NULL,
                titulo TEXT NOT NULL,
                hora INTEGER NOT NULL,
                minuto INTEGER NOT NULL,
                dias TEXT NOT NULL,
                abrir TEXT NOT NULL,
                ativo INTEGER NOT NULL DEFAULT 1
            )""",
        )

        val TABELAS_REGISTROS = listOf("medicoes", "refeicoes", "doses", "atividades")
    }

    override fun onCreate(db: SQLiteDatabase) {
        ESQUEMA.forEach { db.execSQL(it) }
    }

    /** Futuras mudanças de estrutura entram aqui, por versão (ex.: if (antiga < 2) { ALTER TABLE ... }). */
    override fun onUpgrade(db: SQLiteDatabase, antiga: Int, nova: Int) {}

    /** Executa [bloco] numa transação: ou grava tudo, ou nada. */
    fun <T> transacao(bloco: (SQLiteDatabase) -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val r = bloco(db)
            db.setTransactionSuccessful()
            return r
        } finally {
            db.endTransaction()
        }
    }
}

private fun Cursor.texto(coluna: String): String = getString(getColumnIndexOrThrow(coluna)) ?: ""
private fun Cursor.inteiro(coluna: String): Int = getInt(getColumnIndexOrThrow(coluna))
private fun Cursor.real(coluna: String): Double = getDouble(getColumnIndexOrThrow(coluna))

/** Persistência dos registros e da configuração em SQLite. */
class PersistenciaSqlite(ctx: Context) : Persistencia {
    private val banco = BancoDados.de(ctx)

    override fun carregar(): Persistencia.Conteudo {
        val db = banco.readableDatabase
        val registros = mutableListOf<Registro>()
        fun ler(tabela: String, conv: (Cursor) -> Registro) {
            db.query(tabela, null, null, null, null, null, "data_hora").use { c ->
                while (c.moveToNext()) Conversao.seguro { conv(c) }?.let { registros += it }
            }
        }
        ler("medicoes") { c ->
            Medicao(c.texto("id"), Conversao.data(c.texto("data_hora")), c.inteiro("valor"), MomentoMedicao.valueOf(c.texto("momento")), c.texto("observacao"))
        }
        ler("refeicoes") { c ->
            Refeicao(c.texto("id"), Conversao.data(c.texto("data_hora")), TipoRefeicao.valueOf(c.texto("tipo")), c.texto("descricao"), c.real("carboidratos"), c.texto("observacao"))
        }
        ler("doses") { c ->
            Dose(c.texto("id"), Conversao.data(c.texto("data_hora")), TipoDose.valueOf(c.texto("tipo")), c.texto("medicamento"), c.real("quantidade"), c.texto("unidade"), c.texto("observacao"))
        }
        ler("atividades") { c ->
            Atividade(c.texto("id"), Conversao.data(c.texto("data_hora")), c.texto("tipo"), c.inteiro("duracao_min"), Intensidade.valueOf(c.texto("intensidade")), c.texto("observacao"))
        }
        return Persistencia.Conteudo(registros, lerConfiguracao(db))
    }

    private fun tabelaDe(r: Registro) = when (r) {
        is Medicao -> "medicoes"
        is Refeicao -> "refeicoes"
        is Dose -> "doses"
        is Atividade -> "atividades"
    }

    private fun valores(r: Registro): ContentValues = ContentValues().apply {
        put("id", r.id)
        put("data_hora", Conversao.texto(r.dataHora))
        put("observacao", r.observacao)
        when (r) {
            is Medicao -> { put("valor", r.valor); put("momento", r.momento.name) }
            is Refeicao -> { put("tipo", r.tipo.name); put("descricao", r.descricao); put("carboidratos", r.carboidratos) }
            is Dose -> { put("tipo", r.tipo.name); put("medicamento", r.medicamento); put("quantidade", r.quantidade); put("unidade", r.unidade) }
            is Atividade -> { put("tipo", r.tipo); put("duracao_min", r.duracaoMin); put("intensidade", r.intensidade.name) }
        }
    }

    override fun gravar(registros: Collection<Registro>) {
        if (registros.isEmpty()) return
        banco.transacao { db ->
            registros.forEach { r ->
                // remove o id das outras tabelas, caso o registro tenha mudado de tipo
                BancoDados.TABELAS_REGISTROS.filter { it != tabelaDe(r) }.forEach { t -> db.delete(t, "id = ?", arrayOf(r.id)) }
                db.insertWithOnConflict(tabelaDe(r), null, valores(r), SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    override fun apagar(ids: Collection<String>) {
        if (ids.isEmpty()) return
        banco.transacao { db ->
            ids.forEach { id -> BancoDados.TABELAS_REGISTROS.forEach { t -> db.delete(t, "id = ?", arrayOf(id)) } }
        }
    }

    override fun apagarTudo() {
        banco.transacao { db -> BancoDados.TABELAS_REGISTROS.forEach { t -> db.delete(t, null, null) } }
    }

    override fun gravarConfiguracao(c: Configuracao) {
        banco.transacao { db ->
            mapOf(
                "nome" to c.nome, "hipoSevera" to c.hipoSevera.toString(), "alvoMin" to c.alvoMin.toString(),
                "alvoMax" to c.alvoMax.toString(), "hiperSevera" to c.hiperSevera.toString(),
            ).forEach { (k, v) ->
                db.insertWithOnConflict("configuracao", null, ContentValues().apply { put("chave", k); put("valor", v) }, SQLiteDatabase.CONFLICT_REPLACE)
            }
        }
    }

    private fun lerConfiguracao(db: SQLiteDatabase): Configuracao? {
        val mapa = mutableMapOf<String, String>()
        db.query("configuracao", null, null, null, null, null, null).use { c ->
            while (c.moveToNext()) mapa[c.texto("chave")] = c.texto("valor")
        }
        if (mapa.isEmpty()) return null
        val p = Configuracao()
        fun int(k: String, d: Int) = mapa[k]?.toIntOrNull() ?: d
        return Configuracao(
            nome = mapa["nome"] ?: "",
            hipoSevera = int("hipoSevera", p.hipoSevera), alvoMin = int("alvoMin", p.alvoMin),
            alvoMax = int("alvoMax", p.alvoMax), hiperSevera = int("hiperSevera", p.hiperSevera),
        )
    }
}
