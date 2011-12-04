/*
 * NbBuffer.java
 *
 * Created on March 6, 2007, 11:17 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.netbeans.modules.jvi.impl;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.UndoableEditEvent;
import javax.swing.event.UndoableEditListener;
import javax.swing.text.AbstractDocument;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.undo.UndoableEdit;

import org.netbeans.api.editor.settings.SimpleValueNames;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.BaseDocumentEvent;
import org.netbeans.editor.GuardedDocument;
import org.netbeans.editor.GuardedException;
import org.netbeans.modules.editor.NbEditorKit;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.editor.indent.api.Indent;
import org.netbeans.modules.editor.indent.api.Reformat;
import org.openide.actions.UndoAction;
import org.openide.awt.UndoRedo;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.text.CloneableEditorSupport;
import org.openide.util.WeakListeners;
import org.openide.util.actions.SystemAction;

import com.raelity.jvi.ViBadLocationException;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.core.Edit;
import com.raelity.jvi.core.G;
import com.raelity.jvi.core.Misc;
import com.raelity.jvi.core.Options;
import com.raelity.jvi.core.Util;
import com.raelity.jvi.manager.Scheduler;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.swing.SwingBuffer;
import com.raelity.text.TextUtil;

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

        Map<String, Object> map = new HashMap<String, Object>();
        map.put(SimpleValueNames.EXPAND_TABS, b_p_et);
        map.put(SimpleValueNames.INDENT_SHIFT_WIDTH, b_p_sw);
        map.put(SimpleValueNames.SPACES_PER_TAB, b_p_ts);
        map.put(SimpleValueNames.TAB_SIZE, b_p_ts);
        setBlinkRate(map);
        NbJviPrefs.putPrefs(map, null, this);

    }

    @Override
    public void viOptionSet(ViTextView tv, String name) {
        super.viOptionSet(tv, name);

        Map<String, Object> map = new HashMap<String, Object>();

        if("b_p_ts".equals(name)) {
            map.put(SimpleValueNames.TAB_SIZE, b_p_ts);
            map.put(SimpleValueNames.SPACES_PER_TAB, b_p_ts);
        } else if("b_p_sw".equals(name)) {
            map.put(SimpleValueNames.INDENT_SHIFT_WIDTH, b_p_sw);
        } else if("b_p_et".equals(name)) {
            map.put(SimpleValueNames.EXPAND_TABS, b_p_et);
        }
        NbJviPrefs.putPrefs(map, null, this);
    }

    private void setBlinkRate(Map<String, Object> map) {
        map.put(SimpleValueNames.CARET_BLINK_RATE,
                Options.getOption(Options.caretBlinkRate).getInteger());
    }

    private void setBlinkRate() {
        Map<String, Object> map = new HashMap<String, Object>();
        setBlinkRate(map);
        NbJviPrefs.putPrefs(map, null, this);
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
    public void reindent(final int line, final int count) {
        if(getDocument() instanceof BaseDocument) {
            BaseDocument doc = (BaseDocument)getDocument();
            final Indent indent = Indent.get(doc);
            indent.lock();
            try {
                doc.runAtomicAsUser(new Runnable() {
                    @Override
                    public void run()
                    {
                        try {
                            indent.reindent(getLineStartOffset(line),
                                        getLineEndOffset2(line + count - 1));
                        } catch (BadLocationException ex) {
                            processTextException(ex);
                            LOG.log(Level.SEVERE, null, ex);
                        }
                    }
                });
            } finally {
                indent.unlock();
            }
        } else {
            Util.beep_flush();
        }
    }

    @Override
    public void reformat(final int line, final int count) {
        if(getDocument() instanceof BaseDocument) {
            BaseDocument doc = (BaseDocument)getDocument();
            final Reformat reformat = Reformat.get(doc);
            reformat.lock();
            try {
                doc.runAtomicAsUser(new Runnable() {
                    @Override
                    public void run()
                    {
                        try {
                            reformat.reformat(getLineStartOffset(line),
                                          getLineEndOffset2(line + count - 1));
                        } catch (BadLocationException ex) {
                            processTextException(ex);
                            LOG.log(Level.SEVERE, null, ex);
                        }
                    }
                });
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

    private boolean isGuardedException() {
        return fGuardedException;
    }

    private boolean isException() {
        return fException;
    }

    private boolean isAnyException() {
        return fException || fGuardedException;
    }

    private void clearExceptions() {
        fGuardedException = false;
        fException = false;
    }
    
    @Override
    protected void processTextException(BadLocationException ex) {
        if(ex instanceof GuardedException) {
            fGuardedException = true;
            fException = true;
        } else {
            fException = true;
        }
        Util.beep_flush();
    }

    @Override
    public void readOnlyError(ViTextView tv)
    {
        fGuardedException = true;
        fException = true;
        Util.beep_flush();
    }

    //////////////////////////////////////////////////////////////////////
    //
    // Undo handling.
    //
    // The main issue is handling automatic undo after an exception
    // during a programmatic modification.
    //
    // NEEDSWORK: this code uses the text view to get
    //            the undo/redo manager...
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

    /** true while doing a programmatic change */
    private boolean fCheckForAutoUndo;

    @Override
    public void do_beginUndo()
    {
        super.do_beginUndo();
        // during programmed doc changes exceptions should roll back everything
        fCheckForAutoUndo = true;
    }

    @Override
    protected void beginAnyUndo() {
        clearExceptions();
        createDocChangeInfo();
        sendUndoableEdit(CloneableEditorSupport.BEGIN_COMMIT_GROUP);
    }

    @Override
    protected void endAnyUndo()
    {
        sendUndoableEdit(CloneableEditorSupport.END_COMMIT_GROUP);
        if(fCheckForAutoUndo) {
            DocChangeInfo info = getDocChangeInfo();
            if(isAnyException() && info != null && info.isChange) {
                handleAutoUndoException(isGuardedException(),
                                        Scheduler.getCurrentTextView());
            }
            fCheckForAutoUndo = false;
        }
    }

    private void sendUndoableEdit(UndoableEdit ue) {
        Document d = getDocument();
        if(d instanceof AbstractDocument) {
            UndoableEditListener[] uels = ((AbstractDocument)d).getUndoableEditListeners();
            UndoableEditEvent ev = new UndoableEditEvent(d, ue);
            for(UndoableEditListener uel : uels) {
                uel.undoableEditHappened(ev);
            }
        }
    }

    private void handleAutoUndoException(final boolean isGuarded,
                                         ViTextView _tv)
    {
        final NbTextView tv = (NbTextView)_tv;
        G.dbgUndo().printf("endAnyUndo: exception rollback\n");
        doAutoUndo(tv);
        // Defer the message so it won't be lost
        ViManager.nInvokeLater(1, new Runnable() {
            @Override
            public void run() {
                tv.getStatusDisplay().displayErrorMessage(
                        "No changes made."
                        + (isGuarded
                           ? " Attempt to change guarded text."
                           : " Document location error."));
            }
        });
    }

    private void doAutoUndo(NbTextView tv)
    {
        // Can't just do "undo()"
        // because the action is not necessarily enabled.
        // This code is taken from NbEditorKit.NbUndoAction
        // and openide.action's UndoAction
        if (getDocument().getProperty(BaseDocument.UNDO_MANAGER_PROP) != null) {
            // Basic way of undo
            UndoableEdit undoMgr = (UndoableEdit)getDocument().getProperty(
                                       BaseDocument.UNDO_MANAGER_PROP);
            if(undoMgr.canUndo())
                undoMgr.undo();
        } else { // Deleagte to system undo action
            UndoAction ua = SystemAction.get(UndoAction.class);
            NbAppView av = (NbAppView)tv.getAppView();
            UndoRedo ur = av.getTopComponent().getUndoRedo();
            if(ur.canUndo()) {
                ur.undo();
            }
            // do isEnabled to make sure icons are in correct state
            ua.isEnabled();
        }
    }

    private void undoOrRedo(String tag, String action) {
        NbTextView tv = (NbTextView)Scheduler.getCurrentTextView();
        if(tv == null || !tv.isEditable()) {
            Util.beep_flush();
            return;
        }
        if(Misc.isInAnyUndo() || fCheckForAutoUndo) {
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
                ViManager.println(ue.getClass().getSimpleName()
                                   + " sig: " + ue.isSignificant());
                //System.err.println("UndoableEditEvent = " + e );
            }
        };
        //doc.addUndoableEditListener(undoableEditListener);
    }
    
    private void dumpDocEvent(String tag, DocumentEvent e_) {
        if("change".equals(tag)) {
            ViManager.println(tag + ": " + e_);
            return;
        }
        if(e_ instanceof BaseDocumentEvent) {
            BaseDocumentEvent e = (BaseDocumentEvent) e_;
            ViManager.println(tag + ": " + e.getType().toString() + ": "
                               + e.getOffset() + ":" + e.getLength() + " "
                               + "'" + TextUtil.debugString(e.getText()) + "'");
        }
    }
}
