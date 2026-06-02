package dev.pseudoscript.diagram

import com.github.weisj.jsvg.SVGDocument
import com.github.weisj.jsvg.attributes.ViewBox
import com.github.weisj.jsvg.parser.SVGLoader
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.LafManagerListener
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Cursor
import java.awt.Point
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import kotlin.math.min

/**
 * The diagram editor's UI: a zoomable [SvgCanvas] in a scroll pane, an action
 * toolbar (zoom / fit / refresh), and a status card for loading and error
 * states. Renders whatever its [DiagramVirtualFile] currently targets, calling
 * `pds svg` off the EDT and swapping the parsed document back on it.
 */
class DiagramPanel(
    private val project: Project,
    private val file: DiagramVirtualFile,
) : JPanel(BorderLayout()), Disposable {

    private val canvas = SvgCanvas()
    private val scroll = JBScrollPane(canvas)
    private val status = JBLabel("", SwingConstants.CENTER)
    private val cards = CardLayout()
    private val center = JPanel(cards)
    private val titleLabel = JBLabel()
    private val zoomLabel = JBLabel("")
    private val reloadListener: () -> Unit = ::reloadOnEdt

    // Bumped on each reload so a slow render that finishes after the user has
    // already picked something else is discarded instead of flashing in.
    @Volatile
    private var loadToken = 0

    // The raw SVG of the diagram currently on screen, kept so it can be exported.
    // Null while loading or in an error state.
    private var currentSvg: String? = null

    init {
        status.border = JBUI.Borders.empty(16)
        center.add(scroll, CARD_CANVAS)
        center.add(JPanel(BorderLayout()).apply { add(status, BorderLayout.CENTER) }, CARD_STATUS)
        add(buildToolbar(), BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)
        installInteractions()
        file.addReloadListener(reloadListener)
        // Re-render in the new palette when the IDE theme switches (light ⇆ dark).
        ApplicationManager.getApplication().messageBus
            .connect(this)
            .subscribe(LafManagerListener.TOPIC, LafManagerListener { reloadOnEdt() })
        reload()
    }

    private fun buildToolbar(): JComponent {
        val group = DefaultActionGroup().apply {
            add(action("Zoom Out", AllIcons.General.ZoomOut) { zoomBy(1.0 / ZOOM_STEP) })
            add(action("Zoom In", AllIcons.General.ZoomIn) { zoomBy(ZOOM_STEP) })
            add(action("Fit to Window", AllIcons.General.FitContent) { fitToWindow() })
            add(action("Actual Size", AllIcons.General.ActualZoom) { setScale(1.0) })
            addSeparator()
            add(exportAction())
            add(action("Refresh", AllIcons.Actions.Refresh) { reload() })
        }
        val toolbar = ActionManager.getInstance().createActionToolbar(TOOLBAR_PLACE, group, true)
        toolbar.targetComponent = this
        zoomLabel.border = JBUI.Borders.empty(0, 8)
        titleLabel.border = JBUI.Borders.empty(0, 8)
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 0, 0, 1, 0)
            add(toolbar.component, BorderLayout.WEST)
            add(titleLabel, BorderLayout.CENTER)
            add(zoomLabel, BorderLayout.EAST)
        }
    }

    private fun action(text: String, icon: Icon, run: () -> Unit): AnAction =
        object : AnAction(text, text, icon), DumbAware {
            override fun actionPerformed(e: AnActionEvent) = run()
        }

    /** Export action (SVG vector or PNG raster), enabled while a diagram shows. */
    private fun exportAction(): AnAction =
        object : AnAction("Export…", "Save the current diagram as SVG or PNG", AllIcons.Actions.Download), DumbAware {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = currentSvg != null
            }

            override fun actionPerformed(e: AnActionEvent) = export()
        }

    /**
     * Save the current diagram. The chosen extension decides the format: `.png`
     * rasterises the vector at [PNG_SCALE] for pasting into docs, anything else
     * writes the raw `pds svg` source verbatim.
     */
    private fun export() {
        val svg = currentSvg ?: return
        val descriptor = FileSaverDescriptor("Export Diagram", "Save the diagram as SVG or PNG", "svg", "png")
        val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, this)
        val wrapper = dialog.save(null as VirtualFile?, suggestedFileName()) ?: return
        val out = wrapper.file
        try {
            if (out.extension.equals("png", ignoreCase = true)) {
                val document = canvas.document()
                    ?: SVGLoader().load(ByteArrayInputStream(svg.toByteArray(StandardCharsets.UTF_8)))
                    ?: throw IOException("the diagram could not be rasterised")
                ImageIO.write(renderToImage(document, PNG_SCALE), "png", out)
            } else {
                out.writeText(svg, StandardCharsets.UTF_8)
            }
            VfsUtil.markDirtyAndRefresh(true, false, false, out)
            NotificationGroupManager.getInstance()
                .getNotificationGroup("PseudoScript")
                .createNotification("Saved ${out.name}", NotificationType.INFORMATION)
                .notify(project)
        } catch (e: IOException) {
            Messages.showErrorDialog(this, "Could not export the diagram: ${e.message}", "Export Diagram")
        }
    }

    /** Rasterise [document] to an ARGB image at [scale]× its intrinsic size. */
    private fun renderToImage(document: SVGDocument, scale: Double): BufferedImage {
        val size = document.size()
        val width = (size.width * scale).toInt().coerceAtLeast(1)
        val height = (size.height * scale).toInt().coerceAtLeast(1)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g2 = image.createGraphics()
        try {
            // Opaque background matching the on-screen plate (light or dark) so
            // the themed diagram reads wherever the PNG is pasted.
            g2.color = canvas.plate
            g2.fillRect(0, 0, width, height)
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            document.render(null, g2, ViewBox(0f, 0f, width.toFloat(), height.toFloat()))
        } finally {
            g2.dispose()
        }
        return image
    }

    /** A filesystem-safe name for the current diagram (its FQN, or `context`). */
    private fun suggestedFileName(): String {
        val base = file.target?.fqn ?: "context"
        return base.replace("::", ".").replace(Regex("[^A-Za-z0-9._-]"), "_") + ".svg"
    }

    // --- interactions: ctrl/cmd-wheel zoom, drag to pan ---------------------

    private fun installInteractions() {
        scroll.addMouseWheelListener { e ->
            if (e.isControlDown || e.isMetaDown) {
                zoomBy(if (e.preciseWheelRotation < 0) ZOOM_STEP else 1.0 / ZOOM_STEP)
                e.consume()
            } else {
                scroll.parent?.dispatchEvent(e)
            }
        }
        val panner = object : MouseAdapter() {
            private var origin: Point? = null
            override fun mousePressed(e: MouseEvent) {
                origin = e.point
                canvas.cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
            }

            override fun mouseReleased(e: MouseEvent) {
                origin = null
                canvas.cursor = Cursor.getDefaultCursor()
            }

            override fun mouseDragged(e: MouseEvent) {
                val start = origin ?: return
                val view = scroll.viewport
                val pos = view.viewPosition
                pos.translate(start.x - e.x, start.y - e.y)
                canvas.scrollRectToVisible(java.awt.Rectangle(pos, view.extentSize))
            }
        }
        canvas.addMouseListener(panner)
        canvas.addMouseMotionListener(panner)
    }

    // --- rendering ----------------------------------------------------------

    private fun reloadOnEdt() {
        val app = ApplicationManager.getApplication()
        if (app.isDispatchThread) reload() else app.invokeLater(::reload, ModalityState.any())
    }

    private fun reload() {
        val target = file.target
        if (target == null) {
            showStatus("Select a node in the PseudoScript tool window to render its diagram.")
            return
        }
        val token = ++loadToken
        titleLabel.text = target.title
        // Render in the IDE's current theme, and sit the diagram on a matching plate.
        val dark = !JBColor.isBright()
        canvas.plate = if (dark) DARK_PLATE else LIGHT_PLATE
        canvas.plateBorder = if (dark) DARK_BORDER else LIGHT_BORDER
        val theme = if (dark) "dark" else "light"
        showStatus("Rendering ${target.title}…")
        ApplicationManager.getApplication().executeOnPooledThread {
            val result =
                if (target.fqn != null) PdsCli.symbolSvg(target.workspaceDir, target.fqn, theme)
                else PdsCli.contextSvg(target.workspaceDir, theme)
            val document = (result as? PdsResult.Ok)?.value?.let(::parse)
            ApplicationManager.getApplication().invokeLater({
                if (token != loadToken) return@invokeLater
                when {
                    result is PdsResult.Err -> showStatus("Could not render ${target.title}.\n\n${result.message}")
                    document == null -> showStatus("Could not parse the diagram for ${target.title}.")
                    else -> {
                        currentSvg = (result as PdsResult.Ok).value
                        canvas.setDocument(document)
                        cards.show(center, CARD_CANVAS)
                        SwingUtilities.invokeLater(::fitToWindow)
                    }
                }
            }, ModalityState.any())
        }
    }

    private fun parse(svg: String): SVGDocument? =
        try {
            SVGLoader().load(ByteArrayInputStream(svg.toByteArray(StandardCharsets.UTF_8)))
        } catch (e: Exception) {
            null
        }

    private fun showStatus(message: String) {
        currentSvg = null
        // Pre-wrapped HTML so multi-line messages lay out in the centered label.
        status.text = "<html><div style='text-align:center'>" +
            message.replace("&", "&amp;").replace("<", "&lt;").replace("\n", "<br>") +
            "</div></html>"
        cards.show(center, CARD_STATUS)
    }

    // --- zoom ---------------------------------------------------------------

    private fun zoomBy(factor: Double) = setScale(canvas.scale * factor)

    private fun setScale(scale: Double) {
        canvas.scale = scale
        updateZoomLabel()
    }

    private fun fitToWindow() {
        val size = canvas.documentSize()
        if (size == null) {
            updateZoomLabel()
            return
        }
        val extent = scroll.viewport.extentSize
        val fitted =
            if (extent.width <= 0 || extent.height <= 0) 1.0
            else min(extent.width.toDouble() / size.width, extent.height.toDouble() / size.height)
        canvas.scale = if (fitted.isFinite() && fitted > 0) fitted else 1.0
        updateZoomLabel()
    }

    private fun updateZoomLabel() {
        zoomLabel.text = "${(canvas.scale * 100).toInt()}%"
    }

    override fun dispose() {
        file.removeReloadListener(reloadListener)
    }

    companion object {
        private const val CARD_CANVAS = "canvas"
        private const val CARD_STATUS = "status"
        private const val TOOLBAR_PLACE = "PseudoScriptDiagram"
        private const val ZOOM_STEP = 1.2

        /** Oversampling factor for PNG export, so raster output stays sharp in docs. */
        private const val PNG_SCALE = 2.0

        // Theme-matched plate colours (the SVG itself carries theme-correct ink).
        private val LIGHT_PLATE = Color(0xFFFFFF)
        private val LIGHT_BORDER = Color(0xD4D7DD)
        private val DARK_PLATE = Color(0x1E1F22)
        private val DARK_BORDER = Color(0x3A3D44)
    }
}
