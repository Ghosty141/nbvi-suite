package org.netbeans.modules.jvi;

import com.raelity.jvi.G;
import com.raelity.jvi.Misc;
import com.raelity.jvi.Msg;
import com.raelity.jvi.Util;
import com.raelity.jvi.ViStatusDisplay;
import com.raelity.jvi.swing.TextView;
import javax.swing.JEditorPane;
import javax.swing.text.Document;
import org.netbeans.editor.BaseDocument;
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
	cache = createTextViewCache();
	statusDisplay = new NbStatusDisplay();
    }

    public ViStatusDisplay getStatusDisplay() {
	((NbStatusDisplay)statusDisplay).editorPane = editorPane;
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
        // NEEDSWORK: check can undo for beep
        ops.xact(NbEditorKit.redoAction);
      /*
	cache.isUndoChange(); // clears the flag
        ops.xact(SystemAction.get(RedoAction.class)); // in openide
	if(cache.isUndoChange()) {
	    setCaretPosition(cache.getUndoOffset());
	}
	*/
    }

    public void undo() {
	if( ! isEditable()) {
	    Util.vim_beep();
	    return;
	}
        // NEEDSWORK: check can undo for beep
        ops.xact(NbEditorKit.undoAction);
      /*
	cache.isUndoChange(); // clears the flag
        ops.xact(SystemAction.get(UndoAction.class)); // in openide
	if(cache.isUndoChange()) {
	    setCaretPosition(cache.getUndoOffset());
	}
      */
    }

    public void beginUndo() {
        super.beginUndo();
        Document doc = getDoc();
        if(doc instanceof BaseDocument && G.isClassicUndo.getBoolean()) {
            ((BaseDocument)doc).atomicLock();
        }
    }
    
    public void endUndo() {
        Document doc = getDoc();
        if(doc instanceof BaseDocument && G.isClassicUndo.getBoolean()) {
            ((BaseDocument)doc).atomicUnlock();
        }
        super.endUndo();
    }

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
        System.err.println("Closing " + getDisplayFileName());
        TopComponent tc = NbEditorUtilities.getTopComponent(getEditorComponent());
        if(tc != null && tc.close()) {
            return;
        }
        Msg.emsg(getDisplayFileName() + " not closed");
    }
}
