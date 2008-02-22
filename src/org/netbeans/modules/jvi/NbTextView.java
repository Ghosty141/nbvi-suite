package org.netbeans.modules.jvi;

import com.raelity.jvi.Buffer;
import com.raelity.jvi.Edit;
import com.raelity.jvi.G;
import com.raelity.jvi.Misc;
import com.raelity.jvi.Msg;
import com.raelity.jvi.Option.ColorOption;
import com.raelity.jvi.Options;
import com.raelity.jvi.Util;
import com.raelity.jvi.ViBuffer;
import com.raelity.jvi.ViFPOS;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViStatusDisplay;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.ViTextView.TABOP;
import com.raelity.jvi.ViTextView.WMOP;
import com.raelity.jvi.swing.TextView;
import com.raelity.text.TextUtil.MySegment;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.EditorKit;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.api.editor.mimelookup.MimePath;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.FontColorNames;
import org.netbeans.api.editor.settings.FontColorSettings;
import org.netbeans.editor.BaseKit;
import org.netbeans.editor.Settings;
import org.netbeans.editor.SettingsNames;
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
import org.openide.filesystems.FileObject;
import org.openide.util.WeakListeners;
import org.openide.windows.TopComponent;
import static com.raelity.jvi.Constants.*;

/**
 * Pretty much the TextView used for standard swing.
 */
public class NbTextView extends TextView
{
    private boolean isNomadic;

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
        
