package org.netbeans.modules.jvi;

import com.raelity.jvi.G;
import com.raelity.jvi.Misc;
import com.raelity.jvi.Msg;
import com.raelity.jvi.Options;
import com.raelity.jvi.Util;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViStatusDisplay;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.swing.TextView;
import java.awt.Color;
import java.awt.event.ActionEvent;
import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Segment;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.BaseKit;
import org.netbeans.editor.Coloring;
import org.netbeans.editor.DrawContext;
import org.netbeans.editor.DrawLayer;
import org.netbeans.editor.DrawLayer.AbstractLayer;
import org.netbeans.editor.DrawLayerFactory;
import org.netbeans.editor.GuardedException;
import org.netbeans.editor.MarkFactory;
import org.netbeans.editor.Settings;
import org.netbeans.editor.SettingsNames;
import org.netbeans.modules.editor.NbEditorKit;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;
import org.openide.windows.TopComponent;

/**
 * Pretty much the TextView used for standard swing.
 */
public class NbTextView extends TextView
{
    // Use the corresponding values from DrawlayerFactory
    
    /** Highlight search layer name */
    public static final String VI_HIGHLIGHT_SEARCH_LAYER_NAME
                                        = "vi-highlight-search-layer"; // NOI18N

    /** Highlight search layer visibility */
    public static final int VI_HIGHLIGHT_SEARCH_LAYER_VISIBILITY = 9000;

    /** Incremental search layer name */
    public static final String VI_INC_SEARCH_LAYER_NAME
                                            = "vi-inc-search-layer"; // NOI18N

    /** Incremental search layer visibility */
    public static final int VI_INC_SEARCH_LAYER_VISIBILITY = 9500;

    /** Incremental search layer name */
    public static final String VI_VISUAL_SELECT_LAYER_NAME
                                            = "vi-visual-select-layer"; // NOI18N
    
    public static final int VI_VISUAL_SELECT_LAYER_VISIBILITY = 9600;
    
    NbTextView(JEditorPane editorPane) {
        super(editorPane);
        cache = createTextViewCache();
        statusDisplay = new NbStatusDisplay(this);
        
        // since NB insists that this is a shared variable
        // set the common value
        w_p_nu = showLineNumbers;
        
        // add jVi's DrawLayers
        if(editorPane.getDocument() instanceof BaseDocument) {
            BaseDocument doc = (BaseDocument) editorPane.getDocument();
            doc.addLayer(new VisualSelectLayer(),
                         VI_VISUAL_SELECT_LAYER_VISIBILITY);
        }
    }
    
    //
    // The viOptionBag interface
    //
    
    // Line numbers is a global in NB, not per window
    private static boolean showLineNumbers;
    
    @Override
    public void viOptionSet(ViTextView tv, String name) {
        if("w_p_nu".equals(name)) {
            showLineNumbers = w_p_nu;
            Settings.setValue(BaseKit.class,
                              SettingsNames.LINE_NUMBER_VISIBLE,
                              w_p_nu);
            Options.SetCommand.syncAllInstances("w_p_nu");
        }
    }

    @Override
    public void activateOptions(ViTextView tv) {
    }
    
