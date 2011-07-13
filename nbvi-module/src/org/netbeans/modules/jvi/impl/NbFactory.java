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
 * The Original Code is jvi - vi ed clone.
 *
 * The Initial Developer of the Original Code is Ernie Rael.
 * Portions created by Ernie Rael are
 * Copyright (C) 2000 Ernie Rael.  All Rights Reserved.
 *
 * Contributor(s): Ernie Rael <err@raelity.com>
 */
package org.netbeans.modules.jvi.impl;

import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.core.Msg;
import com.raelity.jvi.core.Util;
import com.raelity.jvi.core.Buffer;
import com.raelity.jvi.core.ColonCommands;
import com.raelity.jvi.core.G;
import com.raelity.jvi.*;
import com.raelity.jvi.swing.*;
import com.raelity.jvi.ViTextView.TAGOP;
import com.raelity.jvi.core.Misc01;
import com.raelity.jvi.core.Options;
import com.raelity.jvi.manager.AppViews;
import com.raelity.jvi.manager.Scheduler;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import java.util.Collections;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;

import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.jvi.FsAct;
import org.netbeans.modules.jvi.Module;
import org.netbeans.modules.jvi.completion.CcCompletion;
import org.netbeans.modules.jvi.reflect.NbUtil;
import org.netbeans.modules.jvi.util.NbLineMapFolding;
import org.openide.text.Line;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.windows.TopComponent;

final public class NbFactory extends SwingFactory {

    private static final Logger LOG = Logger.getLogger(NbFactory.class.getName());
    
    NbFS fs = new NbFS();
    
    public NbFactory() {
        super();
    }

    @Override
    public boolean isEnabled() {
        return Module.jViEnabled();
    }
    
    static Set<JTextComponent> getEditorSet() {
        return Collections.unmodifiableSet(
                ((NbFactory)INSTANCE).editorSet.keySet());
    }
    
    static Set<Document> getDocSet() {
        return Collections.unmodifiableSet(
                ((NbFactory)INSTANCE).docSet);
    }

    @Override
    public Class loadClass(String name) throws ClassNotFoundException {
        return Lookup.getDefault().lookup(ClassLoader.class).loadClass(name);
    }
    
    @Override
    public ViFS getFS() {
        return fs;
    }

    @Override
    public ViWindowNavigator getWindowNavigator()
    {
        List<ViAppView> avs = Misc01.getVisibleAppViews(AppViews.ALL);
        if(avs == null)
            return null;
        return new NbWindowTreeBuilder(avs);
    }

    @Override
    public ViWindowNavigator getWindowNavigator(List<ViAppView> avs)
    {
        return new NbWindowTreeBuilder(avs);
    }

    @Override
    public void setShutdownHook(Runnable hook) {
        Module.setShutdownHook(hook);
    }
    
    @Override
    public ViOutputStream createOutputStream(ViTextView tv,
                                             Object type,
                                             Object info,
                                             int priority) {
        return new NbOutputStream(tv,
                                  type.toString(),
                                  info == null ? null : info.toString(),
                                  priority);
    }
    
    private Preferences prefs;
    @Override
    public Preferences getPreferences() {
        if(prefs == null)
            prefs = NbPreferences.forModule(Module.class);
        return prefs;
    }
    
    @Override
    protected ViTextView newTextView(JTextComponent editor) {
        JEditorPane ed = (JEditorPane)editor;
        // NEEDSWORK: move this to base class or ViManager.activateFile
        NbAppView av = (NbAppView)getAppView(ed);
        if(av == null) {
            // Wow, this must be a real nomad
            TopComponent tc = Module.getKnownTopComponent(ed);

            //note that doing a swap on the diff window
            // can create this situation where tc is non null.
            //assert tc == null; // otherwise should have an app view by now

            // Hmm, create a nomad.
            av = NbAppView.updateAppViewForTC("NEW_TEXT_VIEW", tc, ed, true);
        }

        NbTextView tv = new NbTextView(ed);

        LineMap lm;

        lm = new LineMapFoldingSwitcher(
                new LineMapNoFolding(tv), new NbLineMapFolding(tv));
        //ViewMap vm = new SwingViewMapWrapFontFixed(tv);
        ViewMap vm = new ViewMapSwitcher(tv);
        tv.setMaps(lm, vm);
        return tv;
    }
    
    @Override
    protected Buffer createBuffer(ViTextView tv) {
        return new NbBuffer(tv);
    }
    
