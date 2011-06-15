/*
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * The Original Code is jvi - vi editor clone.
 *
 * The Initial Developer of the Original Code is Ernie Rael.
 * Portions created by Ernie Rael are
 * Copyright (C) 2000-2010 Ernie Rael.  All Rights Reserved.
 *
 * Contributor(s): Ernie Rael <err@raelity.com>
 */

package org.netbeans.modules.jvi.completion;

import com.raelity.jvi.ViCmdEntry;
import com.raelity.jvi.core.ColonCommands;
import com.raelity.jvi.core.ColonCommands.ColonEvent;
import com.raelity.jvi.core.Options;
import com.raelity.jvi.core.lib.CcFlag;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.options.DebugOption;
import com.raelity.jvi.swing.CommandLine;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultEditorKit.DefaultKeyTypedAction;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import javax.swing.text.TextAction;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.jvi.Module;
import org.netbeans.modules.jvi.reflect.NbUtil;

/**
 * Colon Command code completion.
 *
 * @author Ernie Rael <err at raelity.com>
 */
public class CcCompletion
{
    private CcCompletion() { }

    private static final
            Logger LOG = Logger.getLogger(CcCompletion.class.getName());

    private static DebugOption dbgCompl;
    private static FocusListener initShowCompletion;

    //
    // Bug 110237 - JTextField with EditorRegistry dependent APIs has problems
    //
    private static void fixupCodeCompletionTextComponent(JTextComponent jtc)
    {
        if (!ViManager.getHackFlag(Module.HACK_CC))
            return;
        NbUtil.EditorRegistryRegister(jtc);
        // Add Ctrl-space binding
        Keymap km = JTextComponent.getKeymap(CommandLine.COMMAND_LINE_KEYMAP);
        if (km != null) {
            KeyStroke ks = KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,
                                                  InputEvent.CTRL_MASK);
            Action act = new TextAction("vi-command-code-completion") {
                            @Override
                            public void actionPerformed(ActionEvent e)
                            {
                                Completion.get().showCompletion();
                            }
                        };
            if (km.getAction(ks) == null)
                km.addActionForKeyStroke(ks, act);
            // Let Ctrl-D bring up code completion
            ks = KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_MASK);
            km.addActionForKeyStroke(ks, act);
            // provide a default key typed action that
            // follows NB's user did the typing protocol
            km.setDefaultAction(new DefaultKeyTyped());
        }
    }

    public static void commandEntryAssist(ViCmdEntry cmdEntry, boolean enable)
    {
        if (dbgCompl == null)
            dbgCompl = (DebugOption)Options.getOption(Options.dbgCompletion);
        JTextComponent jtc = (JTextComponent)cmdEntry.getTextComponent();
        jtc.removeFocusListener(initShowCompletion);
        initShowCompletion = null;

        if (!enable) {
            // Finished, make sure everything's shutdown
            Completion.get().hideAll();
            return;
        }

        fixupCodeCompletionTextComponent(jtc);
        // If either autopopup is enabled,
        // check for starting up code completion with the
        // initial contents of the command window
        if (Options.getOption(Options.autoPopupFN).getBoolean()
            || Options.getOption(Options.autoPopupCcName).getBoolean()) {
            initShowCompletion = new StartCcFocusListener();
            if (jtc.hasFocus())
                initShowCompletion.focusGained(new FocusEvent(jtc, 0));
            else
                jtc.addFocusListener(initShowCompletion);
        }
    }

    private static class DefaultKeyTyped extends DefaultKeyTypedAction
    {
        @Override
        public void actionPerformed(ActionEvent e)
        {
            DocumentUtilities.setTypingModification(
                    getTextComponent(e).getDocument(), true);
            try {
                super.actionPerformed(e);
            } finally {
                DocumentUtilities.setTypingModification(
                        getTextComponent(e).getDocument(), false);
            }
        }
        
    }

    // This is good for one shot, it uninstalls itself
    private static class StartCcFocusListener extends FocusAdapter {
        @Override
        public void focusGained(FocusEvent e)
        {
            JTextComponent jtc = (JTextComponent)e.getComponent();
            jtc.removeFocusListener(this);
            if(Options.getOption(Options.autoPopupFN).getBoolean()
                        && isAlternateFileCompletion(jtc.getDocument())
                || Options.getOption(Options.autoPopupCcName).getBoolean())
            {
                dbgCompl.println("INIT SHOW:");
                Completion.get().showCompletion();
            }
        }
    };

    // since the same string gets checked a lot, provide a cache
    // could improve with document events detecting change
    private static String cacheCommandString;
    private static ColonEvent cacheCommandColonEvent;
    private static ColonEvent getColonEvent(String command)
    {
        if(!command.equals(cacheCommandString)) {
            cacheCommandColonEvent = ColonCommands.parseCommandNoExec(command);
            cacheCommandString = command;
        }
        return cacheCommandColonEvent;
    }

    static boolean isAlternateFileCompletion(Document doc)
    {
        try {
            String s = doc.getText(0, doc.getLength());
            if(s.trim().isEmpty())
                return false;
            else {
                return isAlternateFileCompletion(s);
            }
        } catch(BadLocationException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return false;
    }

    static boolean isAlternateFileCompletion(String command)
    {
        ColonEvent ce = getColonEvent(command);
        return ce != null
                && ce.getColonCommandItem().getFlags().contains(CcFlag.COMPL_FN)
                && ce.getNArg() > 0
                && ce.getArg(1).startsWith("#");
    }

    static String state(JTextComponent jtc)
    {
        try {
            Document doc = jtc.getDocument();
            StringBuilder sb = new StringBuilder("\"");
            sb.append(doc.getText(0, doc.getLength()));
            sb.append('"').append('(').append(jtc.getCaretPosition()).append(')');
            return sb.toString();
        } catch(BadLocationException ex) {
            return "(DOC-ERROR)";
        }
    }
}

