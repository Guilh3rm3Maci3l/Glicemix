package glicemix.app

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import glicemix.analise.Dados
import glicemix.relatorio.Alinhamento
import glicemix.relatorio.GeradorRelatorio
import glicemix.relatorio.Superficie
import java.io.File

/** Superfície do relatório sobre o Canvas de uma página de PdfDocument (1 unidade = 1 ponto). */
private class SuperficiePdf(private val c: Canvas) : Superficie {
    private val pTexto = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pForma = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pTraco = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private val tracejado = DashPathEffect(floatArrayOf(4f, 3f), 0f)

    private fun prepararTexto(tamanho: Float, negrito: Boolean) {
        pTexto.textSize = tamanho
        pTexto.typeface = if (negrito) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    override fun texto(s: String, x: Float, y: Float, tamanho: Float, cor: Int, negrito: Boolean, alinhamento: Alinhamento) {
        prepararTexto(tamanho, negrito)
        pTexto.color = cor
        pTexto.textAlign = when (alinhamento) {
            Alinhamento.ESQ -> Paint.Align.LEFT
            Alinhamento.CENTRO -> Paint.Align.CENTER
            Alinhamento.DIR -> Paint.Align.RIGHT
        }
        c.drawText(s, x, y, pTexto)
    }

    override fun largura(s: String, tamanho: Float, negrito: Boolean): Float {
        prepararTexto(tamanho, negrito)
        return pTexto.measureText(s)
    }

    override fun retangulo(x: Float, y: Float, w: Float, h: Float, cor: Int, raio: Float) {
        pForma.color = cor
        if (raio > 0) c.drawRoundRect(RectF(x, y, x + w, y + h), raio, raio, pForma) else c.drawRect(x, y, x + w, y + h, pForma)
    }

    override fun linha(x1: Float, y1: Float, x2: Float, y2: Float, cor: Int, espessura: Float, tracejada: Boolean) {
        pTraco.color = cor
        pTraco.strokeWidth = espessura
        pTraco.pathEffect = if (tracejada) tracejado else null
        c.drawLine(x1, y1, x2, y2, pTraco)
        pTraco.pathEffect = null
    }

    override fun poligono(xs: FloatArray, ys: FloatArray, cor: Int) {
        if (xs.isEmpty()) return
        val p = Path().apply { moveTo(xs[0], ys[0]); for (i in 1 until xs.size) lineTo(xs[i], ys[i]); close() }
        pForma.color = cor
        c.drawPath(p, pForma)
    }

    override fun polilinha(xs: FloatArray, ys: FloatArray, cor: Int, espessura: Float) {
        if (xs.isEmpty()) return
        val p = Path().apply { moveTo(xs[0], ys[0]); for (i in 1 until xs.size) lineTo(xs[i], ys[i]) }
        pTraco.color = cor
        pTraco.strokeWidth = espessura
        c.drawPath(p, pTraco)
    }
}

/** Gera o relatório do período em PDF (A4), com o mesmo layout do app desktop. */
fun gerarRelatorioPdf(d: Dados, descricaoPeriodo: String, destino: File): File {
    val doc = PdfDocument()
    var pagina: PdfDocument.Page? = null
    var numero = 0
    try {
        GeradorRelatorio(d, descricaoPeriodo).gerar {
            pagina?.let { doc.finishPage(it) }
            numero++
            val info = PdfDocument.PageInfo.Builder(GeradorRelatorio.LARGURA.toInt(), GeradorRelatorio.ALTURA.toInt(), numero).create()
            val nova = doc.startPage(info)
            pagina = nova
            nova.canvas.drawColor(android.graphics.Color.WHITE)
            SuperficiePdf(nova.canvas)
        }
        pagina?.let { doc.finishPage(it) }
        destino.outputStream().use { doc.writeTo(it) }
    } finally {
        doc.close()
    }
    return destino
}