    @Override
    public void setupCaret(Component editor) {
        JEditorPane ep = (JEditorPane)editor;
        // Cursor is currently installed by ed kit
        // install cursor if neeeded
        if(isEnabled() && ! (ep.getCaret() instanceof ViCaret)) {
            if(G.dbgEditorActivation().getBoolean()) {
                G.dbgEditorActivation().println("setupCaret: INSTALLING ViCaret");
            }
            installCaret(ep, new NbCaret());
            Scheduler.register(editor); //NEEDSWORK: register should not public
        }
    }
    
    // NEEDSWORK: put installCaret in factory?
    public static void installCaret(JEditorPane ep, Caret newCaret) {
        Caret oldCaret = ep.getCaret(); // should never be null
        int offset = 0;
        boolean visible = true;
        if(oldCaret != null) {
            offset = oldCaret.getDot();
            visible = oldCaret.isVisible();
            if(G.dbgEditorActivation().getBoolean()) {
                G.dbgEditorActivation().printf("installCaret: was off %d, vis %b\n",
                        offset, visible);
            }
        }
        ep.setCaret(newCaret);
        if(ep.getDocument() instanceof BaseDocument) {
            // If the caret is installed too early the following gets an
            // exception trying to cast PlanDocument to BaseDocument
            newCaret.setDot(offset);
        }
        int blinkRate = Options.getOption(Options.caretBlinkRate).getInteger();
        newCaret.setBlinkRate(blinkRate);
        newCaret.setVisible(visible);
    }

    @Override
    public ViTextView getTextView(ViAppView _av) {
        NbAppView av = (NbAppView)_av;
        return getTextView(av.getEditor());
    }

    @Override
    public Action createCharAction(String name) {
        Action a;
        
        a = super.createCharAction(name);
        // Don't want jVi keys treated as options
        a.putValue(BaseAction.NO_KEYBINDING, Boolean.TRUE);
        return a;
    }

    @Override
    public Action createKeyAction(String name, char key) {
        Action a;
        
        a = super.createKeyAction(name, key);
        // Don't want jVi keys treated as options
        a.putValue(BaseAction.NO_KEYBINDING, Boolean.TRUE);
        return a;
    }

    @Override
    public ViCmdEntry createCmdEntry(ViCmdEntry.Type type) {
        ViCmdEntry ce = super.createCmdEntry(type);
        if(type == ViCmdEntry.Type.COLON) {
            JTextComponent jtc = (JTextComponent)ce.getTextComponent();

            // Set mime type to connect with code completion provider
            jtc.getDocument().putProperty("mimeType", "text/x-vicommand");

            NbUtil.EditorRegistryRegister(jtc);
        }

        return ce;
    }
    
    //
    // Tag stack maintenance
    //
    // This is all experimental, probably belongs in ViManager
    //
    private static class Tag {
        String toIdent;
        
        Document toDoc;
        Position toPosition;
        //int toLine;
        Line toLine;
        
        Document fromDoc;
        Position fromPosition;
        //int fromLine;
        Line fromLine;
        String fromFile;
    }
    
    private static Stack<Tag> tagStack = new Stack<Tag>();
    private static int iActiveTag;
    
    private static Tag pushingTag;
    private static ActionListener finishTagPush = new ActionListener()
            {
                @Override
                public void actionPerformed(ActionEvent e)
                {
                    finishTagPush(e);
                }
            };
    
    private static int calcColumnOffset(Document doc, int offset) {
        Element root = doc.getDefaultRootElement();
        int o = root.getElement(root.getElementIndex(offset)).getStartOffset();
        return offset - o;
    }
    
