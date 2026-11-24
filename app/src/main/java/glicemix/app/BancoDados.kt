package glicemix.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import glicemix.data.Conversao
import glicemix.data.Persistencia
import glicemix.model.Alimento
import glicemix.model.Atividade
import glicemix.model.Origem
import glicemix.model.Configuracao
import glicemix.model.Dose
import glicemix.model.Etiquetas
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
        const val VERSAO = 3

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
                observacao TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT '',
                marcado INTEGER NOT NULL DEFAULT 0
            )""",
            "CREATE INDEX idx_medicoes_data ON medicoes(data_hora)",
            """CREATE TABLE refeicoes (
                id TEXT PRIMARY KEY NOT NULL,
                data_hora TEXT NOT NULL,
                tipo TEXT NOT NULL,
                descricao TEXT NOT NULL DEFAULT '',
                carboidratos REAL NOT NULL DEFAULT 0,
                observacao TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT '',
                marcado INTEGER NOT NULL DEFAULT 0,
                itens TEXT NOT NULL DEFAULT ''
            )""",
            "CREATE INDEX idx_refeicoes_data ON refeicoes(data_hora)",
            """CREATE TABLE doses (
                id TEXT PRIMARY KEY NOT NULL,
                data_hora TEXT NOT NULL,
                tipo TEXT NOT NULL,
                medicamento TEXT NOT NULL,
                quantidade REAL NOT NULL,
                unidade TEXT NOT NULL DEFAULT 'UI',
                observacao TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT '',
                marcado INTEGER NOT NULL DEFAULT 0,
                refeicao_id TEXT NOT NULL DEFAULT ''
            )""",
            "CREATE INDEX idx_doses_data ON doses(data_hora)",
            """CREATE TABLE atividades (
                id TEXT PRIMARY KEY NOT NULL,
                data_hora TEXT NOT NULL,
                tipo TEXT NOT NULL,
                duracao_min INTEGER NOT NULL,
                intensidade TEXT NOT NULL,
                observacao TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT '',
                marcado INTEGER NOT NULL DEFAULT 0
            )""",
            "CREATE INDEX idx_atividades_data ON atividades(data_hora)",
            """CREATE TABLE configuracao (
                chave TEXT PRIMARY KEY NOT NULL,
                valor TEXT NOT NULL
            )""",
            """CREATE TABLE alimentos_proprios (
                id TEXT PRIMARY KEY NOT NULL,
                nome TEXT NOT NULL,
                carboidratos_100 REAL NOT NULL,
                fibra_100 REAL,
                origem TEXT NOT NULL,
                porcao_nome TEXT NOT NULL DEFAULT '',
                porcao_g REAL
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
    override fun onUpgrade(db: SQLiteDatabase, antiga: Int, nova: Int) {
        if (antiga < 2) {
            // v2: etiquetas, "levar para a consulta" e vínculo dose → refeição
            TABELAS_REGISTROS.forEach { t ->
                db.execSQL("ALTER TABLE $t ADD COLUMN tags TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE $t ADD COLUMN marcado INTEGER NOT NULL DEFAULT 0")
            }
            db.execSQL("ALTER TABLE doses ADD COLUMN refeicao_id TEXT NOT NULL DEFAULT ''")
        }
        if (antiga < 3) {
            // v3: alimentos pesados dentro da refeição (TACO, rótulo, próprios) e a lista de alimentos próprios
            db.execSQL("ALTER TABLE refeicoes ADD COLUMN itens TEXT NOT NULL DEFAULT ''")
            db.execSQL(
                """CREATE TABLE alimentos_proprios (
                    id TEXT PRIMARY KEY NOT NULL, nome TEXT NOT NULL, carboidratos_100 REAL NOT NULL,
                    fibra_100 REAL, origem TEXT NOT NULL, porcao_nome TEXT NOT NULL DEFAULT '', porcao_g REAL
                )"""
            )
        }
    }

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
private fun Cursor.etiquetas(): List<String> = Etiquetas.separar(texto("tags"))
private fun Cursor.marcado(): Boolean = inteiro("marcado") == 1

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
            Medicao(c.texto("id"), Conversao.data(c.texto("data_hora")), c.inteiro("valor"), MomentoMedicao.valueOf(c.texto("momento")), c.texto("observacao"), c.etiquetas(), c.marcado())
        }
        ler("refeicoes") { c ->
            Refeicao(c.texto("id"), Conversao.data(c.texto("data_hora")), TipoRefeicao.valueOf(c.texto("tipo")), c.texto("descricao"), c.real("carboidratos"), c.texto("observacao"), c.etiquetas(), c.marcado(), Conversao.itensDeTexto(c.texto("itens")))
        }
        ler("doses") { c ->
            Dose(c.texto("id"), Conversao.data(c.texto("data_hora")), TipoDose.valueOf(c.texto("tipo")), c.texto("medicamento"), c.real("quantidade"), c.texto("unidade"), c.texto("observacao"), c.texto("refeicao_id"), c.etiquetas(), c.marcado())
        }
        ler("atividades") { c ->
            Atividade(c.texto("id"), Conversao.data(c.texto("data_hora")), c.texto("tipo"), c.inteiro("duracao_min"), Intensidade.valueOf(c.texto("intensidade")), c.texto("observacao"), c.etiquetas(), c.marcado())
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
        put("tags", Etiquetas.juntar(r.tags))
        put("marcado", if (r.marcado) 1 else 0)
        when (r) {
            is Medicao -> { put("valor", r.valor); put("momento", r.momento.name) }
            is Refeicao -> { put("tipo", r.tipo.name); put("descricao", r.descricao); put("carboidratos", r.carboidratos); put("itens", Conversao.itensParaTexto(r.itens)) }
            is Dose -> { put("tipo", r.tipo.name); put("medicamento", r.medicamento); put("quantidade", r.quantidade); put("unidade", r.unidade); put("refeicao_id", r.refeicaoId) }
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


/** Alimentos que a própria pessoa cadastrou (pelo rótulo da embalagem ou receita sua), sempre por 100 g. */
class AlimentosProprios(ctx: Context) {
    private val banco = BancoDados.de(ctx)

    private fun Cursor.realOuNulo(coluna: String): Double? = getColumnIndexOrThrow(coluna).let { if (isNull(it)) null else getDouble(it) }

    fun listar(): List<Alimento> {
        val out = mutableListOf<Alimento>()
        banco.readableDatabase.query("alimentos_proprios", null, null, null, null, null, "nome COLLATE NOCASE").use { c ->
            while (c.moveToNext()) Conversao.seguro {
                out += Alimento(
                    id = c.texto("id"), nome = c.texto("nome"), grupo = "Meus alimentos",
                    carboidratos100 = c.real("carboidratos_100"), fibra100 = c.realOuNulo("fibra_100"),
                    origem = Origem.valueOf(c.texto("origem")),
                    porcaoNome = c.texto("porcao_nome"), porcaoGramas = c.realOuNulo("porcao_g"),
                )
            }
        }
        return out
    }

    fun salvar(a: Alimento) {
        banco.transacao { db ->
            val v = ContentValues().apply {
                put("id", a.id); put("nome", a.nome); put("carboidratos_100", a.carboidratos100 ?: 0.0)
                if (a.fibra100 != null) put("fibra_100", a.fibra100) else putNull("fibra_100")
                put("origem", a.origem.name); put("porcao_nome", a.porcaoNome)
                if (a.porcaoGramas != null) put("porcao_g", a.porcaoGramas) else putNull("porcao_g")
            }
            db.insertWithOnConflict("alimentos_proprios", null, v, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun remover(id: String) {
        banco.transacao { db -> db.delete("alimentos_proprios", "id = ?", arrayOf(id)) }
    }
}
