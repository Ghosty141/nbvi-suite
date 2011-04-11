package org.netbeans.modules.jvi.impl;

import org.netbeans.modules.jvi.JViOptionWarning;
import com.raelity.jvi.ViAppView;
import com.raelity.jvi.core.Buffer;
import com.raelity.jvi.core.Edit;
import com.raelity.jvi.core.G;
import com.raelity.jvi.core.Misc;
import com.raelity.jvi.core.Msg;
import com.raelity.jvi.options.ColorOption;
import com.raelity.jvi.core.Options;
import com.raelity.jvi.core.Util;
import com.raelity.jvi.ViBuffer;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.ViStatusDisplay;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.ViTextView.TABOP;
import com.raelity.jvi.ViTextView.WMOP;
import com.raelity.jvi.manager.AppViews;
import com.raelity.jvi.options.SetColonCommand;
import com.raelity.jvi.swing.SwingTextView;
import com.raelity.text.TextUtil.MySegment;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.prefs.Preferences;
import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;
import org.netbeans.api.editor.fold.Fold;
import org.netbeans.api.editor.fold.FoldHierarchy;
import org.netbeans.api.editor.fold.FoldUtilities;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.SimpleValueNames;
import org.netbeans.editor.BaseKit;
import org.netbeans.modules.editor.NbEditorKit;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.editor.indent.spi.CodeStylePreferences;
import org.netbeans.modules.jvi.FsAct;
import org.netbeans.modules.jvi.Module;
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
import static com.raelity.jvi.core.lib.Constants.*;

/**
 * Pretty much the SwingTextView used for standard swing.
 */
public class NbTextView extends SwingTextView
{

    NbTextView(JEditorPane editorPane) {
        super(editorPane);
        statusDisplay = new NbStatusDisplay(this);
    }
    
