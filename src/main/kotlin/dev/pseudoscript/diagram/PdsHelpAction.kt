package dev.pseudoscript.diagram

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Font
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Box
import javax.swing.Action
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Toolbar action (right-aligned, so it sits at the top-right of the PseudoScript
 * tool window) that opens [PdsHelpDialog] — a one-screen intro plus a paste-ready
 * prompt for handing the language to a coding agent.
 */
class PdsHelpAction :
    AnAction("Help", "A quick PseudoScript intro and a paste-ready prompt for a coding agent", AllIcons.Actions.Help),
    RightAlignedToolbarAction,
    DumbAware {
    override fun actionPerformed(e: AnActionEvent) = PdsHelpDialog(e.project).show()
}

/**
 * A TLDR of what PseudoScript and this panel are, plus a copy-ready prompt the
 * user pastes into any coding agent to have it install the PseudoScript skill
 * (`pds lang --skill`) and write the model for them.
 */
class PdsHelpDialog(project: Project?) : DialogWrapper(project) {
    private val copyAction = object : AbstractAction(COPY_LABEL) {
        override fun actionPerformed(e: ActionEvent?) {
            CopyPasteManager.getInstance().setContents(StringSelection(AGENT_PROMPT))
            putValue(Action.NAME, "Copied!")
        }
    }

    init {
        title = "PseudoScript — Quick Help"
        setOKButtonText("Close")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = JBUI.Borders.empty(8, 12, 4, 12)
        }

        panel.add(JBLabel(TLDR_HTML).apply { alignmentX = Component.LEFT_ALIGNMENT })
        panel.add(Box.createVerticalStrut(8))

        val prompt = JBTextArea(AGENT_PROMPT).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            font = Font(Font.MONOSPACED, Font.PLAIN, font.size)
            border = JBUI.Borders.empty(8)
        }
        panel.add(
            JBScrollPane(prompt).apply {
                alignmentX = Component.LEFT_ALIGNMENT
                preferredSize = Dimension(520, 150)
            },
        )

        return JPanel(BorderLayout()).apply { add(panel, BorderLayout.CENTER) }
    }

    /** "Copy prompt" on the left, "Close" on the right. */
    override fun createActions(): Array<Action> = arrayOf(copyAction, okAction)

    companion object {
        private const val COPY_LABEL = "Copy prompt for your agent"

        private val TLDR_HTML =
            """
            <html><body style='width: 500px'>
            <b>PseudoScript</b> (<code>.pds</code>) is the language of <b>PseudoProgramming</b> — a
            <b>Model Driven Development</b> workflow: you write the <b>model</b>, and the <code>pds</code>
            toolchain generates the diagrams and docs from it. The model expresses
            <b>C4 structure</b> (system / container / component / person), <b>features</b>
            (Gherkin given/when/then), and <b>data</b> types.
            <br><br>
            By convention the model lives under <code>&lt;root&gt;/model/</code>, with its workspace
            file at <code>&lt;root&gt;/model/pds.toml</code>.
            <br><br>
            <b>This panel</b><br>
            &bull; <b>Structure</b> — your model's symbol tree (from <code>pds outline</code>).
            Click any node to render its C4 / sequence diagram; export to SVG / PNG.<br>
            &bull; <b>Docs</b> — build, serve, and live-preview the documentation site.<br>
            <br>
            Needs the <code>pds</code> binary on your PATH
            (Settings &rarr; Languages &amp; Frameworks &rarr; PseudoScript).
            <br><br>
            <b>New to PseudoProgramming? Hand it to your agent.</b> Copy the prompt below into any
            coding agent in this project — it installs the PseudoScript skill for your installed
            <code>pds</code> and writes the model for you:
            </body></html>
            """.trimIndent()

        private val AGENT_PROMPT =
            """
            I'm doing PseudoProgramming — Model Driven Development in PseudoScript (.pds).
            Help me write the model in this project.

            1. Install the PseudoScript skill from the installed pds binary: run
               `pds skill` and save its output as a skill you can load (in your
               skills directory). It's the authoring method for this pds version;
               run `pds lang` if you need the full grammar.
            2. The model lives under <root>/model/ (workspace file: model/pds.toml).
               Run `pds outline` there to see the current model structure.
            3. Write and edit .pds files against the skill. After each change, run
               `pds check <file>` to validate and `pds fmt <file>` to format.
            4. To preview a symbol's diagram: `pds svg --symbol <FQN> > out.svg`.
            """.trimIndent()
    }
}
