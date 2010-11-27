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

package org.netbeans.modules.jvi.util;

import com.raelity.jvi.ViAppView;
import com.raelity.jvi.ViCmdEntry;
import com.raelity.jvi.core.Options;
import com.raelity.jvi.manager.AppViews;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.options.DebugOption;
import com.raelity.jvi.swing.CommandLine;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.KeyStroke;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;
import javax.swing.text.TextAction;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.jvi.Module;
import org.netbeans.modules.jvi.impl.NbAppView;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;
import org.openide.ErrorManager;
import org.openide.windows.TopComponent;

/**
 * Colon Command code completion.
 *
 * ViCommandCompletionProvider is registered in layer.xml
 *
 * @author Ernie Rael <err at raelity.com>
 */
public class CcCompletion
{
    private CcCompletion() { }

    private static final
            Logger LOG = Logger.getLogger(CcCompletion.class.getName());

    private static CodeComplDocListener ceDocListen;
    private static boolean ceInSubstitute;
    private static DebugOption dbgCompl;
    private static FocusListener initShowCompletion = new FocusAdapter() {
        @Override
        public void focusGained(FocusEvent e)
        {
            dbgCompl.println("INIT SHOW:");
            Completion.get().showCompletion();
        }
    };
    // Some state info about the items.
    private static final int ITEM_SELECTED = 1;
    private static final int ITEM_MODIFIED = 2;

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
    }

    public static void commandEntryAssist(ViCmdEntry cmdEntry, boolean enable)
    {
        if (dbgCompl == null)
            dbgCompl = (DebugOption)Options.getOption(Options.dbgCompletion);
        JTextComponent ceText = (JTextComponent)cmdEntry.getTextComponent();
        if (!enable) {
            // Finished, make sure everything's shutdown
            Completion.get().hideAll();
            ceText.removeFocusListener(initShowCompletion);
            ceText.getDocument().removeDocumentListener(ceDocListen);
            ceDocListen = null;
            return;
        }
        fixupCodeCompletionTextComponent(ceText);
        if (!Options.getOption(Options.autoPopupFN).getBoolean())
            return;
        ceInSubstitute = false;
        Document ceDoc = ceText.getDocument();
        ceDocListen = new CodeComplDocListener();
        ceDoc.addDocumentListener(ceDocListen);
        String text = null;
        try {
            text =
                    ceText.getDocument().
                    getText(0, ceText.getDocument().getLength());
        } catch (BadLocationException ex) {
            // see if initial conditions warrent bringing up completion
        }
        if (text != null && text.startsWith("e#")) {
            // Wait till combo's ready to go.
            ceDocListen.didIt = true;
            if (ceText.hasFocus())
                initShowCompletion.focusGained(null);
            else
                ceText.addFocusListener(initShowCompletion);
        }
    }

    private static boolean filterDigit(String filter)
    {
        return filter.length() > 0 && Character.isDigit(filter.charAt(0));
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
            try {
                if (doc.getLength() == 2 && !ceInSubstitute) {
                    if (!didIt && "e#".equals(doc.getText(0, doc.getLength()))) {
                        dbgCompl.println("SHOW:");
                        Completion.get().showCompletion();
                        didIt = true;
                    }
                } else if (doc.getLength() < 2) {
                    dbgCompl.println("HIDE:");
                    Completion.get().hideCompletion();
                    didIt = false;
                }
            } catch (BadLocationException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
        }
    }

    // registered in layer.xml
    public static class ViCommandCompletionProvider implements CompletionProvider
    {
        /*public CompletionTask createTask(int queryType,
        JTextComponent jtc) {
        return new AsyncCompletionTask(
        new ViCommandAsyncCompletionQuery(jtc), jtc);
        }*/
        @Override
        public CompletionTask createTask(int queryType, JTextComponent jtc)
        {
            if (queryType != CompletionProvider.COMPLETION_QUERY_TYPE)
                return null;
            return new ViEditAlternateTask(jtc);
        }

        @Override
        public int getAutoQueryTypes(JTextComponent jtc, String typedText)
        {
            return 0;
        }
    }

    public static class ViEditAlternateTask implements CompletionTask
    {
        JTextComponent jtc;
        List<ViEditAlternateItem> query;

        public ViEditAlternateTask(JTextComponent jtc)
        {
            this.jtc = jtc;
        }

        @Override
        public void query(CompletionResultSet resultSet)
        {
            query = new ArrayList<ViEditAlternateItem>();
            Font font = getTxtFont();
            for (ViAppView _av : AppViews.getList(AppViews.ACTIVE)) {
                NbAppView av = (NbAppView)_av;
                TopComponent tc = av.getTopComponent();
                int wnum = av.getWNum();
                int flags = 0;
                if (TopComponent.getRegistry().getActivated() == tc)
                    flags |= ITEM_SELECTED;
                ImageIcon icon =
                        tc.getIcon() != null ? new ImageIcon(tc.getIcon()) : null;
                String name = null;
                if(av.getEditor() != null) {
                    Document doc = av.getEditor().getDocument();
                    if (NbEditorUtilities.getDataObject(doc).isModified())
                        flags |= ITEM_MODIFIED;
                    name = NbEditorUtilities.getFileObject(doc).getNameExt();
                }
                if (name == null)
                    name = ViManager.getFactory()
                            .getFS().getDisplayFileName(av);
                query.add(new ViEditAlternateItem(
                        name,
                        String.format("%02d", wnum),
                        icon, false, flags, font, 2)); // offset 2 is after "e#"
            }
            genResults(resultSet, "QUERY");
        }

        @Override
        public void refresh(CompletionResultSet resultSet)
        {
            genResults(resultSet, "REFRESH");
        }

        // getTxtFont taken from core/swing/tabcontrol/src/
        // org/netbeans/swing/tabcontrol/plaf/AbstractViewTabDisplayerUI.java
        private Font getTxtFont()
        {
            //font = UIManager.getFont("TextField.font");
            //Font font = UIManager.getFont("Tree.font");
            Font txtFont;
            txtFont = (Font)UIManager.get("windowTitleFont");
            if (txtFont == null)
                txtFont = new Font("Dialog", Font.PLAIN, 11);
            else if (txtFont.isBold())
                // don't use deriveFont() - see #49973 for details
                txtFont =
                        new Font(txtFont.getName(), Font.PLAIN,
                                 txtFont.getSize());
            return txtFont;
        }

        private void genResults(CompletionResultSet resultSet, String tag)
        {
            String dbsString = "";
            try {
                Document doc = jtc.getDocument();
                String text = doc.getText(0, doc.getLength());
                if (dbgCompl.getBoolean())
                    dbsString = tag + ": \'" + text + "\'";
                if (text.startsWith("e#")) {
                    int startOffset = 2; // char after 'e#'
                    // NEEDSWORK: can't count on the caret position since
                    // this is sometimes called from the event dispatch thread
                    // so just use the entire string. Set the caret to
                    // the end of the string.
                    //int caretOffset = jtc.getCaretPosition();
                    int caretOffset = text.length();
                    // skip white space
                    for (; startOffset < caretOffset; startOffset++) {
                        if (!Character.isWhitespace(text.charAt(startOffset)))
                            break;
                    }
                    String filter = text.substring(startOffset, caretOffset);
                    if (dbgCompl.getBoolean())
                        dbsString += ", filter \'" + filter + "\'";
                    resultSet.setAnchorOffset(startOffset);
                    boolean fFilterDigit = filterDigit(filter);
                    for (ViEditAlternateItem item : query) {
                        String checkItem = fFilterDigit ? item.num : item.name;
                        if (filter.regionMatches(true, 0, checkItem, 0,
                                                 filter.length())) {
                            item.fFilterDigit = fFilterDigit;
                            resultSet.addItem(item);
                        }
                    }
                }
            } catch (BadLocationException ex) {
            }
            if (dbgCompl.getBoolean()) {
                dbsString += ", result: " + resultSet;
                System.err.println(dbsString);
            }
            resultSet.finish();
        }

        @Override
        public void cancel()
        {
            dbgCompl.println("CANCEL:");
            Completion.get().hideAll();
        }
    }

    private static class ViEditAlternateItem implements CompletionItem
    {
        private static String fieldColorCode = "0000B2";
        private static Color fieldColor =
                Color.decode("0x" + ViEditAlternateItem.fieldColorCode);
        //private static Color fieldColor = Color.decode("0xC0C0B2");
        private Font font;
        private static ImageIcon fieldIcon = null;
        private ImageIcon icon;
        private String name;
        private String nameLabel; // with padding for wide icon
        private String num;
        private boolean fFilterDigit;
        private int startOffset;

        ViEditAlternateItem(String name, String num, ImageIcon icon,
                                boolean fFilterDigit, int flags, Font font,
                                int dotOffset)
        {
            this.name = name;
            this.num = num;
            this.startOffset = dotOffset;
            this.icon = icon != null ? icon : ViEditAlternateItem.fieldIcon;
            this.fFilterDigit = fFilterDigit;
            this.font = font;
            // + "<font color=\"#000000\">"
            nameLabel =
                    "<html>&nbsp;&nbsp;" +
                    ((flags & ITEM_SELECTED) != 0 ? "<b>" : "") +
                    ((flags & ITEM_MODIFIED) != 0
                    ? "<font color=\"#" + ViEditAlternateItem.fieldColorCode +
                    "\">" : "<font color=\"#000000\">") + name +
                    ((flags & ITEM_MODIFIED) != 0 ? " *" : "") +
                    ((flags & ITEM_SELECTED) != 0 ? "</b>" : "") + "</font>" +
                    "</html>";
            //if(fieldIcon == null){
            //    fieldIcon = new ImageIcon(Utilities.loadImage(
            //            "org/netbeans/modules/textfiledictionary/icon.png"));
            //}
        }

        @Override
        public void defaultAction(JTextComponent jtc)
        {
            if (dbgCompl.getBoolean())
                System.err.println("DEFAULT ACTION: \'" + name + "\'");
            try {
                ceInSubstitute = true;
                doSubstitute(jtc);
            } finally {
                ceInSubstitute = false;
            }
            Completion.get().hideAll();
            // Go for it
            Action act = jtc.getKeymap().getAction(CommandLine.EXECUTE_KEY);
            if (act != null)
                act.actionPerformed(new ActionEvent(jtc,
                                                    ActionEvent.ACTION_PERFORMED,
                                                    "\n"));
        }

        private void doSubstitute(JTextComponent jtc)
        {
            Document doc = jtc.getDocument();
            int caretOffset = doc.getLength(); // clear to end of line
            //String value = name;
            String value = num;
            try {
                doc.remove(startOffset, caretOffset - startOffset);
                doc.insertString(startOffset, value, null);
                jtc.setCaretPosition(startOffset + value.length());
            } catch (BadLocationException e) {
                ErrorManager.getDefault().notify(ErrorManager.INFORMATIONAL, e);
            }
        }
        int hack = 0;

        @Override
        public void processKeyEvent(KeyEvent evt)
        {
            if (dbgCompl.getBoolean())
                System.err.println("ViCompletionItem: \'" + name + "\' " +
                        evt.paramString());
            if (evt.getID() == KeyEvent.KEY_PRESSED &&
                    evt.getKeyChar() == KeyEvent.VK_TAB) {
                // The logic in CompletionImpl that does getInsertPrefix
                // sets caretOffset from selection start. So if "e#n" is
                // selected and tab is entered, then caretOffset gets set to 0
                // and anchorOffset is 2 (as always) this ends up with a "-2"
                // -2 used in a String.subsequence....
                // If TAB, get rid of selection and position at end of text
                JTextComponent jtc = (JTextComponent)evt.getSource();
                jtc.setCaretPosition(jtc.getDocument().getLength());
            }
            int i = 0;
            if (evt.getKeyCode() == KeyEvent.VK_DOWN ||
                    evt.getKeyCode() == KeyEvent.VK_UP)
                hack++;
            if (evt.getID() == KeyEvent.KEY_TYPED &&
                    evt.getKeyChar() == KeyEvent.VK_TAB)
                evt.consume();
        }

        @Override
        public int getPreferredWidth(Graphics g, Font font)
        {
            return CompletionUtilities.getPreferredWidth(nameLabel, num, g, font);
        }

        @Override
        public void render(Graphics g, Font defaultFont, Color defaultColor,
                           Color backgroundColor, int width, int height,
                           boolean selected)
        {
            if (dbgCompl.getBoolean())
                System.err.println("RENDER: \'" + name + "\', selected " +
                        selected);
            Font f = font == null ? defaultFont : font;
            Graphics2D g2 = (Graphics2D)g;
            renderingHints = pushCharHint(g2, renderingHints);
            CompletionUtilities.renderHtml(
                    icon, nameLabel, num, g, f,
                    selected ? Color.white : ViEditAlternateItem.fieldColor,
                    width, height, selected);
            popCharHint(g2, renderingHints);
        }
        private RenderingHints renderingHints;
        private boolean charHintsEnabled = true;

        private RenderingHints pushCharHint(Graphics2D g2, RenderingHints rh)
        {
            if (!charHintsEnabled)
                return null;
            if (rh != null)
                rh.clear();
            else
                rh = new RenderingHints(null);
            // hints from: "How can you improve Java Fonts?"
            // http://www.javalobby.org/java/forums/m92159650.html#92159650
            // read entire discussion, KEY_TEXT_ANTIALIASING shouldn't need change
            // NOTE: problem is that KEY_AA should not be on while doing text.
            rh.put(RenderingHints.KEY_ANTIALIASING,
                   g2.getRenderingHint(RenderingHints.KEY_ANTIALIASING));
            rh.put(RenderingHints.KEY_TEXT_ANTIALIASING,
                   g2.getRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING));
            rh.put(RenderingHints.KEY_RENDERING,
                   g2.getRenderingHint(RenderingHints.KEY_RENDERING));
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                RenderingHints.VALUE_ANTIALIAS_OFF);
            // g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
            //                     RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING,
                                RenderingHints.VALUE_RENDER_QUALITY);
            return rh;
        }

        private void popCharHint(Graphics2D g2, RenderingHints rh)
        {
            if (!charHintsEnabled || rh == null)
                return;
            g2.addRenderingHints(rh);
        }

        @Override
        public CompletionTask createDocumentationTask()
        {
            return null;
        }

        @Override
        public CompletionTask createToolTipTask()
        {
            return null;
        }

        @Override
        public boolean instantSubstitution(JTextComponent component)
        {
            return false;
        }

        @Override
        public int getSortPriority()
        {
            return 0;
        }

        @Override
        public CharSequence getSortText()
        {
            return fFilterDigit ? num : name;
        }

        @Override
        public CharSequence getInsertPrefix()
        {
            return fFilterDigit ? "" : name.toLowerCase();
        }
    }

}