    private static void fillTagFrom(Tag tag, ViTextView tv) {
        tag.fromDoc = ((JEditorPane)tv.getEditor()).getDocument();
        tag.fromFile = tv.getBuffer().getDisplayFileName();
        try {
            tag.fromPosition = tag.fromDoc.createPosition(tv.getCaretPosition());
        } catch (BadLocationException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        tag.fromLine = NbEditorUtilities.getLine(
                tag.fromDoc, tag.fromPosition.getOffset(), false);
    }
    
    @Override
    public void displayTags() {
        String heading = "  # TO tag         FROM line  in file/line";
        
        ViOutputStream vios = ViManager.createOutputStream(
                null, ViOutputStream.OUTPUT, heading);
       //vios.println(heading); 
       for(int i = 0; i < tagStack.size(); i++) {
           Tag tag = tagStack.get(i);
           String fromData = tag.fromFile;
           // If the from tag is the current file, show the document's line
            ViTextView tv = G.curwin;
            if(((JEditorPane)tv.getEditor())
                    .getDocument().equals(tag.fromDoc)) {
                String s = tv.getBuffer().getLineSegment(
                                    tag.fromLine.getLineNumber() +1).toString();
                fromData = s.trim();
            }
            vios.println(String.format(
                         "%1s%2s %-18s %5d %s",
                         i == iActiveTag ? ">" : "",
                         i+1,
                         tag.toIdent,
                         tag.fromLine.getLineNumber() +1,
                         fromData
                         ));
       }
       if(iActiveTag == tagStack.size())
           vios.println(">");
       vios.close();
    }
    
    @Override
    public void tagStack(TAGOP op, int count) {
        switch(op) {
        case OLDER: // ^T
        {
            boolean doNothing = false;
            if(iActiveTag <= 0) {
                doNothing = true;
            }
            iActiveTag -= count;
            if(iActiveTag < 0) {
                Msg.emsg("at bottom of tag stack");
                iActiveTag = 0;
            }
            if(doNothing)
                break;
            
            Tag tag = tagStack.get(iActiveTag);
            tag.fromLine.show(Line.ShowOpenType.OPEN,
                              Line.ShowVisibilityType.FOCUS,
                              calcColumnOffset(tag.fromDoc,
                                               tag.fromPosition.getOffset()));
            break;
        }
            
        case NEWER: // :ta
        {
            boolean doNothing = false;
            if(iActiveTag >= tagStack.size()) {
                doNothing = true;
            }
            iActiveTag += count;
            if(iActiveTag > tagStack.size()) {
                Msg.emsg("at top of tag stack");
                iActiveTag = tagStack.size();
            }
            if(doNothing) {
                break;
            }
            
            // Modify the target tags from field
            Tag tag = tagStack.get(iActiveTag -1);
            // modify the tag entry to reflect where we're coming from
            fillTagFrom(tag, G.curwin);
            tag.toLine.show(Line.ShowOpenType.OPEN,
                            Line.ShowVisibilityType.FOCUS,
                            calcColumnOffset(tag.toDoc,
                                             tag.toPosition.getOffset()));
            break;
        }
        }
    }
    
    /**
     * Begin a tag operation, the arguments are where we started the op from.
     */
    @Override
    public final void startTagPush(ViTextView tv, String ident) {
        if(tv == null)
            return;
        if(((JEditorPane)tv.getEditor()).getDocument() == null)
            return;
        
        pushingTag = new Tag();
        pushingTag.toIdent = ident;
        
        fillTagFrom(pushingTag, tv);
        Scheduler.putKeyStrokeTodo(finishTagPush);
    }
    
    /**
     * This is called from any number of places to indicate that an ed
     * is in use, and if a tagPush is in progress, the target has been reached.
     */
    private static void finishTagPush(ActionEvent e) {
        ViTextView tv = (ViTextView)e.getSource();
        if(pushingTag == null)
            return;
        
        Document doc = ((JEditorPane)tv.getEditor()).getDocument();
        if(doc == null)
            return;
                
        pushingTag.toDoc = doc;
        try {
            pushingTag.toPosition = doc.createPosition(tv.getCaretPosition());
        } catch (BadLocationException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        
        // If at same doc and same position, then forget the tag
        if(pushingTag.fromDoc.equals(pushingTag.toDoc)
           && pushingTag.fromPosition.getOffset()
                                        == pushingTag.toPosition.getOffset()) {
            // Forget it
            pushingTag = null;
            return;
        }
        
        pushingTag.toLine = NbEditorUtilities.getLine(
                            doc, pushingTag.toPosition.getOffset(), false);
        if(pushingTag.toIdent.length() == 0) {
            // put the target file name there
            pushingTag.toIdent = "in " + tv.getBuffer().getDisplayFileName();
        }
        
        tagStack.setSize(iActiveTag);
        tagStack.push(pushingTag);
        iActiveTag++;
        
        pushingTag = null;
    }
    
    @Override
    public void tagDialog(ColonCommands.ColonEvent ce) {
        Action act = Module.fetchFileSystemAction(FsAct.GO_TYPE);
        if(act != null && act.isEnabled()) {
            ViManager.getFactory().startTagPush(ce.getViTextView(), "");
            act.actionPerformed(ce);
        } else
            Util.beep_flush();
    }

    @Override
    public void commandEntryAssist(ViCmdEntry cmdEntry, boolean enable) {
        CcCompletion.commandEntryAssist(cmdEntry, enable);
    }
}