    @Override
    public void startup() {
        super.startup();
        
        getBuffer().getDocument().render(new Runnable() {
            @Override
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
            visualSelectHighlighter.goIdle();
            visualSelectHighlighter = null;
        }
        if(searchResultsHighlighter != null) {
            searchResultsHighlighter.goIdle();
            searchResultsHighlighter = null;
        }
    }

    @Override
    public void activateOptions(ViTextView tv)
    {
        super.activateOptions(tv);
        Preferences codePrefs
                = CodeStylePreferences.get((Document)null).getPreferences();

        JViOptionWarning.setInternalAction(true);
        try {
            codePrefs.putBoolean(SimpleValueNames.LINE_NUMBER_VISIBLE, w_p_nu);
            codePrefs.putBoolean(
                    SimpleValueNames.NON_PRINTABLE_CHARACTERS_VISIBLE,
                    w_p_list);
            setWrapPref();
        } finally {
            JViOptionWarning.setInternalAction(false);
        }
    }
    
    //
    // The viOptionBag interface
    //
    
    @Override
    public void viOptionSet(ViTextView tv, String name) {
        super.viOptionSet(tv, name);

        assert this == tv;

        JViOptionWarning.setInternalAction(true);
        try {
            if("w_p_nu".equals(name)) {
                // global affect, just do it
                CodeStylePreferences.get((Document)null).getPreferences()
                        .putBoolean(SimpleValueNames.LINE_NUMBER_VISIBLE,
                                    w_p_nu);
            } else if("w_p_list".equals(name)) {
                // global affect, just do it
                CodeStylePreferences.get((Document)null).getPreferences()
                        .putBoolean(
                            SimpleValueNames.NON_PRINTABLE_CHARACTERS_VISIBLE,
                            w_p_list);
            } else if("w_p_wrap".equals(name)) {
                // vim wants per textView, NB supports per buf
                SetColonCommand.syncTextViewInstances(name, this);
                setWrapPref();
            } else if("w_p_lbr".equals(name)) {
                // vim wants per textView, NB supports per buf
                SetColonCommand.syncTextViewInstances(name, this);
                setWrapPref();
            }
        } finally {
            JViOptionWarning.setInternalAction(false);
        }
    }

    private void setWrapPref()
    {
        Preferences prefs = CodeStylePreferences.get(
                getEditorComponent().getDocument()).getPreferences();
        String s = !w_p_wrap ? "none" : w_p_lbr ? "words" : "chars";
        prefs.put(SimpleValueNames.TEXT_LINE_WRAP, s);
        EventQueue.invokeLater(new Runnable()
        {
            @Override
            public void run()
            {
                // NOTE: not spinning through the editor registry.
                //       This gives per document control (unlike stock NB editor)
                // Needs to check for null since it might be closed
                // by the time we get here.
                if(getEditorComponent() == null
                        || getEditorComponent().getDocument() == null)
                    return;
                getEditorComponent().getDocument()
                    .putProperty(SimpleValueNames.TEXT_LINE_WRAP, "");
            }
        });
    }

    //
    // The viTextView interface
    //
    
    @Override
    public ViStatusDisplay getStatusDisplay() {
        return statusDisplay;
    }
    
    @Override
    protected void createOps() {
        ops = new NbOps(this);
    }

    @Override
    public int getRequiredVpLines()
    {
        int nLines = getVpLines();
        if(ViManager.getHackFlag(Module.HACK_SCROLL))
            nLines = nLines / 2;
        return nLines;
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
    public boolean openNewLine(DIR op) {
        if ( !isEditable() ) {
            Util.beep_flush();
            return false;
        }
        if(op == DIR.BACKWARD && w_cursor.getLine() == 1) {
            // Special case if BACKWARD and at first line of document.
            // set the caret position to 0 so that insert line on first line
            // works as well, set position just before new line of first line
            if(!Edit.canEdit(this, getBuffer(), 0))
                return false;
            w_cursor.set(0);
            insertNewLine();
            
            MySegment seg = getBuffer().getLineSegment(1);
            w_cursor.set(0 + Misc.coladvanceColumnIndex(MAXCOL, seg));
            return true;
        }
        
        // Position cursor according to dir, probably an 'O' or 'o' command.
        int offset; // First set to after \n and check if after EOF
        boolean afterEOF = false;
        int line; // Set to the line number of new line, must be > 1
        if(op == DIR.FORWARD) {
            // add line after the current line
            offset = getDocLineOffset(
                    getLogicalLine(w_cursor.getLine()) + 1);
            if(offset > getBuffer().getLength()) {
                afterEOF = true;
                line = 0; // dont' care
            } else {
                line = getBuffer().getLineNumber(offset);
            }
        } else {
            // add line before the current line
            offset = getBuffer()
                        .getLineStartOffsetFromOffset(w_cursor.getOffset());
            line = w_cursor.getLine();
        }
        --offset; // on line before new line at the char before the \n

        // Check out the fold situation
        boolean inCollapsedFold = false;
        if(!afterEOF) { // folding doesn't matter if at end of file
            FoldHierarchy fh = FoldHierarchy.get(editorPane);
            fh.lock();
            try {
                // Is the line before where the newly opened line goes
                // (position before the newline) in a collapsed fold?
                int tOff = getBuffer().getLineStartOffset(line-1);
                Fold f = FoldUtilities.findCollapsedFold(fh, tOff, tOff);
                if(f != null)
                    inCollapsedFold = true;
            } finally {
                fh.unlock();
            }
        }

        // And the guarded section
        boolean inGuarded = getBuffer().isGuarded(offset);
        
        // Everything could go through the "afterEOF" case, except for
        // problems with unmodifiable or folded text, see method comment.
        if(afterEOF || !(inCollapsedFold || inGuarded)) {
            if(!Edit.canEdit(this, getBuffer(), offset))
                return false;
            w_cursor.set(offset);
            insertNewLine();
            return true;
        }

        //
        
        ++offset; // oops, back to after newline and some special handling
        if(!Edit.canEdit(this, getBuffer(), offset))
            return false;
        w_cursor.set(offset);
        //insertNewLine();
        getBuffer().insertText(offset, "\n");
        w_cursor.set(offset);
        getBuffer().reindent(line, 1);
        return true;
    }
    /**
     * Find matching brace for char at the cursor
     */
    @Override
    public void findMatch() {
        ops.xact(NbEditorKit.matchBraceAction);
    }
    
    /**
     * Jump to the definition of the identifier unde the cursor.
     */
    @Override
    public void jumpDefinition(String ident) {
        ops.xact(NbEditorKit.gotoDeclarationAction);
        ViManager.getFactory().startTagPush(this, ident);
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
                Util.beep_flush();
                break;
        }
    }
    
    @Override
    public void foldOperation(FOLDOP op) {
        String action = null;
        switch(op) {
            case CLOSE:
                action = NbEditorKit.collapseFoldAction;
                break;
            case OPEN:
                action = NbEditorKit.expandFoldAction;
                break;
            case CLOSE_ALL:
                action = NbEditorKit.collapseAllFoldsAction;
                break;
            case OPEN_ALL:
                action = NbEditorKit.expandAllFoldsAction;
                break;
        }
        if(action != null) {
            ops.xact(action);
        } else {
            Util.beep_flush();
        }
    }
    
    @Override
    public void foldOperation(FOLDOP op, final int offset) {
        boolean error = false;
        switch(op) {
            case OPEN:
            case CLOSE:
            case OPEN_ALL:
            case CLOSE_ALL:
                error = true;
                break;
            case MAKE_VISIBLE:
                break;
        }

        if(error) {
            Util.beep_flush();
            return;
        }

        // NOTE: MAKE_VISIBLE is the only thing supported

        final FoldHierarchy fh = FoldHierarchy.get(getEditorComponent());

        // get the fold containing the offset,
        // expand it and all its parents
        getEditorComponent().getDocument().render(new Runnable() {
            public void run() {
                fh.lock();
                try {
                    // Fold f = FoldUtilities.findCollapsedFold(
                    //         fh, offset, offset);
                    Fold f = FoldUtilities.findOffsetFold(fh, offset);
                    while(true) {
                        if (f == null) {
                            // System.err.println("NULL FOLD");
                            break;
                        } else {
                            // int start = f.getStartOffset();
                            // int end = f.getEndOffset();
                            // System.err.println(String.format("%s < %s < %s",
                            //         start, offset, end));
                            // if(!(start < offset && offset < end)) {
                            //     System.err.println("FOLD NOT IN RANGE");
                            // }
                            fh.expand(f);
                        }
                        f = f.getParent();
                    }
                } finally {
                    fh.unlock();
                }
            }
        });
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
            Util.beep_flush();
        }
    }
    
