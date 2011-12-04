package org.netbeans.modules.jvi.completion;

import java.util.logging.Level;

import javax.swing.text.JTextComponent;

import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionTask;

import com.raelity.jvi.core.Options;
import com.raelity.jvi.options.DebugOption;

public class CommandNameProvider implements CompletionProvider {

    private DebugOption dbgCompl;

    public CommandNameProvider()
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
            dbgCompl.println(Level.INFO, "CREATE_TASK: CommandName " + j(jtc));
        return new CommandNameTask(jtc);
    }

    @Override
    public int getAutoQueryTypes(JTextComponent jtc, String typedText)
    {
        dbgCompl.printf(Level.CONFIG,
                        "AUTO_QUERY_TYPES: CommandName '%s'\n", typedText);
        return 0;
    }
}
