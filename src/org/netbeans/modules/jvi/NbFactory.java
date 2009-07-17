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
 * Copyright (C) 2000 Ernie Rael.  All Rights Reserved.
 *
 * Contributor(s): Ernie Rael <err@raelity.com>
 */
package org.netbeans.modules.jvi;

import com.raelity.jvi.*;
import com.raelity.jvi.swing.*;
import com.raelity.jvi.ViTextView.TAGOP;

import java.awt.Container;
import java.util.Collections;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.JTextComponent;
import javax.swing.text.Position;

import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;
import org.openide.text.Line;
import org.openide.util.Lookup;
import org.openide.util.NbPreferences;
import org.openide.windows.TopComponent;

final public class NbFactory extends DefaultViFactory {

    private static Logger LOG = Logger.getLogger(NbFactory.class.getName());
    
    NbFS fs = new NbFS();
    
    NbFactory() {
        super();
    }
    
    static Set<JEditorPane> getEditorSet() {
        return Collections.unmodifiableSet(
                ((NbFactory)INSTANCE).editorSet.keySet());
    }
    
    static Set<Document> getDocSet() {
        return Collections.unmodifiableSet(
                ((NbFactory)INSTANCE).docSet.keySet());
    }

    @Override
    public Class loadClass(String name) throws ClassNotFoundException {
        return ((ClassLoader)(Lookup.getDefault().lookup(ClassLoader.class)))
            .loadClass(name);
    }

    @Override
    public boolean isStandalone() {
        return false;
    }
    
