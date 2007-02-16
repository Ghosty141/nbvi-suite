package org.netbeans.modules.jvi;

import com.raelity.jvi.G;
import com.raelity.jvi.Misc;
import com.raelity.jvi.Msg;
import com.raelity.jvi.Util;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViStatusDisplay;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.swing.TextView;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.GuardedException;
import org.netbeans.modules.editor.NbEditorKit;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;
import org.openide.windows.TopComponent;

/**
 * Pretty much the TextView used for standard swing.
 */
public class NbTextView extends TextView
{
    NbTextView(JEditorPane editorPane) {
        super(editorPane);
        cache = createTextViewCache();
        statusDisplay = new NbStatusDisplay(this);
    }

    public ViStatusDisplay getStatusDisplay() {
	return statusDisplay;
    }

    protected void createOps(JEditorPane editorPane) {
	ops = new NbOps(this);
	ops.init(editorPane);
    }

    public void displayFileInfo() {
	StringBuffer sb = new StringBuffer();
	sb.append("\"" + getDisplayFileName() + "\"");
	int l = getLineCount();
	sb.append(" " + l + " line" + Misc.plural(l));
	sb.append(" --" + (int)((cache.getCursor().getLine() * 100)
				  / getLineCount()) + "%--");
	getStatusDisplay().displayStatusMessage(sb.toString());
    }

    public String getDisplayFileName() {
	Document doc = getDoc();
	if(doc != null) {
	    FileObject fo = NbEditorUtilities.getFileObject(doc);
	    return fo.getNameExt();
	}
	return "";
    }

/* FROM ActionFactory
    public static class UndoAction extends LocalBaseAction {

        static final long serialVersionUID =8628586205035497612L;

        public UndoAction() {
            super(BaseKit.undoAction, ABBREV_RESET
                  | MAGIC_POSITION_RESET | UNDO_MERGE_RESET | WORD_MATCH_RESET);
        }

        public void actionPerformed(ActionEvent evt, JTextComponent target) {
            if (!target.isEditable() || !target.isEnabled()) {
                target.getToolkit().beep();
                return;
            }

            Document doc = target.getDocument();
            UndoableEdit undoMgr = (UndoableEdit)doc.getProperty(
                                       BaseDocument.UNDO_MANAGER_PROP);
            if (target != null && undoMgr != null) {
                try {
                    undoMgr.undo();
                } catch (CannotUndoException e) {
                    target.getToolkit().beep();
                }
            }
        }
    }
*/
    public void redo() {
	if( ! isEditable()) {
	    Util.vim_beep();
	    return;
	}
	if(isInUndo()||isInInsertUndo()) {
	    ViManager.dumpStack("Redo while in begin/endUndo");
            return;
	}
        // NEEDSWORK: check can undo for beep
        
	cache.isUndoChange(); // clears the flag
        ops.xact(NbEditorKit.redoAction);
	if(cache.isUndoChange()) {
            // NEEDSWORK: check if need newline adjust
	    setCaretPosition(cache.getUndoOffset());
	}
        // ops.xact(SystemAction.get(RedoAction.class)); // in openide
    }

    public void undo() {
	if( ! isEditable()) {
	    Util.vim_beep();
	    return;
	}
	if(isInUndo()||isInInsertUndo()) {
	    ViManager.dumpStack("Undo while in begin/endUndo");
            return;
	}
        // NEEDSWORK: check can undo for beep
        
	cache.isUndoChange(); // clears the flag
        ops.xact(NbEditorKit.undoAction);
	if(cache.isUndoChange()) {
            // NEEDSWORK: check if need newline adjust
	    setCaretPosition(cache.getUndoOffset());
	}
        // ops.xact(SystemAction.get(UndoAction.class)); // in openide
    }
    
    //
    // Undo handling.
    // 
    // With NB, the atomic lock on the document groups undo
    // so we can always do that for progromatic undo/redo, eg. "3dd".
    // But for insert mode locking the file has problems, so we
    // continue to use the classic undow flag
    //
  
    private boolean fGuardedException;
    private boolean fException;
  
    protected void processTextException(BadLocationException ex) {
        if(ex instanceof GuardedException) {
            fGuardedException = true;
        } else {
            fException = true;
        }
        Util.vim_beep();
    }

    public void beginUndo() {
        super.beginUndo();
	fGuardedException = false;
	fException = false;
        Document doc = getDoc();
        if(doc instanceof BaseDocument) {
            ((BaseDocument)doc).atomicLock();
        }
    }
    
