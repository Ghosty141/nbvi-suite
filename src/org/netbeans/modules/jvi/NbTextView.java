package org.netbeans.modules.jvi;

import com.raelity.jvi.Buffer;
import com.raelity.jvi.G;
import com.raelity.jvi.Misc;
import com.raelity.jvi.Msg;
import com.raelity.jvi.Option.ColorOption;
import com.raelity.jvi.Options;
import com.raelity.jvi.Util;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViStatusDisplay;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.swing.TextView;
import java.awt.Color;
import java.io.File;
import javax.swing.JEditorPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.Segment;
import org.netbeans.editor.BaseDocument;
import org.netbeans.editor.BaseKit;
import org.netbeans.editor.Coloring;
import org.netbeans.editor.DrawContext;
import org.netbeans.editor.DrawLayer;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.MarkFactory;
import org.netbeans.editor.Settings;
import org.netbeans.editor.SettingsNames;
import org.netbeans.editor.Utilities;
import org.netbeans.modules.editor.NbEditorKit;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.windows.TopComponent;
import static com.raelity.jvi.Constants.*;

/**
 * Pretty much the TextView used for standard swing.
 */
public class NbTextView extends TextView
{
    // Use the corresponding values from DrawlayerFactory
    
    /** Highlight search layer name */
    public static final String HIGHLIGHT_SEARCH_LAYER
                                    = "vi-highlight-search-layer"; // NOI18N
    
    /** Highlight search layer visibility */
    public static final int VI_HIGHLIGHT_SEARCH_LAYER_VISIBILITY = 9000;
    
    /** Incremental search layer name */
    public static final String INC_SEARCH_LAYER
                                            = "vi-inc-search-layer"; // NOI18N
    
    /** Incremental search layer visibility */
    public static final int VI_INC_SEARCH_LAYER_VISIBILITY = 9500;
    
    /** Incremental search layer name */
    public static final String VISUAL_SELECT_LAYER
                                        = "vi-visual-select-layer"; // NOI18N
    
    public static final int VI_VISUAL_SELECT_LAYER_VISIBILITY = 9600;
    
    NbTextView(JEditorPane editorPane) {
        super(editorPane);
        //cache = createTextViewCache();
        statusDisplay = new NbStatusDisplay(this);
        
        // since NB insists that this is a shared variable
        // set the common value
        w_p_nu = showLineNumbers;
    }
    
    public void startup(Buffer buf) {
        super.startup(buf);
        
        // NEEDSWORK: the layer stuff should be in Buffer????
        
        // add jVi's DrawLayers
        EditorUI eui = Utilities.getEditorUI(getEditorComponent());
        if(eui != null) {
            // NEEDSWORK: layers not shared, used to be single layer for all tv
            eui.addLayer(new VisualSelectLayer(),
                         VI_VISUAL_SELECT_LAYER_VISIBILITY);
            eui.addLayer(new HighlightSearchLayer(),
                         VI_HIGHLIGHT_SEARCH_LAYER_VISIBILITY);
        }
    }
    
    public void shutdown() {
        EditorUI eui = Utilities.getEditorUI(getEditorComponent());
        if(eui != null) {
            eui.removeLayer(VISUAL_SELECT_LAYER);
            eui.removeLayer(HIGHLIGHT_SEARCH_LAYER);
        }
        super.shutdown();
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
        super.activateOptions(tv);
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
        if(ViManager.getViFactory().getFS().isModified(this))
            sb.append(" [Modified]");
        int l = getLineCount();
        sb.append(" " + l + " line" + Misc.plural(l));
        sb.append(" --" + (int)((cache.getCursor().getLine() * 100)
                                                    / getLineCount()) + "%--");
        getStatusDisplay().displayStatusMessage(sb.toString());
    }
    
    public String getDisplayFileName() {
        Document doc = (Document)getBuffer().getDocument();
        if(doc != null) {
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            /*System.err.println("getPath = " + fo.getPath());
            System.err.println("getFileDisplayName = "
                               + FileUtil.getFileDisplayName(fo));
            System.err.println("toFileAbs = " + FileUtil.toFile(fo).getAbsolutePath());
            System.err.println("toFileRel = " + FileUtil.toFile(fo).getPath());
            System.err.println("toFileParent = " + FileUtil.toFile(fo).getParent());*/
            if(fo != null)
                return fo.getNameExt();
        }
        return "UNKNOWN";
    }
    