    @Override
    public ViFS getFS() {
        return fs;
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
        if(prefs == null) {
            Preferences jdkPrefs
                        = Preferences.userRoot().node(ViManager.PREFS_ROOT);
            // ALWAYS USE PLATFORM PREFS so import/export options works
            //if(!jdkPrefs.getBoolean(Options.platformPreferences, false))
            if(false) {
                // Use the jdk's Preferences implementation
                prefs = jdkPrefs;
            } else {
                // May use NetBeans platform Preferences implementation
                // unless it is set to false
                Preferences platformPrefs
                        = NbPreferences.forModule(Module.class);
                try {
                    platformPrefs.nodeExists("");
                    // Options.platformPreferences is a boolean,
                    // if it is empty, then it has never been initialized
                    // and we should copy over the options from the jdk
                    // and start using the platform preferences
                    if(platformPrefs.get(Options.platformPreferences, "")
                            .equals("")) {
                        //System.err.println("COPY JVI PREFERENCES");
                        ViManager.copyPreferences(
                                    platformPrefs, jdkPrefs, false);
                        platformPrefs.putBoolean(
                            Options.platformPreferences, true);
                    }

                    // ALWAYS USE PLATFORM PREFS
                    platformPrefs.putBoolean(Options.platformPreferences, true);

                    // Default doesn't matter in following, since this
                    // must have a value.
                    boolean usePlatform = platformPrefs.getBoolean(
                            Options.platformPreferences, false);

                    if(usePlatform) {
                        prefs = platformPrefs;
                        //System.err.println("USE PLATFORM PREFERENCES NODE");
                    } else {
                        // switching back to the jdk preferences
                        jdkPrefs.putBoolean(Options.platformPreferences, false);
                        // leave the platformPrefs side of the value as true,
                        // so things are ready if/when jdk side is set to true
                        platformPrefs.putBoolean(
                                Options.platformPreferences, true);
                        prefs = jdkPrefs;
                        //System.err.println("USE JDK PREFERENCES NODE");
                    }
                } catch(BackingStoreException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }

            // If for whatever reason, there aren't preferences at this
            // point, try the superclass
            if(prefs == null)
                prefs = super.getPreferences();
        }
        return prefs;
    }
    
    @Override
    protected ViTextView createViTextView(JEditorPane editorPane) {
        // Set up some linkage so we can clean up the editorpane
        // when the TopComponent closes.
        // NEEDSWORK: move this to base class or ViManager.activateFile
        TopComponent tc = getEditorTopComponent(editorPane);
        if(tc != null) {
            JEditorPane ep = Module.fetchEpFromTC(tc, editorPane);
            if(ep == null) {
                Module.activateTC(editorPane, tc, "CREATE-TV");
                ep = Module.fetchEpFromTC(tc, editorPane);
            }
            assert(ep != null && ep == editorPane);
        } else
            ViManager.log("createViTextView: not isBuffer");
        
        ViTextView tv = new NbTextView(editorPane);
        return tv;
    }
    
    @Override
    protected Buffer createBuffer(ViTextView tv) {
        return new NbBuffer(tv);
    }
    
    @Override
    public void registerEditorPane(JEditorPane ep) {
        // Cursor is currently installed by editor kit
        // install cursor if neeeded
        if( ! (ep.getCaret() instanceof ViCaret)) {
            installCaret(ep, new NbCaret());
        }
    }
    
    // NEEDSWORK: put installCaret in factory?
    public static void installCaret(JEditorPane ep, Caret newCaret) {
        Caret oldCaret = ep.getCaret(); // should never be null
        int offset = 0;
        int blinkRate = 400;
        if(oldCaret != null) {
            offset = oldCaret.getDot();
            blinkRate = oldCaret.getBlinkRate();
        }
        ep.setCaret(newCaret);
        if(ep.getDocument() instanceof BaseDocument) {
            // If the caret is installed too early the following gets an
            // exception trying to case PlanDocument to BaseDocument
            newCaret.setDot(offset);
        }
        newCaret.setBlinkRate(blinkRate);
    }

    @Override
    public boolean isNomadic(JEditorPane ep, Object appHandle) {
        boolean isNomadic;
        Document doc = ep.getDocument();
        if(doc == null || NbEditorUtilities.getFileObject(doc) == null)
            isNomadic = true;
        else
            isNomadic = false;
        return isNomadic;
    }
  
    @Override
    public boolean isVisible(ViTextView tv) {
        TopComponent tc = getEditorTopComponent(tv.getEditorComponent());
        // wonder if this really works
        return tc != null ? tc.isVisible() : false;
    }
    
    @Override
    public String getDisplayFilename(Object appHandle) {
        if(appHandle instanceof TopComponent)
            return ((TopComponent)appHandle).getDisplayName();
        if(appHandle instanceof Document) {
            Document doc = (Document) appHandle;
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            return fo != null ? fo.getNameExt() : "null-FileObject";
        }
        if(appHandle == null)
            return "null-appHandle";
        return "";
    }

    /** Find a TopComponent that has been activated as an editor */
    public static TopComponent getEditorTopComponent(JEditorPane editorPane) {
        TopComponent tc = null;
        Container parent = SwingUtilities
                .getAncestorOfClass(TopComponent.class, editorPane);
        while (parent != null) {
            tc = (TopComponent)parent;
            if(ViManager.isKnownAppHandle(tc))
                break;
            parent = SwingUtilities.getAncestorOfClass(TopComponent.class, tc);
        }
        return tc;
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
    public ViCmdEntry createCmdEntry(int type) {
        ViCmdEntry ce = super.createCmdEntry(type);
        if(type == ViCmdEntry.COLON_ENTRY && Module.isNb6()) {
            JTextComponent jtc = ce.getTextComponent();

            // Set mime type to connect with code completion provider
            jtc.getDocument().putProperty("mimeType", "text/x-vicommand");

            Module.EditorRegistryRegister(jtc);
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
    
    Stack<Tag> tagStack = new Stack<Tag>(); // Stack allows big shrink
    int iActiveTag;
    
    static Tag pushingTag;
    
    private static int calcColumnOffset(Document doc, int offset) {
        Element root = doc.getDefaultRootElement();
        int o = root.getElement(root.getElementIndex(offset)).getStartOffset();
        return offset - o;
    }
    
    private static void fillTagFrom(Tag tag, ViTextView tv) {
        tag.fromDoc = tv.getEditorComponent().getDocument();
        tag.fromFile = tv.getBuffer().getDisplayFileName();
        try {
            tag.fromPosition
                = tag.fromDoc.createPosition(
                            tv.getEditorComponent().getCaretPosition());
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
            if(tv.getEditorComponent().getDocument().equals(tag.fromDoc)) {
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
                              Line.ShowVisibilityType.FRONT,
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
                            Line.ShowVisibilityType.FRONT,
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
        if(tv.getEditorComponent().getDocument() == null)
            return;
        
        pushingTag = new Tag();
        pushingTag.toIdent = ident;
        
        fillTagFrom(pushingTag, tv);
    }
    
    /**
     * This is called from any number of places to indicate that an editor
     * is in use, and if a tagPush is in progress, the target has been reached.
     */
    @Override
    public final void finishTagPush(ViTextView tv) {
        if(tv == null)
            return;
        if(pushingTag == null)
            return;
        
        Document doc = tv.getEditorComponent().getDocument();
        if(doc == null)
            return;
                
        pushingTag.toDoc = doc;
        try {
            pushingTag.toPosition
                   = doc.createPosition(tv.getEditorComponent().getCaretPosition());
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
            ViManager.getViFactory().startTagPush(ce.getViTextView(), "");
            act.actionPerformed(ce);
        } else
            Util.vim_beep();
    }

    @Override
    public void commandEntryAssist(ViCmdEntry cmdEntry, boolean enable) {
        if(Module.isNb6())
            Module.commandEntryAssist(cmdEntry, enable);
    }
}