        getBuffer().getDocument().render(new Runnable() {
            public void run() {
                hookupHighlighter(VISUAL_MODE_LAYER, MyHl.getVisual(editorPane));
                hookupHighlighter(SEARCH_RESULTS_LAYER, MyHl.getSearch(editorPane));
            }
        });
    }
    
    @Override
    public void shutdown() {
        super.shutdown();
        if(visualSelectHighlighter != null) {
            visualSelectHighlighter.discard();
            visualSelectHighlighter = null;
        }
        if(searchResultsHighlighter != null) {
            searchResultsHighlighter.discard();
            searchResultsHighlighter = null;
        }
    }

    boolean isNomadic() {
        return isNomadic;
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

        TopComponent ancestor = (TopComponent)SwingUtilities
                .getAncestorOfClass(TopComponent.class, getEditorComponent());
        isNomadic = ancestor == null;
    
        if(isNomadic && G.dbgEditorActivation.getBoolean())
            System.err.println("ACTIVATING OPTIONS FOR NOMAD");
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
     * open_line: Add a new line within, below or above the current line.
     * <p>
     * VREPLACE mode not supported.
     * </p><p>
     * For insert/replace put the newline wherever the cursor is. Otherwise,
     * create an empty line either before or after the current line, according
     * to dir.
     * </p><p>
     * This is made messy by auto indent, guarded text, code folding
     * and end of file handling.
     * Consider a line, cursor shown as '|'
     * <pre>
     *     line1|\n
     *     line2\n
     * </pre>
     * This shows the simplest way to add a new line after the "line1",
     * auto-indent works fine and this positioning also works if line1 was the
     * last line of the file. But, consider the case where the cursor was on
     * line2 and the "O" command is given. Line 1 might be
     * guarded/write-protected in which case we generate an error. Also,
     * if line1 is the end of a fold, then the fold gets opened. So instead
     * we need to start with the cursor positioned as in
     * <pre>
     *     line1\n
     * |   line2\n
     * </pre>
     * Now stuff a '\n' directly into the document (not a newline action)
     * <pre>
     *     line1\n
     *
     * |   line2\n
     * </pre>
     * and the middle line has no indent. So we need to put the cursor back on
     * the middle line and do an indent and then finally we get
     *     line1\n
     *     |
     *     line2\n
     * </p>
     */
    @Override
    public boolean openNewLine(NLOP op) {
        final ViFPOS cursor = getWCursor();
        if(op == NLOP.NL_BACKWARD && cursor.getLine() == 1) {
            // Special case if BACKWARD and at first line of document.
            // set the caret position to 0 so that insert line on first line
            // works as well, set position just before new line of first line
            if(!Edit.canEdit(this, getBuffer(), 0))
                return false;
            setCaretPosition(0);
            insertNewLine();
            
            MySegment seg = getBuffer().getLineSegment(1);
            setCaretPosition(0 + Misc.coladvanceColumnIndex(MAXCOL, seg));
            //cursor.setPosition(1, coladvanceColumnIndex(MAXCOL, seg));
            return true;
        }
        
        // position cursor according to dir, probably an 'O' or 'o' command
        int line;
        int offset;
        boolean afterEOF = false;
        if(op == NLOP.NL_FORWARD) {
            // after the current line, but since we might be sitting on
            // a fold,
            offset = G.curwin.getBufferLineOffset(
                    G.curwin.getCoordLine(cursor.getLine()) + 1);
            if(offset > getBuffer().getLength()) {
                afterEOF = true;
                line = 0; // dont' care
            } else {
                line = getBuffer().getLineNumber(offset);
            }
        } else {
            // before the current line
            offset = getBuffer()
                        .getLineStartOffsetFromOffset(cursor.getOffset());
            line = cursor.getLine();
        }
        
        if(afterEOF) {
            offset--;
            if(!Edit.canEdit(this, getBuffer(), offset))
                return false;
            G.curwin.setCaretPosition(offset);
            G.curwin.insertNewLine();
            return true;
        }
        
        // offset is where insert happens
        if(!Edit.canEdit(this, getBuffer(), offset))
            return false;
        G.curwin.setCaretPosition(offset);
        //G.curwin.insertNewLine();
        getBuffer().insertText(offset, "\n");
        setCaretPosition(offset);
        getBuffer().reindent(line, 1);
        return true;
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
            case FOLDOP_CLOSE:
                action = NbEditorKit.collapseFoldAction;
                break;
            case FOLDOP_OPEN:
                action = NbEditorKit.expandFoldAction;
                break;
            case FOLDOP_CLOSE_ALL:
                action = NbEditorKit.collapseAllFoldsAction;
                break;
            case FOLDOP_OPEN_ALL:
                action = NbEditorKit.expandAllFoldsAction;
                break;
        }
        if(action != null) {
            ops.xact(action);
        } else {
            Util.vim_beep();
        }
    }

    @Override
    public void wordMatchOperation(WMOP op) {
        String action = null;
        switch(op) {
            case NEXT_WORD_MATCH:
                action = BaseKit.wordMatchNextAction;
                break;
            case PREV_WORD_MATCH:
                action = BaseKit.wordMatchPrevAction;
                break;
        }
        if(action != null) {
            ops.xact(action);
        } else {
            Util.vim_beep();
        }
    }
    
    @Override
    public void anonymousMark(MARKOP op, int count) {
        String actName = null;
        switch(op) {
            case TOGGLE:
                actName = FsAct.BM_TOGGLE;
                break;
            case NEXT:
                actName = FsAct.BM_NEXT;
                break;
            case PREV:
                actName = FsAct.BM_PREV;
                break;
        }
        Action act = Module.fetchFileSystemAction(actName);
        if(act != null && act.isEnabled()) {
            ActionEvent e = new ActionEvent(getBuffer().getDocument(), 0, "");
            act.actionPerformed(e);
        } else
            Util.vim_beep();
    }

    @Override
    public void tabOperation(TABOP op, int count) {
        String fsAct = null;
        switch(op) {
        case NEXT_TAB:
            fsAct = FsAct.TAB_NEXT;
            break;
        case PREV_TAB:
            fsAct = FsAct.TAB_PREV;
            break;
        }

        if(fsAct != null) {
            ActionEvent e = new ActionEvent(getEditorComponent(),
                                            ActionEvent.ACTION_PERFORMED,
                                            "");
            Module.execFileSystemAction(fsAct, e);
        } else
            Util.vim_beep();
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
    // Highlighte for visual select mode and search results
    //

    // Use the Doc lock throughout?

    // The most recently created highlighters for this text view
    private BlocksHighlighter visualSelectHighlighter;
    private BlocksHighlighter searchResultsHighlighter;

    public static final String VISUAL_MODE_LAYER
            = "VISUAL_SELECT_JVI";
    public static final String  SEARCH_RESULTS_LAYER 
            = "SEARCH_RESULTS_JVI";

    // NEEDSWORK: should these hilit.reset() be proteted by readLock?

    @Override
    public void updateVisualState() {
        if(visualSelectHighlighter != null)
            visualSelectHighlighter.reset();
    }

    @Override
    public void updateHighlightSearchState() {
        // NEEDSWORK: Should only reset if the search has changed since
        // the bag was last filled. Maybe put a serial number on the search
        if(searchResultsHighlighter != null)
            searchResultsHighlighter.reset();
    }

    private static boolean dbgHL(BlocksHighlighter h) {
        // filter out stuff if wanted
        //if(h.name == VISUAL_MODE_LAYER)
        //    return false;
        //if(h.name == SEARCH_RESULTS_LAYER)
        //    return false;

        return Module.dbgHL.getBoolean();
    }

    // NEEDSWORK: there is no guarenteed way implemented to hook up a
    // highlighter to the text view. Need to set up a map of ep-->highli
    private void hookupHighlighter(String name, BlocksHighlighter h) {
        if(isShutdown())
            return;
        BlocksHighlighter discarded = null;
        BlocksHighlighter installed = null;
        if(VISUAL_MODE_LAYER.equals(name)) {
            if(h != visualSelectHighlighter) {
                if(visualSelectHighlighter != null) {
                    discarded = visualSelectHighlighter;
                }
                visualSelectHighlighter = h;
                installed = h;
            }
        } else if(SEARCH_RESULTS_LAYER.equals(name)) {
            if(h != searchResultsHighlighter) {
                if(searchResultsHighlighter != null) {
                    discarded = searchResultsHighlighter;
                }
                searchResultsHighlighter = h;
                installed = h;
            }
        }
        if(discarded != null) {
            if(dbgHL(discarded))
                System.err.println(discarded.displayName()
                        + ": hookup discard");
            discarded.discard();
        }
        if(installed != null) {
            installed.tvTag = String.valueOf(mygen);
            if(dbgHL(installed))
                System.err.println(installed.displayName()
                        + ": hookup");
            ////////////////////////////////////////////////////////////////////
            if(installed == visualSelectHighlighter)
                updateVisualState(); // NEEDSWORK: CHANGE IF SHARE BAG?
            else
                updateHighlightSearchState(); // NEEDSWORK: CHANGE IF SHARE BAG?
            ////////////////////////////////////////////////////////////////////
        }
    }
    
    // Map so that text view can get hold of the highlighter
    // shortly after textview creation.
    private static Map<JEditorPane, MyHl> hlMap
            = new WeakHashMap<JEditorPane, MyHl>();

    private static class MyHl {
        WeakReference<VisualSelectHighlighter> visualRef;
        WeakReference<SearchResultsHighlighter> searchRef;

        static MyHl get(JEditorPane ep) {
            MyHl myHl = hlMap.get(ep);
            if(myHl == null) {
                myHl = new MyHl();
            }
            hlMap.put(ep, myHl);
            return myHl;
        }

        static void putVisual(JEditorPane ep, VisualSelectHighlighter visual) {
            synchronized(hlMap) {
                MyHl myHl = get(ep);
                myHl.visualRef
                        = new WeakReference<VisualSelectHighlighter>(visual);
            }
        }

        static VisualSelectHighlighter getVisual(JEditorPane ep) {
            synchronized(hlMap) {
                WeakReference<VisualSelectHighlighter> ref;
                ref = get(ep).visualRef;
                return ref != null ? ref.get() : null;
            }
        }

        static void putSearch(JEditorPane ep, SearchResultsHighlighter search) {
            synchronized(hlMap) {
                MyHl myHl = get(ep);
                myHl.searchRef
                        = new WeakReference<SearchResultsHighlighter>(search);
            }
        }

        static SearchResultsHighlighter getSearch(JEditorPane ep) {
            synchronized(hlMap) {
                WeakReference<SearchResultsHighlighter> ref;
                ref = get(ep).searchRef;
                return ref != null ? ref.get() : null;
            }
        }
    }

    
    public static class HighlightsFactory implements HighlightsLayerFactory {

        public HighlightsLayer[] createLayers(Context context) {
            ArrayList<HighlightsLayer> layers
                    = new ArrayList<HighlightsLayer>();

            FileObject fo = NbEditorUtilities.getFileObject(
                    context.getDocument());
            if(Module.dbgHL.getBoolean())
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
                    new SearchResultsHighlighter(SEARCH_RESULTS_LAYER, ep)
                
                ));

                layers.add(HighlightsLayer.create(
                    VISUAL_MODE_LAYER, 
                    ZOrder.SHOW_OFF_RACK.forPosition(800),
                    true,
                    new VisualSelectHighlighter(VISUAL_MODE_LAYER, ep)
                ));
            }

            return layers.toArray(new HighlightsLayer [layers.size()]);
        }
    }

    private static class VisualSelectHighlighter extends BlocksHighlighter {
        private ColorOption selectColorOption;
        private ColorOption selectFgColorOption;
        private Color selectColor;
        private Color selectFgColor;
        private AttributeSet selectAttribs;

        VisualSelectHighlighter(String name, JEditorPane ep) {
            super(name, ep);

            selectColorOption
                    = (ColorOption)Options.getOption(Options.selectColor);
            selectFgColorOption
                    = (ColorOption)Options.getOption(Options.selectFgColor);

            MyHl.putVisual(ep, this);
            if(dbgHL(this))
                System.err.println(displayName() + " putVisual");

            NbTextView tv = getTv();
            if(tv != null) {
                tv.hookupHighlighter(name, this);
            }
        }

        @Override
        protected int[] getBlocks(NbTextView tv, int startOffset, int endOffset) {
            return  tv.getBuffer().getVisualSelectBlocks(
                                        tv, startOffset, endOffset);
        }

        @Override
        protected AttributeSet getAttribs() {
            // NEEDSWORK: could listen to option change.
            // NEEDSWORK: using "!=" in following instead of '.equals'
            // to avoid messy 'null' handling (listener would fix that)
            if(selectColorOption.getColor() != selectColor
                    || selectFgColorOption.getColor() != selectFgColor) {
                selectColor = selectColorOption.getColor();
                selectFgColor = selectFgColorOption.getColor();
                List<Object> l = new ArrayList<Object>();
                l.add(StyleConstants.Background);
                l.add(selectColor);
                if(selectFgColor != null) {
                    l.add(StyleConstants.Foreground);
                    l.add(selectFgColor);
                }
                selectAttribs = AttributesUtilities.createImmutable(
                        l.toArray());
            }
            return selectAttribs;
        }

        protected boolean isEnabled() {
            return G.VIsual_active || G.drawSavedVisualBounds;
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

            MyHl.putSearch(ep, this);
            if(dbgHL(this))
                System.err.println(displayName() + " putSearch");

            NbTextView tv = getTv();
            if(tv != null) {
                tv.hookupHighlighter(name, this);
            }
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
        private OffsetsBag bag; // not final, not created in contructor
        protected final JEditorPane ep;
        protected final Document document;
        protected final String name;
        protected boolean isDiscarded;
        protected int mygen;
        protected static int gen;
        String tvTag = "";

        BlocksHighlighter(String name, JEditorPane ep) {
            this.name = name;
            this.ep = ep;
            this.document = ep.getDocument();
            mygen = ++gen;
            
            // Let the bag update first...
            this.bag = new OffsetsBag(document);
            this.bag.addHighlightsChangeListener(this);
            
            // ...and the internal listener second
            this.document.addDocumentListener(
                    WeakListeners.document(this, this.document));
        }

        protected String displayName() {
            return name + "-" + tvTag + "-" + mygen;
        }

        protected NbTextView getTv() {
            NbTextView tv = (NbTextView)ViManager.getViFactory()
                                        .getExistingViTextView((ep));
            return tv;
        }

        protected void discard() {
            bag.removeHighlightsChangeListener(this);
            bag.discard();
            isDiscarded = true;
        }

        protected abstract int[] getBlocks(
                NbTextView tv, int startOffset, int endOffset);

        protected abstract AttributeSet getAttribs();

        protected abstract boolean isEnabled();

        void reset() {
            if(dbgHL(this))
                System.err.println(displayName() + " BlocksHighlighter reset:");
            fillInTheBag();
        }
        
        public void highlightChanged(HighlightsChangeEvent event) {
            if(dbgHL(this))
                System.err.println(displayName() + " highlightChanged: "
                    + event.getStartOffset() + "," + event.getEndOffset());
            fireHighlightsChange(event.getStartOffset(), event.getEndOffset());
        }
        
        public HighlightsSequence getHighlights(int startOffset, int endOffset) {
            if(dbgHL(this)) {
                System.err.println(displayName() + " getHighlights: "
                                   + startOffset + "," + endOffset);
                dumpHLSeq(displayName(),
                          bag.getHighlights(startOffset, endOffset));
            }
            return bag.getHighlights(startOffset, endOffset);
        }
        
        public void insertUpdate(DocumentEvent e) {
            if(isDiscarded)
                return;
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
            if(isDiscarded)
                return;
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
                    if(isDiscarded)
                        return;
                    OffsetsBag newBag = new OffsetsBag(document);
                    
                    NbTextView tv = getTv();
                    if (isEnabled() && tv != null) {

                        int [] blocks = getBlocks(tv, startOffset, endOffset);
                        if(dbgHL(BlocksHighlighter.this))
                            Buffer.dumpBlocks(displayName(), blocks);
                        
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

    static void dumpHLSeq(String tag, HighlightsSequence seq) {
        System.err.print(tag + " seq:");
        if(HighlightsSequence.EMPTY.equals(seq)) {
            System.err.print(" EMPTY");
        }
        while(seq.moveNext()) {
            System.err.print(String.format(" {%d,%d}",
                    seq.getStartOffset(), seq.getEndOffset()));
        }
        System.err.println("");
    }
}