    /**
     * NEEDSWORK: getFileName (getModifiedFileName)
     * In the future, to support multiple file modifiers, could take a File
     * as an argument, and return a File.
     *
     * At some point the guts can go into jVi Core, and only the translation
     * from FileObject to Object needs to be in here.
     * Probably have something like tv.getFile().
     *
     * The code that deals with extensions, :r, :e should just "do it".
     */
    public String getFileName(char option) {
        Document doc = (Document)getBuffer().getDocument();
        if(doc != null) {
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            if(fo != null){
                String filename;
                File fi = FileUtil.toFile(fo);
                switch (option){
                    case 'p':
                        filename = fi.getAbsolutePath();
                        break;
                    case 'e':
                        filename = fo.getExt(); // not in Java
                        break;
                    case 'r':
                        // NEEDSWORK: this ":r" code may not work in corner
                        // cases or with relative paths, but should cover all
                        // use cases in NetBeans
                        filename = fi.getParent() + fi.separator
                                    + fo.getName(); // not in Java
                        break;
                    case 't':
                        filename = fi.getName();
                        break;
                    case 'h':
                        if(fi.isAbsolute()) {
                            filename = fi.getParent();
                            break;
                        }
                        // FALLTHROUGH
                    case ' ':
                        filename = fi.getPath();
                        break;
                    default:
                        filename = fi.getPath()
                                   + ":" + new String(new char[] {option});
                        break;
                }
                
                if(G.p_ssl.getBoolean()){
                    // Shellslash is on, replace \ with /
                    filename = filename.replace('\\','/');
                } 
                return filename;
            }
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
    
    
    /**
     * Find matching brace for char at the cursor
     */
    public void findMatch() {
        if(NbCaret.goodGotoMatchBehavior()) {
            ops.xact(NbEditorKit.matchBraceAction);
        } else {
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

    public void reindent(int line, int count) {
        if(getBuffer().getDocument() instanceof BaseDocument) {
            try {
                Utilities.reformat((BaseDocument)getBuffer().getDocument(),
                                   getLineStartOffset(line),
                                   getLineEndOffset(line + count - 1));
                return;
            } catch (BadLocationException ex) { }
        }
        Util.vim_beep();
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
    
    public static class VisualSelectLayer extends HighlightBlocksLayer {
        private ColorOption selectColorOption;
        private Coloring selectColoring;
        
        VisualSelectLayer() {
            super(VISUAL_SELECT_LAYER);
            selectColorOption
                    = (ColorOption)Options.getOption(Options.selectColor);
            selectColoring = new Coloring(null, null,
                                          selectColorOption.getColor());
        }
        
        protected Coloring getColoring(DrawContext ctx) {
            Color c = selectColorOption.getColor();
            if(!c.equals(selectColoring.getBackColor()))
                selectColoring = new Coloring(null, null, c);
            return selectColoring;
        }
        
        protected int[] getBlocks(DrawContext ctx) {
            NbTextView tv = getTextView(ctx);
            return tv.getVisualSelectBlocks(ctx.getStartOffset(),
                                               ctx.getEndOffset());
        }
    }
    
    public void updateVisualState() {
        updateVisualSelectDisplay();
        EditorUI eui = Utilities.getEditorUI(getEditorComponent());
        if(eui != null) {
            // Enable/disable the visual select layer
            HighlightBlocksLayer dl = (HighlightBlocksLayer)
                                    eui.findLayer(VISUAL_SELECT_LAYER);
            if(dl != null) {
                dl.setEnabled(G.VIsual_active || G.drawSavedVisualBounds);
            }
            try {
                // Poke the document indicating that things have changed.
                eui.repaintBlock(0, eui.getDocument().getLength());
            } catch (BadLocationException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    public int[] getVisualSelectBlocks(int startOffset, int endOffset) {
        int[] xBlocks = super.getVisualSelectBlocks(startOffset, endOffset);
        //return xBlocks;
        return getInterestingBlocks(xBlocks, startOffset, endOffset);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // HighlightSearchLayer
    //
    
    public void updateHighlightSearchState() {
        updateHighlightSearchCommonState();
        EditorUI eui = Utilities.getEditorUI(getEditorComponent());
        if(eui != null) {
            // Enable/disable the hightlight search layer
            HighlightBlocksLayer dl;
            dl = (HighlightBlocksLayer) eui.findLayer(HIGHLIGHT_SEARCH_LAYER);
            if(dl != null) {
                dl.setEnabled(Options.doHighlightSearch());
            }
            try {
                // Poke the document indicating that things have changed.
                eui.repaintBlock(0, eui.getDocument().getLength());
            } catch (BadLocationException ex) {
                ex.printStackTrace();
            }
        }
    }
    
    public static class HighlightSearchLayer extends HighlightBlocksLayer {
        // private ColorOption selectColorOption;
        // private Coloring selectColoring;
        
        HighlightSearchLayer() {
            super(HIGHLIGHT_SEARCH_LAYER);
            /*
            selectColorOption = (ColorOption)
                                Options.getOption(Options.selectColor);
            selectColoring = new Coloring(null, null,
                                          selectColorOption.getColor());
             */
        }
        
        protected Coloring getColoring(DrawContext ctx) {
            return ctx.getEditorUI().getColoring(
                    SettingsNames.HIGHLIGHT_SEARCH_COLORING);
            /*
            Color c = selectColorOption.getColor();
            if(!c.equals(selectColoring.getBackColor()))
                selectColoring = new Coloring(null, null, c);
            return selectColoring;
             */
        }
        
        protected int[] getBlocks(DrawContext ctx) {
            NbTextView tv = getTextView(ctx);
            return tv.getHighlightSearchBlocks(ctx.getStartOffset(),
                                               ctx.getEndOffset());
            // int[] xBlocks = tv.getHighlightSearchBlocks(startOffset, endOffset);
            // return getInterestingBlocks(xBlocks, startOffset, endOffset);
        }
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Draw Layer based on integer array of blocks to highlight
    //

    static int[] getInterestingBlocks(int[] allBlocks,
                                      int startOffset,
                                      int endOffset) {
        
        // NEEDWORK: The array is not a cache anymore, so it can be modified,
        //           that avoids the arraycopy. Eventually, should be able
        //           to get rid of this method entirely by trimming the bounds
        //           elsewhere.
        
        // return relevent blocks properly bounded
        if(allBlocks[0] < 0) {
            return allBlocks;
        }

        // If within block, then adjust the start of the block
        if(allBlocks[0] <= startOffset && allBlocks[0] >= 0)
            allBlocks[0] = startOffset;
        //TextView.dumpBlocks("OUT", allBlocks);
        return allBlocks;
    }
    
    /** Highlight blocks layer highlights all occurences
     * indicated by the blocks array.
     */
    abstract static class HighlightBlocksLayer extends DrawLayer.AbstractLayer {
        
        /** Pairs of start and end position */
        //int blocks[] = new int[] { -1, -1 };
        int blocks[];
        
        /** Coloring to use for highlighting */
        Coloring coloring;
        Coloring defaultColoring;
        
        protected Coloring getColoring(DrawContext ctx) {
            if(defaultColoring == null)
                defaultColoring = new Coloring(null, null, Color.orange);
            return defaultColoring;
        }
        
        abstract protected int[] getBlocks(DrawContext ctx);
        
        protected NbTextView getTextView(DrawContext ctx) {
            NbTextView tv = (NbTextView) ViManager.getViTextView(
                                (JEditorPane) ctx.getEditorUI().getComponent());
            return tv;
        }
        
        /** Current index for painting */
        int curInd;
        
        /** Enabled flag */
        private boolean enabled;
        
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
            if (isEnabled()) {
                try { // Just in case..., see jvi-Bugs-1703078
                    blocks = getBlocks(ctx);
                } catch(Exception ex) {
                    ex.printStackTrace();
                    setEnabled(false);
                    blocks = new int[] { -1, -1};
                }
                coloring = null; // reset so it will be re-read
                curInd = 0;
            }
        }
        
        public boolean isActive(DrawContext ctx, MarkFactory.DrawMark mark) {
            boolean active;
            if (isEnabled()) {
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
                    coloring = getColoring(ctx);
                }
                if (coloring != null) {
                    coloring.apply(ctx);
                }
            }
        }
        
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // DRAW LAYER TEST STUFF
    //
    
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
        /* NEEDSWORK: test visual highlight
        if(tv.getEditorComponent().getDocument() instanceof BaseDocument) {
            // Poke ??? that things have changed.
            BaseDocument doc = (BaseDocument) tv.getEditorComponent().getDocument();
            //doc.repaintBlock(0, doc.getLength());
            
            // enable the visual select layer
            HighlightBlocksLayer dl;
            dl = (HighlightBlocksLayer) doc.findLayer(VISUAL_SELECT_LAYER);
            if(dl != null) {
                dl.setEnabled(true);
            }
        }*/
        
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
        
        int nLine = tv.getBuffer().getLineCount();
        for(int iLine = 1; iLine < nLine; iLine++) {
            if(iLine % modulo != 0)
                continue;
            int endLine = Math.min(iLine + contig -1, nLine);
            for(; iLine <= endLine; iLine++) {
                int lineOffset = tv.getBuffer().getLineStartOffset(iLine);
                Segment seg = tv.getBuffer().getLineSegment(iLine);
                if(seg.count > col1) {
                    tBlocks[idx++] = col1 + lineOffset;
                    tBlocks[idx++] = Math.min(col2, seg.count) + lineOffset;
                }
            }
        }
        tBlocks[idx++] = -1;
        tBlocks[idx++] = -1;
    }
}
