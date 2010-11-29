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

import com.raelity.jvi.ViAppView;
import com.raelity.jvi.core.Options;
import com.raelity.jvi.manager.AppViews;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.options.DebugOption;
import com.raelity.jvi.swing.CommandLine;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.swing.Action;
import javax.swing.ImageIcon;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.completion.Completion;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.jvi.impl.NbAppView;
import org.netbeans.spi.editor.completion.CompletionItem;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.CompletionUtilities;
import org.openide.ErrorManager;
import org.openide.windows.TopComponent;

/**
 * Filename completion for ":e#" command.
 * 
 * @author Ernie Rael <err at raelity.com>
 */
public class EditAlternateTask implements CompletionTask
{
    private static DebugOption dbgCompl;
    private static final int ITEM_SELECTED = 1;
    private static final int ITEM_MODIFIED = 2;

    JTextComponent jtc;
    List<EditAlternateItem> query = new ArrayList<EditAlternateItem>();

    public EditAlternateTask(JTextComponent jtc)
    {
        this.jtc = jtc;
        dbgCompl = (DebugOption)Options.getOption(Options.dbgCompletion);
    }

    private static boolean filterDigit(String filter)
    {
        return filter.length() > 0 && Character.isDigit(filter.charAt(0));
    }

    @Override
    public void query(CompletionResultSet resultSet)
    {
        if(!CcCompletion.isEditAlternate(jtc.getDocument())) {
            resultSet.finish();
            return;
        }
        buildQueryResult();
        filterResult(resultSet, "QUERY EA");
    }

    @Override
    public void refresh(CompletionResultSet resultSet)
    {
        if(resultSet == null) {
            dbgCompl.println("REFRESH EA with null resultSet");
            return;
        }
        if(!CcCompletion.isEditAlternate(jtc.getDocument())) {
            resultSet.finish();
            return;
        }
        if(query == null)
            buildQueryResult();
        filterResult(resultSet, "REFRESH EA");
    }

    @Override
    public void cancel()
    {
        dbgCompl.println("CANCEL EA:");
        Completion.get().hideAll();
    }

    private void buildQueryResult()
    {
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
            query.add(new EditAlternateItem(
                    name,
                    String.format("%02d", wnum),
                    icon, false, flags, 2)); // offset 2 is after "e#"
        }
    }

    private void filterResult(CompletionResultSet resultSet, String tag)
    {
        String dbsString = "";
        try {
            Document doc = jtc.getDocument();
            String text = doc.getText(0, doc.getLength());
            if (dbgCompl.getBoolean())
                dbsString = tag + ": \'" + text + "\'";
            if (CcCompletion.isEditAlternate(doc)) {
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
                for (EditAlternateItem item : query) {
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

    // getTxtFont taken from core/swing/tabcontrol/src/
    // org/netbeans/swing/tabcontrol/plaf/AbstractViewTabDisplayerUI.java
    ///// private Font getTxtFont()
    ///// {
    /////     //font = UIManager.getFont("TextField.font");
    /////     //Font font = UIManager.getFont("Tree.font");
    /////     Font txtFont;
    /////     txtFont = (Font)UIManager.get("windowTitleFont");
    /////     if (txtFont == null)
    /////         txtFont = new Font("Dialog", Font.PLAIN, 11);
    /////     else if (txtFont.isBold())
    /////         // don't use deriveFont() - see #49973 for details
    /////         txtFont =
    /////                 new Font(txtFont.getName(), Font.PLAIN,
    /////                          txtFont.getSize());
    /////     return txtFont;
    ///// }

    private static class EditAlternateItem implements CompletionItem
    {
        private static String fieldColorCode = "0000B2";
        private static Color fieldColor =
                Color.decode("0x" + EditAlternateItem.fieldColorCode);
        //private static Color fieldColor = Color.decode("0xC0C0B2");
        private static ImageIcon fieldIcon = null;
        private ImageIcon icon;
        private String name;
        private String nameLabel; // with padding for wide icon
        private String num;
        private boolean fFilterDigit;
        private int startOffset;

        EditAlternateItem(String name, String num, ImageIcon icon,
                                boolean fFilterDigit, int flags,
                                int dotOffset)
        {
            this.name = name;
            this.num = num;
            this.startOffset = dotOffset;
            this.icon = icon != null ? icon : EditAlternateItem.fieldIcon;
            this.fFilterDigit = fFilterDigit;
            // + "<font color=\"#000000\">"
            nameLabel =
                    "<html>&nbsp;&nbsp;"
                    + ((flags & ITEM_SELECTED) != 0 ? "<b>" : "")
                    + "<font color=\"#"
                    + ((flags & ITEM_MODIFIED) != 0
                        ? EditAlternateItem.fieldColorCode : "000000")
                    + "\">"
                    +     name
                    +     ((flags & ITEM_MODIFIED) != 0 ? " *" : "")
                    + "</font>"
                    + ((flags & ITEM_SELECTED) != 0 ? "</b>" : "")
                    + "</html>";
            //if(fieldIcon == null){
            //    fieldIcon = new ImageIcon(Utilities.loadImage(
            //            "org/netbeans/modules/textfiledictionary/icon.png"));
            //}
        }

        @Override
        public void defaultAction(JTextComponent jtc)
        {
            if (dbgCompl.getBoolean())
                System.err.println("DEFAULT ACTION EA: \'" + name + "\'");
            try {
                CcCompletion.ceInSubstitute = true;
                doSubstitute(jtc);
            } finally {
                CcCompletion.ceInSubstitute = false;
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
                System.err.println("ViCompletionItem EA: \'" + name + "\' " +
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
            if (dbgCompl.getBoolean(Level.FINER))
                System.err.println("RENDER EA: \'" + name + "\', selected " +
                        selected);
            Graphics2D g2 = (Graphics2D)g;
            CompletionUtilities.renderHtml(
                    icon, nameLabel, num, g, defaultFont,
                    selected ? Color.white : EditAlternateItem.fieldColor,
                    width, height, selected);
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
