package org.netbeans.modules.jvi.completion;

import com.raelity.jvi.core.Options;
import com.raelity.jvi.options.DebugOption;
import java.util.logging.Level;
import javax.swing.text.JTextComponent;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionTask;

public class CommandNameProvider implements CompletionProvider {

    private DebugOption dbgCompl;

    public CommandNameProvider()
    {
        dbgCompl = (DebugOption)Options.getOption(Options.dbgCompletion);
    }

    @Override
    public CompletionTask createTask(int queryType, JTextComponent jtc)
    {
        if(queryType != CompletionProvider.COMPLETION_QUERY_TYPE) {
            return null;
        }
        dbgCompl.println(Level.INFO, "CREATE_TASK: CommandName");
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
