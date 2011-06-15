package org.netbeans.modules.jvi.completion;

import com.raelity.jvi.core.Options;
import com.raelity.jvi.options.DebugOption;
import java.util.logging.Level;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionTask;

/**
 * Filename completion for ":e#" command.
 * 
 * @author Ernie Rael <err at raelity.com>
 */
public class EditAlternateProvider implements CompletionProvider
{
    private DebugOption dbgCompl;

    public EditAlternateProvider()
    {
        dbgCompl = (DebugOption)Options.getOption(Options.dbgCompletion);
    }

    private String j(JTextComponent jtc) { return CcCompletion.state(jtc); }

    @Override
    public CompletionTask createTask(int queryType, JTextComponent jtc)
    {
        if(queryType != CompletionProvider.COMPLETION_QUERY_TYPE) {
            return null;
        }
        if (dbgCompl.getBoolean())
            dbgCompl.println(Level.INFO, "CREATE_TASK: EditAlternate " + j(jtc));
        return new EditAlternateTask(jtc);
    }

    @Override
    public int getAutoQueryTypes(JTextComponent jtc, String typedText)
    {
        dbgCompl.printf(Level.CONFIG,
                        "AUTO_QUERY_TYPES: EditAlternate: '%s' %s\n",
                        typedText, j(jtc));
        Document doc = jtc.getDocument();
        if ("#".equals(typedText)
                && Options.getOption(Options.autoPopupFN).getBoolean()
                && CcCompletion.isAlternateFileCompletion(doc)) {
            dbgCompl.println("SHOW:");
            return CompletionProvider.COMPLETION_QUERY_TYPE;
        }
        return 0;
    }
}