    // 
    // The viTextView interface
    //

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
            if(fo != null)
                return fo.getNameExt();
	}
	return "UNKNOWN";
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
     * Jump to the definition of the identifier unde the cursor.
     */
    public void jumpDefinition(String ident) {
        ops.xact(NbEditorKit.gotoDeclarationAction);
        ViManager.getViFactory().startTagPush(this, ident);
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
    
    //////////////////////////////////////////////////////////////////////
    //
    // VisualSelectLayer
    //
    
    public class VisualSelectLayer extends HighlightBlocksLayer {
        VisualSelectLayer() {
            super(VI_VISUAL_SELECT_LAYER_NAME);
        }
        
        protected Coloring getColoring() {
            return new Coloring(null, null, Options.getSelectColor());
        }
        
        protected int[] getBlocks(int startOffset, int endOffset) {
            return getVisualSelectBlocks(startOffset, endOffset);
        }
    }
    
    public void updateVisualState() {
        if(getDoc() instanceof BaseDocument) {
            BaseDocument doc = (BaseDocument) getDoc();
            
            // Enable/disable the visual select layer
            HighlightBlocksLayer dl
                        = (HighlightBlocksLayer)
                                    doc.findLayer(VI_VISUAL_SELECT_LAYER_NAME);
            if(dl != null) {
                dl.setEnabled(G.VIsual_active || G.Visual_active_colon
                              ? true : false);
            }
            
            // Poke the document indicating that things have changed.
            doc.repaintBlock(0, doc.getLength());
        }
    }
    
    public int[] getVisualSelectBlocks(int startOffset, int endOffset) {
        // allBlocks = tBlocks; // TESTING, TESTING .....
        
        //int[] allBlocks = super.getVisualSelectBlocks(startOffset, endOffset);
        
        // Pass in an array terminated with '-1', '-1'
        // remove this, and uncomment above, if super terminates with -1,-1
        // and sets up previous highlight
        int[] xBlocks = super.getVisualSelectBlocks(startOffset, endOffset);
        previousHighlight = xBlocks;
        
        int[] allBlocks = new int[xBlocks.length +2];
        System.arraycopy(xBlocks, 0, allBlocks, 0, xBlocks.length);
        allBlocks[allBlocks.length -2] = -1;
        allBlocks[allBlocks.length -1] = -1;
        
        return getInterestingBlocks(allBlocks, startOffset, endOffset);
    }
    
    static int tBlocks[];
    
    /** Scan through the file building a blocks array; TEST ONLY.
     * @param col1 start
     * @param modulo start at every Nth line, if -1 then file offsets
     * @param contig contiguous lines to apply highlight
     */
    static void testVisualHighlight(int col1, int col2,
                                    int modulo, int contig) {
        System.err.println("" + col1 + ", " + col2 + ", "
                           + modulo + ", " + contig);
        
        if(tBlocks == null) {
            tBlocks = new int[300];
            tBlocks[0] = -1;
            tBlocks[1] = -1;
        }
        
        ViTextView tv = G.curwin;
        
        // enable the layer
        if(tv.getEditorComponent().getDocument() instanceof BaseDocument) {
            // Poke ??? that things have changed.
            BaseDocument doc = (BaseDocument) tv.getEditorComponent().getDocument();
            //doc.repaintBlock(0, doc.getLength());
            
            // enable the visual select layer
            HighlightBlocksLayer dl
                        = (HighlightBlocksLayer)
                                    doc.findLayer(VI_VISUAL_SELECT_LAYER_NAME);
            if(dl != null) {
                dl.setEnabled(true);
            }
        }
        
        if(modulo != -1 && contig >= modulo)
            return;
        
        if(col1 > col2) {
            int t = col1;
            col1 = col2;
            col2 = t;
        }
        
        int idx = 0;
        if(modulo < 0) {
            tBlocks[idx++] = col1;
            tBlocks[idx++] = col2;
            tBlocks[idx++] = -1;
            tBlocks[idx++] = -1;
            return;
        }
        
        int nLine = tv.getLineCount();
        for(int iLine = 1; iLine < nLine; iLine++) {
            if(iLine % modulo != 0)
                continue;
            int endLine = Math.min(iLine + contig -1, nLine);
            for(; iLine <= endLine; iLine++) {
                int lineOffset = tv.getLineStartOffset(iLine);
                Segment seg = tv.getLineSegment(iLine);
                if(seg.count > col1) {
                    tBlocks[idx++] = col1 + lineOffset;
                    tBlocks[idx++] = Math.min(col2, seg.count) + lineOffset;
                }
            }
        }
        tBlocks[idx++] = -1;
        tBlocks[idx++] = -1;
    }
    
    private int[] getInterestingBlocks(int[] allBlocks,
                                       int startOffset,
                                       int endOffset) {
        // return relevent blocks properly bounded
        if(allBlocks[0] < 0) {
            return new int[] { -1, -1};
        }
        
        //
        // find the first block of interest
        //
        
        // skip blocks until startOffset within or after block
        int idx = 0;
        for(;
               idx < allBlocks.length
            && allBlocks[idx +2] != -1
            && allBlocks[idx +1] < startOffset ;
            idx += 2);
        if(startOffset > allBlocks[idx +1])
            idx += 2;
        
        // copy what's needed
        int t[] = new int[allBlocks.length];
        System.arraycopy(allBlocks, idx, t, 0,
                         Math.min(allBlocks.length - idx, t.length - 1));
        
        // If within block, then adjust the start of the block
        if(t[0] <= startOffset)
            t[0] = startOffset;
        t[t.length -2] = -1;
        t[t.length -1] = -1;
        return t;
    }
    
    /** Highlight blocks layer highlights all occurences
    * indicated by the blocks array.
    */
    abstract class HighlightBlocksLayer extends DrawLayer.AbstractLayer {

        /** Pairs of start and end position */
        //int blocks[] = new int[] { -1, -1 };
        int blocks[];

        /** Coloring to use for highlighting */
        Coloring coloring;
        Coloring defaultColoring;
        
        protected Coloring getColoring() {
            if(defaultColoring == null)
                defaultColoring = new Coloring(null, null, Color.orange);
            return defaultColoring;
        }
        
        abstract protected int[] getBlocks(int startOffset, int endOffset);

        /** Current index for painting */
        int curInd;

        /** Enabled flag */
        protected boolean enabled;

        protected HighlightBlocksLayer(String layerName) {
            super(layerName);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public void init(DrawContext ctx) {
            if (enabled) {
                blocks = getBlocks(ctx.getStartOffset(), ctx.getEndOffset());
                coloring = null; // reset so it will be re-read
                curInd = 0;
            }
        }

        public boolean isActive(DrawContext ctx, MarkFactory.DrawMark mark) {
            boolean active;
            if (enabled) {
                int pos = ctx.getFragmentOffset();
                if (pos == blocks[curInd]) {
                    active = true;
                    setNextActivityChangeOffset(blocks[curInd + 1]);

                } else if (pos == blocks[curInd + 1]) {
                    active = false;
                    curInd += 2;
                    setNextActivityChangeOffset(blocks[curInd]);
                    if (pos == blocks[curInd]) { // just follows
                        setNextActivityChangeOffset(blocks[curInd + 1]);
                        active = true;
                    }

                } else {
                    setNextActivityChangeOffset(blocks[curInd]);
                    active = false;
                }
            } else {
                active = false;
            }

            return active;
        }

        public void updateContext(DrawContext ctx) {
            int pos = ctx.getFragmentOffset();
            if (pos >= blocks[curInd] && pos < blocks[curInd + 1]) {
                if (coloring == null) {
                    //coloring = ctx.getEditorUI().getColoring(SettingsNames.HIGHLIGHT_SEARCH_COLORING);
                    coloring = getColoring();
                }
                if (coloring != null) {
                    coloring.apply(ctx);
                }
            }
        }

    }
}
