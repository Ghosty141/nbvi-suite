/*
 * NbBuffer.java
 *
 * Created on March 6, 2007, 11:17 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.netbeans.modules.jvi.impl;

import org.openide.util.WeakListeners;
import com.raelity.jvi.core.Options;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import com.raelity.jvi.ViBadLocationException;
import org.netbeans.modules.editor.indent.spi.CodeStylePreferences;
import com.raelity.jvi.core.Edit;
import com.raelity.jvi.core.G;
import com.raelity.jvi.core.Misc;
import com.raelity.jvi.core.Util;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.manager.Scheduler;
import com.raelity.jvi.swing.SwingBuffer;
import com.raelity.text.TextUtil;
import java.io.File;
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
import javax.swing.text.JTextComponent;
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
import org.netbeans.modules.editor.indent.api.Reformat;
import org.netbeans.modules.jvi.JViOptionWarning;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.text.CloneableEditorSupport;

import static com.raelity.jvi.core.lib.Constants.*;


/**
 *
 * @author erra
 */
public class NbBuffer extends SwingBuffer {
    private static final Logger LOG = Logger.getLogger(NbBuffer.class.getName());
    
    /** Creates a new instance of NbBuffer */
    public NbBuffer(ViTextView tv) {
        super(tv);

        // NB associates blink rate with mime type.
        // Without tracking all the mimeTypes,
        // set the blink rate with the buffer; this
        // is too often, but it almost never changes.
        Preferences prefs = ViManager.getFactory().getPreferences();
        prefs.addPreferenceChangeListener(WeakListeners.create(
                PreferenceChangeListener.class, prefsListener, prefs));
    }

    @Override
    public void removeShare() {
        if(getShare() == 1)
            stopDocumentEvents();
        super.removeShare();
    }

    private final PreferenceChangeListener prefsListener
            = new PreferenceChangeListener() {
        @Override
        public void preferenceChange(PreferenceChangeEvent evt)
        {
            if(evt.getKey().equals(Options.caretBlinkRate)) {
                setBlinkRate();
            }
        }
    };

    @Override
    public void activateOptions(ViTextView tv) {
        super.activateOptions(tv);
        Preferences codePrefs = CodeStylePreferences.get(
                getDocument()).getPreferences();

        JViOptionWarning.setInternalAction(true);
        try {
            // NEEDSWORK: only do the "put" if something changed
            codePrefs.putBoolean(SimpleValueNames.EXPAND_TABS, b_p_et);

            codePrefs.putInt(SimpleValueNames.INDENT_SHIFT_WIDTH, b_p_sw);

            codePrefs.putInt(SimpleValueNames.SPACES_PER_TAB, b_p_ts);
            codePrefs.putInt(SimpleValueNames.TAB_SIZE, b_p_ts);
        } finally {
            JViOptionWarning.setInternalAction(false);
        }

    }

    /**
     * Get the Preferences node for the mime type associated with tv.
     * If tv is null, then use mime type of this NbBuffer's document.
     * @param tv
     * @return 
     */
    public Preferences getMimePrefs(ViTextView tv)
    {
        Preferences prefs = null;
        String mimeType = tv == null
                ? NbEditorUtilities.getMimeType(getDocument())
                : NbEditorUtilities.getMimeType(
                    ((JTextComponent)tv.getEditor()));
        if(mimeType != null) {
            prefs = MimeLookup.getLookup(
                    MimePath.parse(mimeType)).lookup(Preferences.class);
        }
        return prefs;
    }

    @Override
    public void viOptionSet(ViTextView tv, String name) {
        super.viOptionSet(tv, name);
        Preferences mimePrefs = getMimePrefs(tv);
        if(mimePrefs == null)
            return;

        JViOptionWarning.setInternalAction(true);
        try {

            if("b_p_ts".equals(name)) {
                mimePrefs.putInt(SimpleValueNames.TAB_SIZE, b_p_ts);
                mimePrefs.putInt(SimpleValueNames.SPACES_PER_TAB, b_p_ts);
            } else if("b_p_sw".equals(name)) {
                mimePrefs.putInt(SimpleValueNames.INDENT_SHIFT_WIDTH, b_p_sw);
            } else if("b_p_et".equals(name)) {
                mimePrefs.putBoolean(SimpleValueNames.EXPAND_TABS, b_p_et);
            }
        } finally {
            JViOptionWarning.setInternalAction(false);
        }
    }

