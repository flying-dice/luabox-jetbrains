package dev.pseudoscript.diagram

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.attributes.ViewBox
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JComponent

/**
 * Paints a parsed [SVGDocument] at a scalable zoom. The diagram stays vector —
 * JSVG renders straight to the [Graphics2D], so it is crisp at any [scale].
 * Sized to the document times the zoom so a surrounding scroll pane paginates it.
 *
 * `pds svg` emits a fixed light-theme palette (dark ink, no background), so the
 * diagram is drawn on a constant light "paper" plate — as the doc site does —
 * rather than the editor background, keeping it legible under a dark IDE theme.
 */
class SvgCanvas : JComponent() {
    private var doc: SVGDocument? = null

    /** The plate the diagram sits on, set per theme by the host panel. */
    var plate: Color = Color.WHITE
    var plateBorder: Color = Color(0xD4D7DD)

    init {
        isOpaque = true
    }

    var scale: Double = 1.0
        set(value) {
            field = value.coerceIn(MIN_SCALE, MAX_SCALE)
            revalidate()
            repaint()
        }

    fun setDocument(document: SVGDocument?) {
        doc = document
        revalidate()
        repaint()
    }

    fun document(): SVGDocument? = doc

    /** The document's intrinsic (unscaled) size, or `null` if nothing is loaded. */
    fun documentSize(): Dimension? =
        doc?.size()?.let { Dimension(it.width.toInt().coerceAtLeast(1), it.height.toInt().coerceAtLeast(1)) }

    override fun getPreferredSize(): Dimension {
        val size = doc?.size() ?: return Dimension(0, 0)
        return Dimension(
            (size.width * scale).toInt().coerceAtLeast(1),
            (size.height * scale).toInt().coerceAtLeast(1),
        )
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g.create() as Graphics2D
        try {
            // Theme-matched plate (light/dark, set by the host) the diagram sits
            // on, with a hairline edge to set it off from the surround.
            g2.color = plate
            g2.fillRect(0, 0, width, height)
            g2.color = plateBorder
            g2.drawRect(0, 0, width - 1, height - 1)

            val document = doc ?: return
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            val size = document.size()
            document.render(
                this,
                g2,
                ViewBox(0f, 0f, (size.width * scale).toFloat(), (size.height * scale).toFloat()),
            )
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val MIN_SCALE = 0.05
        const val MAX_SCALE = 20.0
    }
}