    public void endUndo() {
        Document doc = getDoc();
        if(doc instanceof BaseDocument) {
	    if(fGuardedException || fException) {
                // get rid of the changes
		((BaseDocument)doc).breakAtomicLock();
	    }
            
	    ((BaseDocument)doc).atomicUnlock();
            
	    if(fGuardedException || fException) {
                // This must come *after* atomicUnlock, otherwise it gets
                // overwritten by a clear message due to scrolling.
                
		// Don't want this message lost, so defer until things
		// settle down. The change/unlock-undo cause a lot of
		// action

		SwingUtilities.invokeLater(new Runnable() {
		    public void run() {
			getStatusDisplay().displayErrorMessage(
			    "No changes made."
			    + (fGuardedException
                               ? " Attempt to change guarded text."
                               : " Document location error."));
		    }
		});
            }
        }
        super.endUndo();
    }

    public void beginInsertUndo() {
        super.beginInsertUndo();
        Document doc = getDoc();
        if(doc instanceof BaseDocument && G.isClassicUndo.getBoolean()) {
            ((BaseDocument)doc).atomicLock();
        }
    }
    
    public void endInsertUndo() {
        Document doc = getDoc();
        if(doc instanceof BaseDocument && G.isClassicUndo.getBoolean()) {
            ((BaseDocument)doc).atomicUnlock();
        }
        super.endInsertUndo();
    }
    
    /**
     * Find matching brace for char at the cursor
     */
    public void findMatch() {
        // NB's match brace action uses the character before the cursor
        int startingOffset = getCaretPosition();
        setCaretPosition(startingOffset + 1);
        ops.xact(NbEditorKit.matchBraceAction);
        if(getCaretPosition() != startingOffset + 1) {
            // it moved, success match, need to backup
            setCaretPosition(getCaretPosition()-1);
        } else {
            // match failed, back to original position (is this needed?)
            setCaretPosition(startingOffset);
        }
    }
    
    /**
     * Jump to the definiiton of the identifier unde the cursor.
     */
    public void jumpDefinition() {
        ops.xact(NbEditorKit.gotoDeclarationAction);
    }

  public void jumpList(JLOP op, int count) {
    switch(op) {
      case NEXT_CHANGE:
        ops.xact(NbEditorKit.jumpListNextAction);
        break;
          
      case PREV_CHANGE:
        ops.xact(NbEditorKit.jumpListPrevAction);
        break;
        
      case NEXT_JUMP:
      case PREV_JUMP:
	Util.vim_beep();
        break;
    }
  }

  public void anonymousMark(MARKOP op, int count) {
    String actName = null;
    switch(op) {
        case TOGGLE:
            actName = "/Actions/Edit/bookmark-toggle.instance";
            break;
        case NEXT:
            actName = "/Actions/Edit/bookmark-next.instance";
            break;
        case PREV:
            actName = "/Actions/Edit/bookmark-previous.instance";
            break;
    }
    Action act = Module.fetchFileSystemAction(actName);
    if(act != null && act.isEnabled()) {
        ActionEvent e = new ActionEvent(getEditorComponent(), 0, "");
        act.actionPerformed(e);
    } else
        Util.vim_beep();
  }

    public void foldOperation(int op) {
        String action = null;
        switch(op) {
            case ViTextView.FOLDOP_CLOSE:
                action = NbEditorKit.collapseFoldAction;
                break;
            case ViTextView.FOLDOP_OPEN:
                action = NbEditorKit.expandFoldAction;
                break;
            case ViTextView.FOLDOP_CLOSE_ALL:
                action = NbEditorKit.collapseAllFoldsAction;
                break;
            case ViTextView.FOLDOP_OPEN_ALL:
                action = NbEditorKit.expandAllFoldsAction;
                break;
        }
        if(action != null) {
            ops.xact(action);
        } else {
	    Util.vim_beep();
        }
    }
    
    //
    // Widow manipulation operations
    //

    public void win_quit() {
        // if split, close this half; otherwise close view
        win_close(false);
    }

    public void win_split(int n) {
        super.win_split(n);
    }

    public void win_goto(int n) {
        super.win_goto(n);
    }

    public void win_cycle(int n) {
        super.win_cycle(n);
    }

    public void win_close_others(boolean forceit) {
        super.win_close_others(forceit);
    }

    public void win_close(boolean freeBuf) {
        JEditorPane ep = getEditorComponent();
        TopComponent closeTC = NbFactory.getEditorTopComponent(ep);
        if(closeTC == null)
            return;
        
        // activate the previously active TC
        TopComponent prevTC = ((TopComponent)ViManager.getMruBuffer(1));
        if(prevTC != null)
            prevTC.requestActive();
        
        // and close the one requested
        if(!closeTC.close())
            Msg.emsg(getDisplayFileName() + " not closed");
    }
}
