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
import java.awt.font.TextAttribute;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final int ITEM_ACTIVE = 1;
    private static final int ITEM_DIRTY = 2;     // modified, not written
    private static final int ITEM_MODIFIED = 4;  // out of sync with VCS
    private static final int ITEM_NEW = 8;       // not yet in VCS

    List<EditAlternateItem> query = new ArrayList<EditAlternateItem>();

    // follow shared by CompletionItem
    JTextComponent jtc;
    private Font ciDefaultFont;
    private Font ciDirtyFont;
    private int ciMaxIconWidth;
    private int ciTextOffset;

    public EditAlternateTask(JTextComponent jtc)
    {
        this.jtc = jtc;
        dbgCompl = (DebugOption)Options.getOption(Options.dbgCompletion);
    }

    private String jtc() { return CcCompletion.state(jtc); }

    private static boolean filterDigit(String filter)
    {
        return filter.length() > 0 && Character.isDigit(filter.charAt(0));
    }

    @Override
    public void query(CompletionResultSet resultSet)
    {
        if(!CcCompletion.isAlternateFileCompletion(jtc.getDocument())) {
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
            if (dbgCompl.getBoolean())
                dbgCompl.println("REFRESH EA with null resultSet " + jtc());
            return;
        }
        if(!CcCompletion.isAlternateFileCompletion(jtc.getDocument())) {
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
        if (dbgCompl.getBoolean())
            dbgCompl.println("CANCEL EA: " + jtc());
        Completion.get().hideAll();
        // drop the fonts
    }

    private void buildQueryResult()
    {
        for (ViAppView _av : AppViews.getList(AppViews.ACTIVE)) {
            NbAppView av = (NbAppView)_av;
            TopComponent tc = av.getTopComponent();
            int wnum = av.getWNum();
            int flags = 0;
            if (TopComponent.getRegistry().getActivated() == tc)
                flags |= ITEM_ACTIVE;
            ImageIcon icon =
                    tc.getIcon() != null ? new ImageIcon(tc.getIcon()) : null;
            String name = null;
            if(av.getEditor() != null) {
                Document doc = av.getEditor().getDocument();
                if (NbEditorUtilities.getDataObject(doc).isModified())
                    flags |= ITEM_DIRTY;
                name = NbEditorUtilities.getFileObject(doc).getNameExt();
            }
            if (name == null)
                name = ViManager.getFactory()
                        .getFS().getDisplayFileName(av);
            query.add(new EditAlternateItem(
                    name,
                    String.format("%02d", wnum),
                    icon, false, flags));
        }
    }

    private void filterResult(CompletionResultSet resultSet, String tag)
    {
        String dbsString = "";
        try {
            Document doc = jtc.getDocument();
            String text = doc.getText(0, doc.getLength());
            if (dbgCompl.getBoolean())
                dbsString = tag + ": \'" + jtc() + "\'";
            if (CcCompletion.isAlternateFileCompletion(doc)) {
                ciTextOffset = text.indexOf('#') + 1; // char after 'e#'
                int startOffset = ciTextOffset;
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
            dbgCompl.println(dbsString);
        }
        resultSet.finish();
    }

    // o.n.swing.tabcontrol/src/org/netbeans/swing/tabcontrol
    // Tip_Show_Opened_Documents_List=Show Opened Documents List
    // getTxtFont taken from core/swing/tabcontrol/src/
    // org/netbeans/swing/tabcontrol/plaf/AbstractViewTabDisplayerUI.java

    private class EditAlternateItem implements CompletionItem
    {
        private static final String modifiedColorCode = "0x0000B2";
        private ImageIcon icon;
        private String name;
        private String nameLabel;
        private String num;
        private boolean fFilterDigit;
        private int flags;
        private Color myColor;
        private static final String LEFT_ARROW = "\u2190"; //larr ← U+2190
        private Font myFont;

        EditAlternateItem(String name, String num, ImageIcon icon,
                                boolean fFilterDigit, int flags)
        {
            this.name = name;
            this.num = num;
            this.fFilterDigit = fFilterDigit;
            this.flags = flags;
            this.icon = icon;
            if(icon.getIconWidth() > ciMaxIconWidth)
                ciMaxIconWidth = icon.getIconWidth();

            // Font color spec'd in html overrides defaultColor.
            // Since using standard CompletionUtilities to work around icon
            // width issues, so not using "PatchedHtmlRenderer".
            //
            // DO NOT put font color spec in html.
            //

            StringBuilder sb = new StringBuilder();
            sb.append(name);
            if((flags & ITEM_ACTIVE) != 0) {
                sb.append(" ").append(LEFT_ARROW);
            }
            if((flags & ITEM_MODIFIED) != 0) {
                myColor = Color.decode(modifiedColorCode);
            }
            nameLabel = sb.toString();
        }

        private Font getFont(Font defaultFont)
        {
            if(myFont != null)
                return myFont;

            // first make sure there's local, static, default font
            if(ciDefaultFont == null) {
                Map<TextAttribute, Object> m
                        = new HashMap<TextAttribute, Object>();
                m.put(TextAttribute.FAMILY, Font.SANS_SERIF);
                m.put(TextAttribute.SIZE, defaultFont.getSize2D());
                ciDefaultFont = new Font(m);
            }
            if((flags & ITEM_DIRTY) != 0) {
                if(ciDirtyFont == null) {
                    Map<TextAttribute, Object> m
                            = new HashMap<TextAttribute, Object>();
                    m.put(TextAttribute.FAMILY, Font.SANS_SERIF);
                    // font size +1 looks better than extended width
                    // no modification (except bold) is ok, but seems too subtle
                    m.put(TextAttribute.SIZE, ciDefaultFont.getSize2D()+1);
                    m.put(TextAttribute.WEIGHT, TextAttribute.WEIGHT_BOLD);
                    ciDirtyFont = new Font(m);
                }
                myFont = ciDirtyFont;
            } else
                myFont = ciDefaultFont;
            return myFont;
        }
        private Color getColor(Color defaultColor)
        {
            if(myColor != null)
                return myColor;
            return Color.black;
        }

        @Override
        public void defaultAction(JTextComponent jtc)
        {
            if (dbgCompl.getBoolean())
                dbgCompl.println("DEFAULT ACTION EA: \'" + name + "\'");
            try {
                doSubstitute(jtc);
            } finally {
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
                doc.remove(ciTextOffset, caretOffset - ciTextOffset);
                doc.insertString(ciTextOffset, value, null);
                jtc.setCaretPosition(ciTextOffset + value.length());
            } catch (BadLocationException e) {
                ErrorManager.getDefault().notify(ErrorManager.INFORMATIONAL, e);
            }
        }
        int hack = 0;

        @Override
        public void processKeyEvent(KeyEvent evt)
        {
            if (dbgCompl.getBoolean())
                dbgCompl.println("ViCompletionItem EA: \'" + name + "\' " +
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
            font = getFont(font);
            // System.err.println(""+font+"   "+name);
            return CompletionUtilities.getPreferredWidth(
                    nameLabel, num, g, font, ciMaxIconWidth);
        }

        @Override
        public void render(Graphics g, Font defaultFont, Color defaultColor,
                           Color backgroundColor, int width, int height,
                           boolean selected)
        {
            if (dbgCompl.getBoolean(Level.FINER))
                dbgCompl.println(Level.FINER, "RENDER EA: \'" + name + "\', selected " +
                        selected);
            Color color = getColor(defaultColor);
            Font font = getFont(defaultFont);
            // System.err.println(""+font+"  "+color+"  "+name);
            Graphics2D g2 = (Graphics2D)g;
            CompletionUtilities.renderHtml(
                    icon, nameLabel, num, g, font,
                    selected ? Color.white : color,
                    width, height, selected, ciMaxIconWidth);
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
            return fFilterDigit ? num : name.toLowerCase();
        }

        @Override
        public CharSequence getInsertPrefix()
        {
            return fFilterDigit ? "" : name.toLowerCase();
        }
    }

}