    @Override
    public void anonymousMark(MARKOP op, int count) {
        FsAct fsAct = null;
        switch(op) {
            case TOGGLE:
                fsAct = FsAct.BM_TOGGLE;
                break;
            case NEXT:
                fsAct = FsAct.BM_NEXT;
                break;
            case PREV:
                fsAct = FsAct.BM_PREV;
                break;
        }
        Action act = Module.fetchFileSystemAction(fsAct);
        if(act != null && act.isEnabled()) {
            ActionEvent e = new ActionEvent(getBuffer().getDocument(), 0, "");
            act.actionPerformed(e);
        } else
            Util.beep_flush();
    }

    @Override
    public void tabOperation(TABOP op, int count) {
        FsAct fsAct = null;
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
            Util.beep_flush();
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
    public void win_close_others(boolean forceit) {
        List<ViAppView> avs = AppViews.getList(AppViews.ACTIVE);
        int idx = AppViews.indexOfCurrentAppView(avs);


        if(avs.size() <= 1 || idx < 0)
            return;

        for (int i = 0; i < avs.size(); i++) {
            if(i != idx)
                ((NbAppView)avs.get(i)).getTopComponent().close();
        }
    }
    
    @Override
    public void win_close(boolean freeBuf) {
        NbAppView avClose = (NbAppView)getAppView();

        if(avClose == null)
            return;
        
        // activate the previously active TC
        NbAppView avPrev = (NbAppView)AppViews.getMruAppView(1);
        TopComponent prevTC = null;
        if(avPrev != null)
                prevTC = avPrev.getTopComponent();
        if(prevTC != null)
            prevTC.requestActive();
        
        // and close the one requested
        if(!avClose.getTopComponent().close())
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

        return Module.dbgHL();
    }

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
            installed.tvTag = String.valueOf(w_num);
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
    private static final Map<JTextComponent, MyHl> hlMap
            = new WeakHashMap<JTextComponent, MyHl>();

    private static class MyHl {
        WeakReference<VisualSelectHighlighter> visualRef;
        WeakReference<SearchResultsHighlighter> searchRef;

        static MyHl get(JTextComponent ep) {
            MyHl myHl = hlMap.get(ep);
            if(myHl == null) {
                myHl = new MyHl();
            }
            hlMap.put(ep, myHl);
            return myHl;
        }

        static void putVisual(JTextComponent ep, VisualSelectHighlighter visual) {
            synchronized(hlMap) {
                MyHl myHl = get(ep);
                myHl.visualRef
                        = new WeakReference<VisualSelectHighlighter>(visual);
            }
        }

        static VisualSelectHighlighter getVisual(JTextComponent ep) {
            synchronized(hlMap) {
                WeakReference<VisualSelectHighlighter> ref;
                ref = get(ep).visualRef;
                return ref != null ? ref.get() : null;
            }
        }

        static void putSearch(JTextComponent ep, SearchResultsHighlighter search) {
            synchronized(hlMap) {
                MyHl myHl = get(ep);
                myHl.searchRef
                        = new WeakReference<SearchResultsHighlighter>(search);
            }
        }

        static SearchResultsHighlighter getSearch(JTextComponent ep) {
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
            if(Module.dbgHL())
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
        protected int[] getBlocks(NbTextView tv, int startOffset, int endOffset)
        {
            // NEEDSWORK: why is tv in the signature? This is in tv.
            // ???? assert tv == this
            return  tv.w_buffer.getVisualSelectBlocks(
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
        
        private ColorOption searchColorOption;
        private ColorOption searchFgColorOption;
        private Color searchColor;
        private Color searchFgColor;
        private AttributeSet searchAttribs;

        /** Creates a new instance of TextSearchHighlighter */
        public SearchResultsHighlighter(String name, JEditorPane ep) {
            super(name, ep);

            searchColorOption
                    = (ColorOption)Options.getOption(Options.searchColor);
            searchFgColorOption
                    = (ColorOption)Options.getOption(Options.searchFgColor);

            MyHl.putSearch(ep, this);
            if(dbgHL(this))
                System.err.println(displayName() + " putSearch");

            NbTextView tv = getTv();
            if(tv != null) {
                tv.hookupHighlighter(name, this);
            }
        }

        @Override
        protected int[] getBlocks(NbTextView tv, int startOffset, int endOffset) {
            return tv.w_buffer.getHighlightSearchBlocks(startOffset,
                                                        endOffset);
        }

        @Override
        protected AttributeSet getAttribs() {
            // NEEDSWORK: could listen to option change.
            // NEEDSWORK: using "!=" in following instead of '.equals'
            // to avoid messy 'null' handling (listener would fix that)
            if(searchColorOption.getColor() != searchColor
                    || searchFgColorOption.getColor() != searchFgColor) {
                searchColor = searchColorOption.getColor();
                searchFgColor = searchFgColorOption.getColor();
                List<Object> l = new ArrayList<Object>();
                l.add(StyleConstants.Background);
                l.add(searchColor);
                if(searchFgColor != null) {
                    l.add(StyleConstants.Foreground);
                    l.add(searchFgColor);
                }
                searchAttribs = AttributesUtilities.createImmutable(
                        l.toArray());
            }
            return searchAttribs;
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

            // Let the bag update first... (it's doc listener)
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
            NbTextView tv = (NbTextView)ViManager.getFactory()
                                        .getTextView((ep));
            return tv;
        }

        protected void discard() {
            bag.removeHighlightsChangeListener(this);
            bag.discard();
            isDiscarded = true;
        }

        protected void goIdle() {
            bag.clear();
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
                ViBuffer buf = tv.w_buffer;
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
                ViBuffer buf = tv.w_buffer;
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
                        //  Issue 114642
                        int bug = 0; //-1;
                        if(startOffset == endOffset)
                            bug = 0;
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
