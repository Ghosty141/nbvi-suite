/*
 * NbBuffer.java
 *
 * Created on March 6, 2007, 11:17 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.netbeans.modules.jvi;

import com.raelity.jvi.Edit;
import com.raelity.jvi.G;
import com.raelity.jvi.Misc;
import com.raelity.jvi.Util;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.swing.DefaultBuffer;
import com.raelity.text.TextUtil;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.undo.UndoableEdit;
import org.netbeans.modules.editor.indent.api.Indent;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.SimpleValueNames;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.BaseDocumentEvent;
import org.netbeans.editor.GuardedDocument;
import org.netbeans.editor.GuardedException;
import org.netbeans.modules.editor.NbEditorKit;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.awt.UndoRedo;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

import static com.raelity.jvi.Constants.*;


/**
 *
 * @author erra
 */
public class NbBuffer extends DefaultBuffer {
    private static Logger LOG = Logger.getLogger(NbBuffer.class.getName());
    private UndoRedo.Manager undoRedo;

    private static Method beginUndo;
    private static Method endUndo;
    private static Method commitUndo;

//    private CompoundEdit compoundEdit;
//    private static Method undoRedoFireChangeMethod;
    
    /** Creates a new instance of NbBuffer */
    public NbBuffer(ViTextView tv) {
        super(tv);
        
        UndoableEditListener l[] = ((AbstractDocument)getDoc())
                                                .getUndoableEditListeners();
        for (int i = 0; i < l.length; i++) {
            if(l[i] instanceof UndoRedo.Manager) {
                undoRedo = (UndoRedo.Manager) l[i];
                break;
            }
        }

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

    @Override
    public void removeShare() {
        if(getShare() == 1)
            stopDocumentEvents();
        super.removeShare();
    }
    
    @Override
    public void activateOptions(ViTextView tv) {
        super.activateOptions(tv);
        // String mimeType = tv.getEditorComponent().getContentType();
        String mimeType
                = NbEditorUtilities.getMimeType(tv.getEditorComponent());
        Preferences prefs = MimeLookup.getLookup(
                MimePath.parse(mimeType)).lookup(Preferences.class);

        JViOptionWarning.setInternalAction(true);
        try {
            // NEEDSWORK: only do the "put" if something changed
            prefs.putBoolean(SimpleValueNames.EXPAND_TABS, b_p_et);

            prefs.putInt(SimpleValueNames.INDENT_SHIFT_WIDTH, b_p_sw);

            prefs.putInt(SimpleValueNames.SPACES_PER_TAB, b_p_ts);
            prefs.putInt(SimpleValueNames.TAB_SIZE, b_p_ts);
        } finally {
            JViOptionWarning.setInternalAction(false);
        }

    }

    @Override
    public void viOptionSet(ViTextView tv, String name) {
        super.viOptionSet(tv, name);
        // String mimeType = tv.getEditorComponent().getContentType();
        String mimeType
                = NbEditorUtilities.getMimeType(tv.getEditorComponent());
        Preferences prefs = MimeLookup.getLookup(
                MimePath.parse(mimeType)).lookup(Preferences.class);

        JViOptionWarning.setInternalAction(true);
        try {

            if("b_p_ts".equals(name)) {
                prefs.putInt(SimpleValueNames.TAB_SIZE, b_p_ts);
                prefs.putInt(SimpleValueNames.SPACES_PER_TAB, b_p_ts);
            } else if("b_p_sw".equals(name)) {
                prefs.putInt(SimpleValueNames.INDENT_SHIFT_WIDTH, b_p_sw);
            } else if("b_p_et".equals(name)) {
                prefs.putBoolean(SimpleValueNames.EXPAND_TABS, b_p_et);
            }
        } finally {
            JViOptionWarning.setInternalAction(false);
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    //

    /**
     * This method provides a file only so that the path can be examined.
     * @return null or the path for this file
     */
    @Override
    public File getFile() {
        // NEEDSWORK: see NbFactory.isNomadic
        File fi = null;
        Document doc = getDocument();
        if(doc != null) {
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            if(fo != null){
                fi = FileUtil.toFile(fo);
            }
        }
        return fi;
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Document commands
    //

    @Override
    public void reindent(int line, int count) {
        if(getDoc() instanceof BaseDocument) {
            BaseDocument doc = (BaseDocument)getDoc();
            boolean keepAtomicLock = doc.isAtomicLock();
            if(keepAtomicLock)
                doc.atomicUnlock();
            Indent indent = Indent.get(doc);
            indent.lock();
            try {
                doc.atomicLock();
                try {
                    indent.reindent(getLineStartOffset(line),
                                    getLineEndOffset(line + count - 1));
                } catch (BadLocationException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                } finally {
                    if(!keepAtomicLock)
                        doc.atomicUnlock();
                }
            } finally {
                indent.unlock();
            }
        } else {
            Util.vim_beep();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Things that go bump in the document
    //

    @Override
    public boolean isGuarded(int offset) {
        boolean isGuarded = false;
        if(getDoc() instanceof GuardedDocument) {
            return ((GuardedDocument)getDoc()).isPosGuarded(offset);
        }
        return isGuarded;
    }

    @Override
    protected String getRemovedText(DocumentEvent e) {
        String s = null;
        if(e instanceof BaseDocumentEvent) {
            s = ((BaseDocumentEvent)e).getText();
        }
        return s;
    }
    
    private boolean fGuardedException;
    private boolean fException;

    boolean isGuardedException() {
        return fGuardedException;
    }

    boolean isExecption() {
        return fException;
    }

    boolean isAnyExecption() {
        return fException || fGuardedException;
    }

    void clearExceptions() {
        fGuardedException = false;
        fException = false;
    }
    
    @Override
    protected void processTextException(BadLocationException ex) {
        if(ex instanceof GuardedException) {
            fGuardedException = true;
        } else {
            fException = true;
        }
        Util.vim_beep();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Undo handling.
    //

    @Override
    protected void redoOperation() {
        //undoOrRedo("Redo", FsAct.REDO);
        undoOrRedo("Redo", NbEditorKit.redoAction);
    }
    
    @Override
    protected void undoOperation() {
        //undoOrRedo("Undo", FsAct.UNDO);
        undoOrRedo("Undo", NbEditorKit.undoAction);
    }
    
    private void undoOrRedo(String tag, String action) {
        NbTextView tv = (NbTextView)ViManager.getCurrentTextView();
        if(tv == null || !tv.isEditable()) {
            Util.vim_beep();
            return;
        }
        if(Misc.isInAnyUndo()) {
            ViManager.dumpStack(tag + " while in begin/endUndo");
            return;
        }
        // NEEDSWORK: check can undo for beep
        
        isUndoChange(); // clears the flag
        int n = getLineCount();
        tv.getOps().xact(action);
        ///// ActionEvent e = new ActionEvent(tv.getEditorComponent(),
        /////                                 ActionEvent.ACTION_PERFORMED,
        /////                                 "");
        ///// Module.execFileSystemAction(action, e);
        
        if(isUndoChange()) {
            // NEEDSWORK: check if need newline adjust
            tv.setCaretPosition(getUndoOffset());
            try {
                if(n != getLineCount())
                    Edit.beginline(BL_WHITE);
                else if ("\n".equals(getText(tv.getCaretPosition(), 1))) {
                    Misc.check_cursor_col();
                }
            } catch (BadLocationException ex) { }
        } else
            Util.vim_beep();
        // ops.xact(SystemAction.get(UndoAction.class)); // in openide
    }

    //
    // With NB, the atomic lock on the document groups undo
    // so we can always do that for progromatic undo/redo, eg. "3dd".
    // But for insert mode can not lock file (user interactions), so we
    // continue to use the classic undo flag
    //

    @Override
    protected void do_runUndoable(final Runnable r) {
        final Document doc = getDoc();
        if(doc instanceof BaseDocument) {
            r.run();

            // maybe someday
            // ((BaseDocument)doc).runAtomicAsUser(new Runnable() {
            //     public void run() {
            //         try {
            //             r.run();
            //         } finally {
            //             if(isAnyExecption()) {
            //                 ((BaseDocument)doc).breakAtomicLock();
            //             }
            //         }
            //     }
            // });
        } else {
            r.run();
        }
    }
    
    @Override
    public void do_beginUndo() {
        clearExceptions();
        Document doc = getDoc();
        if(doc instanceof BaseDocument) {
            ((BaseDocument)doc).atomicLock();
        }
    }
    
    @Override
    public void do_endUndo() {
        Document doc = getDoc();
        if(doc instanceof BaseDocument) {
            if(isAnyExecption()) {
                // get rid of the changes
                ((BaseDocument)doc).breakAtomicLock();
            }
            
            ((BaseDocument)doc).atomicUnlock();

            if(isAnyExecption()) {
                final ViTextView tv = ViManager.getCurrentTextView();
                // This must come *after* atomicUnlock, otherwise it gets
                // overwritten by a clear message due to scrolling.
                
                // Don't want this message lost, so defer until things
                // settle down. The change/unlock-undo cause a lot of
                // action
                
                SwingUtilities.invokeLater(new Runnable() {
                    public void run() {
                        tv.getStatusDisplay().displayErrorMessage(
                                "No changes made."
                                + (isGuardedException()
                                   ? " Attempt to change guarded text."
                                   : " Document location error."));
                    }
                });
            }
        }
    }

    @Override
    public void do_beginInsertUndo() {
        // NEDSWORK: when development on NB6, and method in NB6, use boolean
        //           for method is available and ifso invoke directly.
        if(G.isClassicUndo.getBoolean()) {
            if(beginUndo != null && undoRedo != null) {
                try {
                    beginUndo.invoke(undoRedo);
                } catch (InvocationTargetException ex) {
                } catch (IllegalAccessException ex) { }
            }
        }
    }

    @Override
    public void do_endInsertUndo() {
        if(G.isClassicUndo.getBoolean()) {
            if(endUndo != null && undoRedo != null) {
                try {
                    endUndo.invoke(undoRedo);
                } catch (InvocationTargetException ex) {
                } catch (IllegalAccessException ex) { }
            }
        }
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
