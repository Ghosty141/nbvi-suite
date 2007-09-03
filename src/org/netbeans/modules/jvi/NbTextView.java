package org.netbeans.modules.jvi;

import com.raelity.jvi.Buffer;
import com.raelity.jvi.G;
import com.raelity.jvi.Msg;
import com.raelity.jvi.Option.ColorOption;
import com.raelity.jvi.Options;
import com.raelity.jvi.Util;
import com.raelity.jvi.ViBuffer;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViStatusDisplay;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.swing.TextView;
import java.awt.Color;
import java.util.ArrayList;
import javax.swing.JEditorPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;
import javax.swing.text.Segment;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.FontColorNames;
import org.netbeans.api.editor.settings.FontColorSettings;
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
import org.netbeans.spi.editor.highlighting.HighlightsChangeEvent;
import org.netbeans.spi.editor.highlighting.HighlightsChangeListener;
import org.netbeans.spi.editor.highlighting.HighlightsLayer;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory;
import org.netbeans.spi.editor.highlighting.HighlightsLayerFactory.Context;
import org.netbeans.spi.editor.highlighting.HighlightsSequence;
import org.netbeans.spi.editor.highlighting.ZOrder;
import org.netbeans.spi.editor.highlighting.support.AbstractHighlightsContainer;
import org.netbeans.spi.editor.highlighting.support.OffsetsBag;
import org.netbeans.spi.editor.highlighting.support.PositionsBag;
import org.openide.filesystems.FileObject;
import org.openide.util.WeakListeners;
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
        statusDisplay = new NbStatusDisplay(this);
        
        // since NB insists that this is a shared variable
        // set the common value
        w_p_nu = showLineNumbers;
    }
    
    @Override
    public void startup(Buffer buf) {
        super.startup(buf);
        
        if(useOldLayers) {
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
    }
    
    @Override
    public void shutdown() {
        if(useOldLayers) {
            EditorUI eui = Utilities.getEditorUI(getEditorComponent());
            if(eui != null) {
                eui.removeLayer(VISUAL_SELECT_LAYER);
                eui.removeLayer(HIGHLIGHT_SEARCH_LAYER);
            }
        }
        super.shutdown();
        if(visualSelectHighlighter != null)
            visualSelectHighlighter.reset();
        if(searchResultsHighlighter != null)
            searchResultsHighlighter.reset();
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
    
    @Override
    public ViStatusDisplay getStatusDisplay() {
        return statusDisplay;
    }
    
    @Override
    protected void createOps(JEditorPane editorPane) {
        ops = new NbOps(this);
        ops.init(editorPane);
    }
    
    /**
     * Find matching brace for char at the cursor
     */
    @Override
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
    @Override
    public void jumpDefinition(String ident) {
        ops.xact(NbEditorKit.gotoDeclarationAction);
        ViManager.getViFactory().startTagPush(this, ident);
    }
    
    @Override
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
    
    @Override
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
    
    @Override
    public void win_quit() {
        // if split, close this half; otherwise close view
        win_close(false);
    }
    
    @Override
    public void win_split(int n) {
        super.win_split(n);
    }
    
    @Override
    public void win_goto(int n) {
        super.win_goto(n);
    }
    
    @Override
    public void win_cycle(int n) {
        super.win_cycle(n);
    }
    
    @Override
    public void win_close_others(boolean forceit) {
        super.win_close_others(forceit);
    }
    
    @Override
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
            Msg.emsg(getBuffer().getDisplayFileName() + " not closed");
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
        
        @Override
        protected Coloring getColoring(DrawContext ctx) {
            Color c = selectColorOption.getColor();
            if(!c.equals(selectColoring.getBackColor()))
                selectColoring = new Coloring(null, null, c);
            return selectColoring;
        }
        
        @Override
        protected int[] getBlocks(DrawContext ctx) {
            NbTextView tv = getTextView(ctx);
            if(tv == null) {
                return new int[] {-1,-1};
            }
            return tv.getTrimmedVisualSelectBlocks(ctx.getStartOffset(),
                                               ctx.getEndOffset());
        }
    }
    
    public void oldUpdateVisualState() {
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
    
    public int[] getTrimmedVisualSelectBlocks(int startOffset, int endOffset) {
        int[] xBlocks = getVisualSelectBlocks(startOffset, endOffset);
        //return xBlocks;
        return getInterestingBlocks(xBlocks, startOffset, endOffset);
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // HighlightSearchLayer
    //
    
    public void oldUpdateHighlightSearchState() {
        getBuffer().updateHighlightSearchCommonState();
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
        
        @Override
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
        
        @Override
        protected int[] getBlocks(DrawContext ctx) {
            NbTextView tv = getTextView(ctx);
            if(tv == null) {
                return new int[] {-1,-1};
            }
            return tv.getBuffer().getHighlightSearchBlocks(ctx.getStartOffset(),
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
            NbTextView tv = (NbTextView) ViManager.getViFactory()
                             .getExistingViTextView(
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
        
        @Override
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




    
    //////////////////////////////////////////////////////////////////////
    //
    // Highlighte for visual select mode and search results
    //

    // Use the Doc lock throughout?

    // The most recently created highlighters for this text view
    private BlocksHighlighter visualSelectHighlighter;
    private BlocksHighlighter searchResultsHighlighter;

    private static final boolean useOldLayers = false;
    private static final boolean dbgHL = false;

    public static final String VISUAL_MODE_LAYER
            = "VISUAL_SELECT_JVI";
    public static final String  SEARCH_RESULTS_LAYER 
            = "SEARCH_RESULTS_JVI";

    @Override
    public void updateVisualState() {
        if(useOldLayers) {
            oldUpdateVisualState();
            return;
        }
        updateVisualSelectDisplay();
        if(visualSelectHighlighter != null)
            visualSelectHighlighter.reset();
    }

    @Override
    public void updateHighlightSearchState() {
        if(useOldLayers) {
            oldUpdateHighlightSearchState();
            return;
        }
        getBuffer().updateHighlightSearchCommonState();
        if(searchResultsHighlighter != null)
            searchResultsHighlighter.reset();
    }

    // NEEDSWORK: there is no guarenteed way implemented to hook up a
    // highlighter to the text view. Need to set up a map of ep-->highli
    private void hookupHighlighter(String name, BlocksHighlighter h) {
        if(isShutdown())
            return;
        if(VISUAL_MODE_LAYER.equals(name)) {
            if(h != visualSelectHighlighter) {
                if(visualSelectHighlighter != null)
                    visualSelectHighlighter.discard();
                visualSelectHighlighter = h;
            }
        } else if(SEARCH_RESULTS_LAYER.equals(name)) {
            if(h != searchResultsHighlighter) {
                if(searchResultsHighlighter != null)
                    searchResultsHighlighter.discard();
                searchResultsHighlighter = h;
            }
        }
    }
    
    public static class HighlightsFactory implements HighlightsLayerFactory {

        public HighlightsLayer[] createLayers(Context context) {
            ArrayList<HighlightsLayer> layers
                    = new ArrayList<HighlightsLayer>();

            FileObject fo = NbEditorUtilities.getFileObject(
                    context.getDocument());
            if(dbgHL)
                System.err.println("Highlight Factory: "
                        + (fo != null ? fo.getNameExt() : ""));

            // NEEDSWORK: check that EP has a top component?
            // if context has jvi (PROP_JEP) create layers

            if(context.getComponent() instanceof JEditorPane) {
                JEditorPane ep = (JEditorPane)context.getComponent();


                // Take a look at TextSearchHighlighting.java in
                // lib2/src/org/netbeans/modules/editor/lib2/highlighting/
                layers.add(HighlightsLayer.create(
                    SEARCH_RESULTS_LAYER, 
                    ZOrder.SHOW_OFF_RACK.forPosition(200),
                    true,
                    //new SearchResultsHighlighter(SEARCH_RESULTS_LAYER, ep)
                    new SearchResultsHighlighter(SEARCH_RESULTS_LAYER, ep)
                
                ));

                layers.add(HighlightsLayer.create(
                    VISUAL_MODE_LAYER, 
                    ZOrder.SHOW_OFF_RACK.forPosition(800),
                    true,
                    new VisualSelectHighlighter(VISUAL_MODE_LAYER, ep)
                ));
            }

            if(useOldLayers)
                return new HighlightsLayer [0];
            return layers.toArray(new HighlightsLayer [layers.size()]);
        }
    }

    private static class VisualSelectHighlighter extends BlocksHighlighter {
        private ColorOption selectColorOption;
        private Color selectColor;
        private AttributeSet selectAttribs;

        VisualSelectHighlighter(String name, JEditorPane ep) {
            super(name, ep);

            selectColorOption
                    = (ColorOption)Options.getOption(Options.selectColor);
        }

        @Override
        protected int[] getBlocks(NbTextView tv, int startOffset, int endOffset) {
            return  tv.getVisualSelectBlocks(startOffset, endOffset);
        }

        @Override
        protected AttributeSet getAttribs() {
            if(!selectColorOption.getColor().equals(selectColor)) {
                selectColor = selectColorOption.getColor();
                selectAttribs = AttributesUtilities.createImmutable(
                        StyleConstants.Background, selectColor);
            }
            return selectAttribs;
        }

        protected boolean isEnabled() {
            return true;
        }
    }

    // Take a look at TextSearchHighlighting.java in
    // lib2/src/org/netbeans/modules/editor/lib2/highlighting/
    private static class SearchResultsHighlighter
            //extends AbstractHighlightsContainer
            extends BlocksHighlighter
            implements HighlightsChangeListener,
                       DocumentListener {
        
        private final AttributeSet attribs;

        @Override
        protected int[] getBlocks(NbTextView tv, int startOffset, int endOffset) {
            return tv.getBuffer().getHighlightSearchBlocks(startOffset,
                                                           endOffset);
        }

        protected AttributeSet getAttribs() {
            return attribs;
        }
        
        /** Creates a new instance of TextSearchHighlighter */
        public SearchResultsHighlighter(String name, JEditorPane ep) {
            super(name, ep);

            // Determine the color
            MimePath mimePath;
            EditorKit kit = ep.getUI().getEditorKit(ep);
            String mimeType = kit == null ? null : kit.getContentType();
            mimePath = mimeType == null ? MimePath.EMPTY : MimePath.parse(mimeType);
            FontColorSettings fcs = MimeLookup.getLookup(mimePath)
                                        .lookup(FontColorSettings.class);
            AttributeSet t;
            t = fcs.getFontColors(FontColorNames.HIGHLIGHT_SEARCH_COLORING);
            attribs = t == null ? SimpleAttributeSet.EMPTY : t;

            fillInTheBag();
        }
        
        protected boolean isEnabled() {
            return Options.doHighlightSearch();
        }
    }

    private abstract static class BlocksHighlighter
            extends AbstractHighlightsContainer
            implements HighlightsChangeListener,
                       DocumentListener {

        //
        // Is there a way to get active highlight container
        //
        protected final OffsetsBag bag;
        protected final JEditorPane ep;
        protected final Document document;
        protected final String name;

        BlocksHighlighter(String name, JEditorPane ep) {
            this.name = name;
            this.ep = ep;
            this.document = ep.getDocument();
            
            // Let the bag update first...
            this.bag = new OffsetsBag(document);
            this.bag.addHighlightsChangeListener(this);
            
            // ...and the internal listener second
            this.document.addDocumentListener(
                    WeakListeners.document(this, this.document));
        }

        // NEEDSWORK: THERE IS NO GUARENTEED WAY FOR THE tv TO
        // FIND this. The current scheme depends on the infrastructure
        // calling getHighlights, which will call getTv()

        protected NbTextView getTv() {
            NbTextView tv = (NbTextView)ViManager.getViFactory()
                                        .getExistingViTextView((ep));
            if(tv != null)
                tv.hookupHighlighter(name, this);
            return tv;
        }

        protected void discard() {
            bag.discard();
        }

        protected abstract int[] getBlocks(
                NbTextView tv, int startOffset, int endOffset);

        protected abstract AttributeSet getAttribs();

        protected abstract boolean isEnabled();

        void reset() {
            if(dbgHL)
                System.err.println("BlocksHighlighter reset:");
            fillInTheBag();
        }
        
        public void highlightChanged(HighlightsChangeEvent event) {
            if(dbgHL)
                System.err.println(name + " highlightChanged: "
                    + event.getStartOffset() + "," + event.getEndOffset());
            fireHighlightsChange(event.getStartOffset(), event.getEndOffset());
        }
        
        public HighlightsSequence getHighlights(int startOffset, int endOffset) {
            if(dbgHL)
                System.err.println("getHighlights: " + startOffset + "," + endOffset);
            getTv();
            return bag.getHighlights(startOffset, endOffset);
        }
        
        public void insertUpdate(DocumentEvent e) {
            // redo the full lines of the inserted area
            NbTextView tv = getTv();
            if(tv != null) {
                ViBuffer buf = tv.getBuffer();
                // set start,end to line numbers around the change
                int start = buf.getLineNumber(e.getOffset());
                int end = buf.getLineNumber(e.getOffset() + e.getLength());
                fillInTheBag(buf.getLineStartOffset(start),
                             buf.getLineEndOffset(end),
                             false);
            }
        }
        
        public void removeUpdate(DocumentEvent e) {
            // pick a few lines around the change
            NbTextView tv = getTv();
            if(tv != null) {
                ViBuffer buf = tv.getBuffer();
                // set start,end to line numbers around the change
                int start = buf.getLineNumber(e.getOffset());
                int end = start;
                if(start > 1)
                    --start;
                if(end < buf.getLineCount())
                    ++end;
                fillInTheBag(buf.getLineStartOffset(start),
                             buf.getLineEndOffset(end),
                             false);
            }
        }
        
        public void changedUpdate(DocumentEvent e) {
            // not interested
        }

        // NEEDSWORK: have a couple of different methods
        // bagNewBlocks(start,end)
        // bagReplaceBlocks(start,end)
        // ???
        
        // This entry recomputes entire document
        protected void fillInTheBag() {
            fillInTheBag(0, Integer.MAX_VALUE, true);
        }

        protected void fillInTheBag(final int startOffset,
                                  final int endOffset,
                                  final boolean replaceAll) {
            document.render(new Runnable() {
                public void run() {
                    OffsetsBag newBag = new OffsetsBag(document);
                    
                    NbTextView tv = getTv();
                    if (isEnabled() && tv != null) {

                        int [] blocks = getBlocks(tv, startOffset, endOffset);
                        if(dbgHL)
                            Buffer.dumpBlocks(name, blocks);
                        
                        AttributeSet as = getAttribs();
                        for (int i = 0; blocks[i] >= 0; i += 2) {
                            newBag.addHighlight(blocks[i],
                                    blocks[i + 1],
                                    as);
                        }
                    }
                    
                    if(replaceAll) {
                        bag.setHighlights(newBag);
                    } else {
                        int bug = -1;
                        bag.removeHighlights(startOffset, endOffset+bug, false);
                        bag.addAllHighlights(newBag.getHighlights(startOffset,
                                                                  endOffset));
                    }
                    newBag.discard();
                }
            });
        }

    }
}
