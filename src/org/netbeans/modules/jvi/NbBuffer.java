/*
 * NbBuffer.java
 *
 * Created on March 6, 2007, 11:17 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.netbeans.modules.jvi;

import com.raelity.jvi.Buffer;
import com.raelity.jvi.G;
import com.raelity.jvi.ViTextView;
import com.raelity.text.TextUtil;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.Document;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoableEdit;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.BaseDocumentEvent;
import org.netbeans.editor.Settings;
import org.netbeans.editor.SettingsNames;
import org.netbeans.modules.editor.FormatterIndentEngine;
import org.netbeans.modules.editor.options.BaseOptions;
import org.openide.text.IndentEngine;
import org.openide.util.Lookup;
import org.openide.awt.UndoRedo;


/**
 *
 * @author erra
 */
public class NbBuffer extends Buffer {
    private UndoRedo.Manager undoRedo;

    private static Method beginUndo;
    private static Method endUndo;
    private static Method commitUndo;

//    private CompoundEdit compoundEdit;
//    private static Method undoRedoFireChangeMethod;
    
    /** Creates a new instance of NbBuffer */
    public NbBuffer(Document doc) {
        super(doc);
        //correlateDocumentEvents();
        
        UndoableEditListener l[] = ((AbstractDocument)doc).getUndoableEditListeners();
        for (int i = 0; i < l.length; i++) {
            if(l[i] instanceof UndoRedo.Manager) {
                undoRedo = (UndoRedo.Manager) l[i];
                break;
            }
        }

//        if(undoRedoFireChangeMethod == null) {
//            try {
//                undoRedoFireChangeMethod = UndoRedo.Manager.class.getMethod(
//                                            "fireChange", (Class<?>[])null);
//            } catch (NoSuchMethodException ex) { }
//        }
        if(beginUndo == null) {
            try {
                beginUndo = UndoRedo.Manager.class.getMethod("beginUndoGroup",
                                                             (Class<?>[])null);
                endUndo = UndoRedo.Manager.class.getMethod("endUndoGroup",
                                                           (Class<?>[])null);
                commitUndo = UndoRedo.Manager.class.getMethod("commitUndoGroup",
                                                              (Class<?>[])null);
            } catch (NoSuchMethodException ex) { }
            if(commitUndo == null || endUndo == null || commitUndo == null) {
                beginUndo = null;
                endUndo = null;
                commitUndo = null;
            }
        }
    }

    public void removeShare() {
        if(getShare() == 1)
            stopDocumentEvents();
        super.removeShare();
    }

    public void viOptionSet(ViTextView tv, String name) {
        Class kitC = tv.getEditorComponent().getEditorKit().getClass();
        String content = tv.getEditorComponent().getContentType();
        
        if("b_p_ts".equals(name)) {
            Settings.setValue(kitC, SettingsNames.TAB_SIZE, b_p_ts);
            Settings.setValue(kitC, SettingsNames.SPACES_PER_TAB, b_p_ts);
        } else if("b_p_sw".equals(name)) {
            //IndentEngine ie = IndentEngine.find(content);
            FormatterIndentEngine ie = fetchIndentEngine(tv);
            if(ie != null)
                ie.setSpacesPerTab(b_p_sw); // space per tab ??????
        } else if("b_p_et".equals(name)) {
            FormatterIndentEngine ie = fetchIndentEngine(tv);
            if(ie != null)
                ie.setExpandTabs(b_p_et);
        }
    }
    
    public void activateOptions(ViTextView tv) {
        // put them all out there.
        Class kitC = tv.getEditorComponent().getEditorKit().getClass();
        String content = tv.getEditorComponent().getContentType();
        
        Settings.setValue(kitC, SettingsNames.TAB_SIZE, b_p_ts);
        Settings.setValue(kitC, SettingsNames.SPACES_PER_TAB, b_p_ts);
        FormatterIndentEngine ie = fetchIndentEngine(tv);
        if(ie != null) {
            ie.setSpacesPerTab(b_p_sw); // space per tab ??????
            ie.setExpandTabs(b_p_et);
        }
    }

    // In NB all the action for non-insert undo is happening in NbTextView
    // Need this here, to insure base class doesn't get involved
    public void beginUndo() {
    }