    // set the blink rate for the mime type of this buffer's Document
    private void setBlinkRate() {
        Preferences mimePrefs = getMimePrefs(null);
        if(mimePrefs != null) {
            int n = Options.getOption(Options.caretBlinkRate).getInteger();
            // when there are multiple editors of the same mime type
            // (the usual case), only need to do the putInt once for
            // the mime type. (note the putInt triggers a NB listener)
            if(mimePrefs.getInt(SimpleValueNames.CARET_BLINK_RATE, -1) != n)
                mimePrefs.putInt(SimpleValueNames.CARET_BLINK_RATE, n);
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
        if(getDocument() instanceof BaseDocument) {
            BaseDocument doc = (BaseDocument)getDocument();
            boolean keepAtomicLock = doc.isAtomicLock();
            if(keepAtomicLock)
                doc.atomicUnlock();
            Indent indent = Indent.get(doc);
            indent.lock();
            try {
                doc.atomicLock();
                try {
                    indent.reindent(getLineStartOffset(line),
                                    getLineEndOffset2(line + count - 1));
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
            Util.beep_flush();
        }
    }

    @Override
    public void reformat(int line, int count) {
        if(getDocument() instanceof BaseDocument) {
            BaseDocument doc = (BaseDocument)getDocument();
            boolean keepAtomicLock = doc.isAtomicLock();
            if(keepAtomicLock)
                doc.atomicUnlock();
            Reformat reformat = Reformat.get(doc);
            reformat.lock();
            try {
                doc.atomicLock();
                try {
                    reformat.reformat(getLineStartOffset(line),
                                      getLineEndOffset2(line + count - 1));
                } catch (BadLocationException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                } finally {
                    if(!keepAtomicLock)
                        doc.atomicUnlock();
                }
            } finally {
                reformat.unlock();
            }
        } else {
            Util.beep_flush();
        }
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Things that go bump in the document
    //

    @Override
    public boolean isGuarded(int offset)
    {
        boolean isGuarded = false;
        if(getDocument() instanceof GuardedDocument) {
            return ((GuardedDocument)getDocument()).isPosGuarded(offset);
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
        Util.beep_flush();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Undo handling.
    //

    @Override
    public void redo() {
        //undoOrRedo("Redo", FsAct.REDO);
        undoOrRedo("Redo", NbEditorKit.redoAction);
    }
    
    @Override
    public void undo() {
        //undoOrRedo("Undo", FsAct.UNDO);
        undoOrRedo("Undo", NbEditorKit.undoAction);
    }
    
    private void undoOrRedo(String tag, String action) {
        NbTextView tv = (NbTextView)Scheduler.getCurrentTextView();
        if(tv == null || !tv.isEditable()) {
            Util.beep_flush();
            return;
        }
        if(Misc.isInAnyUndo()) {
            ViManager.dumpStack(tag + " while in begin/endUndo");
            return;
        }
        // NEEDSWORK: check can undo for beep
        
        createDocChangeInfo();
        int n = getLineCount();
        tv.getOps().xact(action);
        ///// ActionEvent e = new ActionEvent(tv.getEditorComponent(),
        /////                                 ActionEvent.ACTION_PERFORMED,
        /////                                 "");
        ///// Module.execFileSystemAction(action, e);
        
        DocChangeInfo info = getDocChangeInfo();
        if(info.isChange) {
            try {
                // Only adjust the cursor if undo/redo left the cursor on col 0;
                // not entirely correct, but...
                if(info.offset != tv.w_cursor.getOffset()) {
                    tv.w_cursor.set(info.offset);
                }
                if(tv.w_cursor.getColumn() == 0) {
                    if(n != getLineCount())
                        Edit.beginline(BL_WHITE);
                    else if ("\n".equals(getText(tv.getCaretPosition(), 1))) {
                        Misc.check_cursor_col();
                    }
                }
            } catch (ViBadLocationException ex) { }
        } else
            Util.beep_flush();
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
        G.dbgUndo.printf("NbBuf:RunUndoable: \n");
        final Document doc = getDocument();
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
        G.dbgUndo.printf("NbBuf:do_beginUndo: \n");
        clearExceptions();
        Document doc = getDocument();
        if(doc instanceof BaseDocument) {
            ((BaseDocument)doc).atomicLock();
        }
    }
    
    @Override
    public void do_endUndo() {
        G.dbgUndo.printf("NbBuf:do_endUndo: \n");
        Document doc = getDocument();
        if(doc instanceof BaseDocument) {
            if(isAnyExecption()) {
                // get rid of the changes
                ((BaseDocument)doc).breakAtomicLock();
            }
            
            ((BaseDocument)doc).atomicUnlock();

            if(isAnyExecption()) {
                final ViTextView tv = Scheduler.getCurrentTextView();
                // This must come *after* atomicUnlock, otherwise it gets
                // overwritten by a clear message due to scrolling.
                
                // Don't want this message lost, so defer until things
                // settle down. The change/unlock-undo cause a lot of
                // action
                
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
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
        G.dbgUndo.printf("NbBuf:do_beginInsertUndo: \n");
        // NEDSWORK: when development on NB6, and method in NB6, use boolean
        //           for method is available and ifso invoke directly.
        if(G.isClassicUndo.getBoolean()) {
            sendUndoableEdit(CloneableEditorSupport.BEGIN_COMMIT_GROUP);
        }
    }

    @Override
    public void do_endInsertUndo() {
        G.dbgUndo.printf("NbBuf:do_beginInsertUndo: \n");
        if(G.isClassicUndo.getBoolean()) {
            sendUndoableEdit(CloneableEditorSupport.END_COMMIT_GROUP);
        }
    }

    void sendUndoableEdit(UndoableEdit ue) {
        Document d = getDocument();
        if(d instanceof AbstractDocument) {
            UndoableEditListener[] uels = ((AbstractDocument)d).getUndoableEditListeners();
            UndoableEditEvent ev = new UndoableEditEvent(d, ue);
            for(UndoableEditListener uel : uels) {
                uel.undoableEditHappened(ev);
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
        getDocument().removeDocumentListener(documentListener);
        getDocument().removeUndoableEditListener(undoableEditListener);
    }
    
    private void correlateDocumentEvents() {
        Document doc = getDocument();
        documentListener = new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {
                //dumpDocEvent("change", e);
            }
            @Override
            public void insertUpdate(DocumentEvent e) {
                dumpDocEvent("insert", e);
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                dumpDocEvent("remove", e);
            }
        };
        doc.addDocumentListener(documentListener);
        undoableEditListener = new UndoableEditListener() {
            @Override
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
