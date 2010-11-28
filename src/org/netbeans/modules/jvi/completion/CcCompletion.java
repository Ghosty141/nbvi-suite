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
import com.raelity.jvi.core.Options;
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
import javax.swing.KeyStroke;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import javax.swing.text.TextAction;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.lib.editor.util.swing.DocumentUtilities;
import org.netbeans.modules.jvi.Module;
import org.netbeans.modules.jvi.util.NbUtil;

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

    private static CodeComplDocListener ceDocListen;
    static boolean ceInSubstitute; // NEEDWORK: cleanup this HACK
    private static DebugOption dbgCompl;
    private static FocusListener initShowCompletion = new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e)
        {
            dbgCompl.println("INIT SHOW:");
            Completion.get().showCompletion();
        }
    };


    //
    // NOTE: Use DocumentUtilities.setTypingModification() for
    //       getAutoQueryTypes to work. See editor.completion...CompletionImpl
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
            KeyStroke ks =
                    KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,
                                           InputEvent.CTRL_MASK);
            if (km.getAction(ks) == null)
                km.addActionForKeyStroke(ks,
                     new TextAction("vi-command-code-completion") {
                            @Override
                            public void actionPerformed(ActionEvent e)
                            {
                                Completion.get().showCompletion();
                            }
                        });
        }
        // OUCH, treat any input as user typed
        DocumentUtilities.setTypingModification(jtc.getDocument(), true);
    }

    public static void commandEntryAssist(ViCmdEntry cmdEntry, boolean enable)
    {
        if (dbgCompl == null)
            dbgCompl = (DebugOption)Options.getOption(Options.dbgCompletion);
        JTextComponent jtc = (JTextComponent)cmdEntry.getTextComponent();
        if (!enable) {
            // Finished, make sure everything's shutdown
            DocumentUtilities.setTypingModification(jtc.getDocument(), false);
            Completion.get().hideAll();
            jtc.removeFocusListener(initShowCompletion);
            jtc.getDocument().removeDocumentListener(ceDocListen);
            ceDocListen = null;
            return;
        }
        fixupCodeCompletionTextComponent(jtc);
        if (!Options.getOption(Options.autoPopupFN).getBoolean())
            return;
        ceInSubstitute = false;
        Document ceDoc = jtc.getDocument();
        ceDocListen = new CodeComplDocListener();
        ceDoc.addDocumentListener(ceDocListen);
        if(isEditAlternate(ceDoc)) {
            // Wait till combo's ready to go.
            ceDocListen.didIt = true;
            if (jtc.hasFocus())
                initShowCompletion.focusGained(null);
            else
                jtc.addFocusListener(initShowCompletion);
        }
    }

    static boolean isEditAlternate(Document doc)
    {
        try {
            String s = doc.getText(0, doc.getLength());
            return s.startsWith("e#");
        } catch(BadLocationException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return false;
    }

    private static class CodeComplDocListener implements DocumentListener
    {
        boolean didIt;

        @Override
        public void changedUpdate(DocumentEvent e)
        {
        }

        @Override
        public void insertUpdate(DocumentEvent e)
        {
            ceDocCheck(e.getDocument());
        }

        @Override
        public void removeUpdate(DocumentEvent e)
        {
            ceDocCheck(e.getDocument());
        }

        private void ceDocCheck(Document doc)
        {
            if (doc.getLength() == 2 && !ceInSubstitute) {
                if (!didIt && isEditAlternate(doc)) {
                    dbgCompl.println("SHOW:");
                    Completion.get().showCompletion();
                    didIt = true;
                }
            } else if (doc.getLength() < 2) {
                dbgCompl.println("HIDE:");
                Completion.get().hideCompletion();
                didIt = false;
            }
        }
    }
}