    public void endUndo() {
    }

//    private class CoalesceEdit extends CompoundEdit {}
    public void beginInsertUndo() {
        // NEDSWORK: when development on NB6, and method in NB6, use boolean
        //           for method is available and ifso invoke directly.
        if(G.isClassicUndo.getBoolean()) {
//            if(undoRedo != null) {
//                // see issue 103467 for why adding two edits.
//                compoundEdit = new CompoundEdit();
//                compoundEdit.end();
//                undoRedo.undoableEditHappened(
//                            new UndoableEditEvent(getDoc(), compoundEdit));
//                compoundEdit = new CoalesceEdit();
//                undoRedo.undoableEditHappened(
//                            new UndoableEditEvent(getDoc(), compoundEdit));
//            }
            if(beginUndo != null && undoRedo != null) {
                try {
                    beginUndo.invoke(undoRedo);
                } catch (InvocationTargetException ex) {
                } catch (IllegalAccessException ex) { }
            }
        }
    }

    public void endInsertUndo() {
        if(G.isClassicUndo.getBoolean()) {
//            compoundEdit.end();
//            compoundEdit = null;
//            undoRedo.fireChange();
            if(endUndo != null && undoRedo != null) {
                try {
                    endUndo.invoke(undoRedo);
                } catch (InvocationTargetException ex) {
                } catch (IllegalAccessException ex) { }
            }
        }
    }

    
    private FormatterIndentEngine fetchIndentEngine(ViTextView tv) {
        FormatterIndentEngine fie = null;
        IndentEngine ie = IndentEngine.find(
                                    tv.getEditorComponent().getDocument());
        if(ie instanceof FormatterIndentEngine)
            fie = (FormatterIndentEngine) ie;
        return fie;
    }
    
    
    //////////////////////////////////////////////////////////////////////
    //
    // Following NOT USED but keep the code around for an example BaseOptions
    //
    
    private FormatterIndentEngine XXXfetchIndentEngine(ViTextView tv) {
        FormatterIndentEngine ie = null;
        BaseOptions bo = XXXfetchBaseOptions(tv);
        if(bo != null
            && bo.getIndentEngine() instanceof FormatterIndentEngine) {
            ie = (FormatterIndentEngine) bo.getIndentEngine();
        }
        return ie;
    }
    
    private BaseOptions XXXfetchBaseOptions(ViTextView tv) {
        String content = tv.getEditorComponent().getContentType();
        BaseOptions bo = null;
        
        Lookup.Result result = MimeLookup.getMimeLookup(content)
                            .lookup(new Lookup.Template(BaseOptions.class));
        
        // Its really a list (see the docs)
        // and directly access the rest of the type casts.
        
        if(result != null) {
            List instances = (List)result.allInstances();
            if(instances != null && instances.size() > 0)
                bo = (BaseOptions)instances.get(0);
        }
        return bo;
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Following stuff is for play
    //

    private DocumentListener documentListener;
    private UndoableEditListener undoableEditListener;

    private void stopDocumentEvents() {
        getDoc().removeDocumentListener(documentListener);
        getDoc().removeUndoableEditListener(undoableEditListener);
    }
    
    private void correlateDocumentEvents() {
        Document doc = getDoc();
        documentListener = new DocumentListener() {
            public void changedUpdate(DocumentEvent e) {
                //dumpDocEvent("change", e);
            }
            public void insertUpdate(DocumentEvent e) {
                dumpDocEvent("insert", e);
            }
            public void removeUpdate(DocumentEvent e) {
                dumpDocEvent("remove", e);
            }
        };
        doc.addDocumentListener(documentListener);
        undoableEditListener = new UndoableEditListener() {
            public void undoableEditHappened(UndoableEditEvent e) {
                UndoableEdit ue = e.getEdit();
                System.err.println(ue.getClass().getSimpleName()
                                   + " sig: " + ue.isSignificant());
                //System.err.println("UndoableEditEvent = " + e );
            }
        };
        //doc.addUndoableEditListener(undoableEditListener);
    }
    
    private void dumpDocEvent(String tag, DocumentEvent e_) {
        if("change".equals(tag)) {
            System.err.println(tag + ": " + e_);
            return;
        }
        if(e_ instanceof BaseDocumentEvent) {
            BaseDocumentEvent e = (BaseDocumentEvent) e_;
            System.err.println(tag + ": " + e.getType().toString() + ": "
                               + e.getOffset() + ":" + e.getLength() + " "
                               + "'" + TextUtil.debugString(e.getText()) + "'");
        }
    }
}
