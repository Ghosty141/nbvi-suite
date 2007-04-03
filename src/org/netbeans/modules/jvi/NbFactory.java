package org.netbeans.modules.jvi;

import com.raelity.jvi.Buffer;
import com.raelity.jvi.G;
import com.raelity.jvi.Msg;
import com.raelity.jvi.ViFS;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViOutputStream;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.swing.CommandLine;
import com.raelity.jvi.swing.DefaultViFactory;
import com.raelity.jvi.swing.ViCaret;
import java.awt.Container;
import java.util.Collections;
import java.util.Set;
import java.util.Stack;
import java.util.prefs.Preferences;
import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.Position;
import org.netbeans.editor.BaseAction;
import org.netbeans.editor.BaseDocument;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;
import org.openide.text.Line;
import org.openide.windows.TopComponent;
import com.raelity.jvi.ViTextView.TAGOP;

final public class NbFactory extends DefaultViFactory {
    
    NbFS fs = new NbFS();
    
    NbFactory() {
        super((CommandLine)null);
    }
    
    static Set<JEditorPane> getEditorSet() {
        return Collections.unmodifiableSet(((NbFactory)INSTANCE).editorSet.keySet());
    }
    
    static Set<Document> getDocSet() {
        return Collections.unmodifiableSet(((NbFactory)INSTANCE).docSet.keySet());
    }
    
    public ViFS getFS() {
        return fs;
    }
    
    public void updateKeymap() {
        Module.updateKeymap();
    }
    
    public ViOutputStream createOutputStream(ViTextView tv,
                                             Object type, Object info) {
        return new NbOutputStream(tv, type.toString(),
                                  info == null ? null : info.toString());
    }
    
    public Preferences getPreferences() {
        // NB6 return NbPreferences.forModule(ViManager.class); Not in 5.5
        Preferences retValue;
        
        retValue = super.getPreferences();
        return retValue;
    }
    
    protected ViTextView createViTextView(JEditorPane editorPane) {
        // Set up some linkage so we can clean up the editorpane
        // when the TopComponent closes.
        // NEEDSWORK: move this to base class or ViManager.activateFile
        TopComponent tc = getEditorTopComponent(editorPane);
        if(tc != null) {
            Object ep = tc.getClientProperty(Module.PROP_JEP);
            assert(ep != null && ep == editorPane);
            tc.putClientProperty(Module.PROP_JEP, editorPane);
        } else
            ViManager.log("createViTextView: not isBuffer");
        
        return new NbTextView(editorPane);
    }
    
    protected Buffer createBuffer(JEditorPane editorPane) {
        return new NbBuffer();
    }
    
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
    
    public String getDisplayFilename(Object o) {
        if(o instanceof TopComponent)
            return ((TopComponent)o).getDisplayName();
        return "";
    }
    
    /** Find a TopComponent that has been activated as an editor */
    public static TopComponent getEditorTopComponent(JEditorPane editorPane) {
        TopComponent tc = null;
        Container parent = SwingUtilities
                .getAncestorOfClass(TopComponent.class, editorPane);
        while (parent != null) {
            tc = (TopComponent)parent;
            if(ViManager.isBuffer(tc))
                break;
            parent = SwingUtilities.getAncestorOfClass(TopComponent.class, tc);
        }
        return tc;
    }

    public Action createCharAction(String name) {
        Action a;
        
        a = super.createCharAction(name);
        // Don't want jVi keys treated as options
        a.putValue(BaseAction.NO_KEYBINDING, Boolean.TRUE);
        return a;
    }

    public Action createKeyAction(String name, int key) {
        Action a;
        
        a = super.createKeyAction(name, key);
        // Don't want jVi keys treated as options
        a.putValue(BaseAction.NO_KEYBINDING, Boolean.TRUE);
        return a;
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
        tag.fromFile = tv.getDisplayFileName();
        try {
            tag.fromPosition
                = tag.fromDoc.createPosition(
                            tv.getEditorComponent().getCaretPosition());
        } catch (BadLocationException ex) {
            ex.printStackTrace();
        }
        tag.fromLine = NbEditorUtilities.getLine(
                tag.fromDoc, tag.fromPosition.getOffset(), false);
    }
    
    @Override
    public void displayTags() {
        String heading = "   # TO tag         FROM line  in file/line";
        
        ViOutputStream vios = ViManager.createOutputStream(
                null, ViOutputStream.OUTPUT, heading);
       //vios.println(heading); 
       for(int i = 0; i < tagStack.size(); i++) {
           Tag tag = tagStack.get(i);
           String fromData = tag.fromFile;
           // If the from tag is the current file, show the document's line
            ViTextView tv = G.curwin;
            if(tv.getEditorComponent().getDocument().equals(tag.fromDoc)) {
                String s = tv.getLineSegment(
                                    tag.fromLine.getLineNumber() +1).toString();
                fromData = s.trim();
            }
            vios.println(String.format(
                         "%1s%2s %-17s %5d %s",
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
            tag.fromLine.show(Line.SHOW_TOFRONT,
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
            tag.toLine.show(Line.SHOW_TOFRONT,
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
        
        System.err.println("##### startTagPush:"
                + " name = " + tv.getDisplayFileName()
                + " line = " + tv.getWCursor().getLine()
                + " col = " + tv.getWCursor().getColumn());
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
            ex.printStackTrace();
        }
        
        // If at same doc and same position, then forget the tag
        if(pushingTag.fromDoc.equals(pushingTag.toDoc)
           && pushingTag.fromPosition.getOffset()
                                        == pushingTag.toPosition.getOffset()) {
            // Forget it
            pushingTag = null;
            System.err.println("avoid duplicate entries");
            return;
        }
        
        pushingTag.toLine = NbEditorUtilities.getLine(
                            doc, pushingTag.toPosition.getOffset(), false);
        if(pushingTag.toIdent.length() == 0) {
            // put the target file name there
            pushingTag.toIdent = "in " + tv.getDisplayFileName();
        }
        
        System.err.println("##### finishTagPush:"
                + " name = " + tv.getDisplayFileName()
                + " line = " + tv.getWCursor().getLine()
                + " col = " + tv.getWCursor().getColumn());
        
        tagStack.setSize(iActiveTag);
        tagStack.push(pushingTag);
        iActiveTag++;
        
        pushingTag = null;
    }
}
