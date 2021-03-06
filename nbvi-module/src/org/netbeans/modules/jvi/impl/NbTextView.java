package org.netbeans.modules.jvi.impl;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.AttributeSet;
import javax.swing.text.Caret;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import javax.swing.text.StyleConstants;

import org.netbeans.api.editor.fold.Fold;
import org.netbeans.api.editor.fold.FoldHierarchy;
import org.netbeans.api.editor.fold.FoldUtilities;
import org.netbeans.api.editor.settings.AttributesUtilities;
import org.netbeans.api.editor.settings.SimpleValueNames;
import org.netbeans.editor.BaseCaret;
import org.netbeans.editor.BaseKit;
import org.netbeans.modules.editor.NbEditorKit;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.jvi.FsAct;
import org.netbeans.modules.jvi.Module;
import org.netbeans.modules.jvi.reflect.NbWindows;
import org.netbeans.modules.jvi.spi.WindowsProvider;
import org.netbeans.modules.jvi.spi.WindowsProvider.EditorHandle;
import org.netbeans.modules.jvi.spi.WindowsProvider.EditorSizerArgs;
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
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import com.raelity.jvi.ViAppView;
import com.raelity.jvi.ViBuffer;
import com.raelity.jvi.ViFPOS;
import com.raelity.jvi.ViStatusDisplay;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.ViTextView.TABOP;
import com.raelity.jvi.ViTextView.WMOP;
import com.raelity.jvi.ViWindowNavigator;
import com.raelity.jvi.ViWindowNavigator.SplitterChildNode;
import com.raelity.jvi.ViWindowNavigator.SplitterNode;
import com.raelity.jvi.core.Buffer;
import com.raelity.jvi.core.Edit;
import com.raelity.jvi.core.G;
import com.raelity.jvi.core.Misc;
import com.raelity.jvi.core.Msg;
import com.raelity.jvi.core.Options;
import com.raelity.jvi.core.Util;
import com.raelity.jvi.manager.AppViews;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.options.ColorOption;
import com.raelity.jvi.options.DebugOption;
import com.raelity.jvi.swing.SwingTextView;
import com.raelity.text.TextUtil.MySegment;

import static com.raelity.jvi.core.lib.Constants.*;

/**
 * Pretty much the SwingTextView used for standard swing.
 */
public class NbTextView extends SwingTextView
{
    private static final WindowsProvider wp = Module.getWindowsProvider();
    private static final boolean diffSidebarDirty = ViManager.getHackFlag(Module.HACK_DIFF_SIDEBAR_DIRTY);
    private static final boolean hackScroll = ViManager.getHackFlag(Module.HACK_SCROLL);
    final FoldOps foldOps;
    final FoldHierarchy fh;

    NbTextView(JEditorPane editorPane) {
        super(editorPane);
        statusDisplay = new NbStatusDisplay(this);
        foldOps = new FoldOps(this);
        fh = FoldHierarchy.get(editorPane);
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

        Map<String, Object> map = new HashMap<String, Object>();
        map.put(SimpleValueNames.LINE_NUMBER_VISIBLE, w_p_nu);
        map.put(SimpleValueNames.NON_PRINTABLE_CHARACTERS_VISIBLE, w_p_list);
        setWrapPref(map);
        NbJviPrefs.putPrefs(map, this, null);
    }
    
    //
    // The viOptionBag interface
    //
    
    @Override
    public void viOptionSet(ViTextView tv, String name) {
        super.viOptionSet(tv, name);

        assert this == tv;

        Map<String, Object> map = new HashMap<String, Object>();
        if("w_p_nu".equals(name)) {
            map.put(SimpleValueNames.LINE_NUMBER_VISIBLE, w_p_nu);
        } else if("w_p_list".equals(name)) {
            map.put(SimpleValueNames.NON_PRINTABLE_CHARACTERS_VISIBLE, w_p_list);
        } else if("w_p_wrap".equals(name)) {
            setWrapPref(map);
        } else if("w_p_lbr".equals(name)) {
            setWrapPref(map);
        }
        NbJviPrefs.putPrefs(map, this, null);
    }

    private void setWrapPref(Map<String, Object> map)
    {
        String s = !w_p_wrap ? "none" : w_p_lbr ? "words" : "chars";
        map.put(SimpleValueNames.TEXT_LINE_WRAP, s);
    }

    //
    // HACK
    //
    @Override
    protected void endVisualBell()
    {
        if(!diffSidebarDirty)
            return;

        Container parent = editorPane.getParent();
        while(parent != null) {
            if(parent instanceof JScrollPane) {
                break;
            }
            parent = parent.getParent();
        }

        // Workaround a bug in the diff sidebar.
        // If there is a Brace Match Tooltip,
        // and you enter escape (dismiss the tooltip)
        // then the diff side bar redraws using the
        // editor's background color (which is currently inverted).
        // Seems like a timing thing that we always catch the wrong background...
        if(parent != null) {
            Component c = Module.findComponentByName(parent, "DiffSidebar");
            if(c != null)
                c.repaint();
        }
    }

    //
    // The viTextView interface
    //

    class NbWCursor extends WCursor {
        @Override
        public void set(int offset)
        {
            Caret c = editorPane.getCaret();
            // Do not open fold (if NB caret)
            if(c instanceof BaseCaret)
                ((BaseCaret)c).setDot(offset, false);
            else
                c.setDot(offset);
        }
    }

    @Override
    public ViFPOS createWCursor()
    {
        return w_cursor == null ? new NbWCursor() : null;
    }

    @Override
    public void setSelection( int dot, int mark )
    {
        w_cursor.set(mark);
        editorPane.getCaret().moveDot(dot);
    }

    @Override
    public void clearSelection()
    {
        w_cursor.set(w_cursor.getOffset());
    }
    
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
        if(hackScroll)
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
    public void foldOperation(FOLDOP op, int start, int end, boolean isVisual) {
        foldOps.foldOperation(op, start, end, isVisual);
    }

    @Override
    public void foldOpenCursor(int line)
    {
        foldOps.makeVisible(line);
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
        boolean error = false;

        if(count == 0) {
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
                ActionEvent e = new ActionEvent(
                        getEditor(), ActionEvent.ACTION_PERFORMED, "");
                Module.execFileSystemAction(fsAct, e);
            } else
                error = true;
        } else {
            NbAppView av = (NbAppView)getAppView();
            if(av == null) {
                error = true;
            } else {
                TopComponent tc = av.getTopComponent();
                TopComponent tcNext = null;
                Mode m = WindowManager.getDefault().findMode(tc);
                TopComponent[] tcs
                        = WindowManager.getDefault().getOpenedTopComponents(m);
                if(op == TABOP.NEXT_TAB) {
                    if(count > tcs.length)
                        error = true;
                    else
                        tcNext = tcs[count - 1];
                } else {
                    int idx = (Arrays.asList(tcs).indexOf(tc) - count)
                                % tcs.length;
                    if(idx < 0)
                        idx += tcs.length;
                    tcNext = tcs[idx];
                }
                if(tcNext != null) {
                    tcActivate(tcNext);
                }
            }
        }

        if(error)
            Util.beep_flush();
    }
    
    //////////////////////////////////////////////////////////////////////
    //
    // Widow manipulation operations
    //
    // win_split, win_clone, win_move, ...
    //

    /**
     * The idea behind the run event q is to let the top component
     * become fully active and focused. Then if there are keys queued up
     * they can operate on the newly focused editor.
     */
    static void tcActivate(TopComponent tc)
    {
        tc.requestActive();
        ViManager.requestCharBreakPauseRunEventQueue(3);
    }

    static double getTargetWeight(double n, Orientation orientation,
                                  EditorSizerArgs era)
    {
        return wp.getWeight(n, orientation.name(), era);
    }

    /**
     * Calculate initial state for a split:
     *          idxToSplit, originalWeights, ea (equalalways)
     */
    private static SplitParams doEditorSplit1(SplitterNode sn)
    {
        SplitParams sp = new SplitParams();

        if(sn == null)
            return sp;

        SplitterChildNode[] children = sn.getChildren();
        double[] weights = new double[children.length];
        double full = getDim(sn.getOrientation(), sn.getComponent());

        for(int i = 0; i < children.length; i++) {
            SplitterChildNode scn = children[i];
            Component c = scn.getComponent();
            if(scn.isEditor())
                c = wp.findModePanel(scn.getComponent());
            weights[i] = getDim(sn.getOrientation(), c) / full;
        }

        sp.idxToSplit = sn.getTargetIndex();
        sp.originalWeights = weights;

        return sp;
    }

    /**
     * A new mode has been created and tc installed in it
     * or a mode is being resized.
     * Set the new weights and adjust other weights.
     * Pay attention to equalalways option.
     * <p/>
     * The SplitterNode may be null and if null it is inferred.
     * When specified, split that node; typically a resize opertaion.
     * <p/>
     * NEEDSWORK: split this into two parts
     *                1 - setup idxOf{New,Other}, starting values for newWeights
     *                2 - adjust the weights and change the splitter
     *            Formalize how SplitParams controls/interacts...
     */
    private static void doEditorSplit2(TopComponent tc,
                                       SplitParams sp,
                                       SplitterNode sn)
    {
        Set<NbAppView> tcSet = NbAppView.fetchAvFromTC(tc);
        if(tcSet.isEmpty())
            return;
        NbAppView av = tcSet.iterator().next();

        // determine weight index of new editor and prev editor
        if(sn == null) {
            ViWindowNavigator nav = ViManager.getFactory().getWindowNavigator();
            sn = nav.getParentSplitter(av);
        }
        int idxOfNew;
        int idxOfOther;
        double[] newWeights;
        if(sp.resizeOperation) {
            // A new mode was not created, changing an existing mode
            newWeights = sp.originalWeights;
            idxOfNew = sp.idxToSplit;
            idxOfOther = idxOfNew + 1;
            // NEEDSWORK: could use splitbottom/splitright options to select other
            //            but this is what vim does
            // Take weight from next, but if adjusting last take from prev
            if(idxOfOther >= newWeights.length)
                idxOfOther = idxOfNew - 1;
            // put all the weight into idxOfOther, so looks like a split
            newWeights[idxOfOther] += newWeights[idxOfNew];
            newWeights[idxOfNew] = 0D;
        } else if(sp.operateOnCurrent) { // NEEDSWORK: this belongs in win_move?
            // toss the old weight info; get current info
            SplitParams sp2 = doEditorSplit1(sn);
            newWeights = sp2.originalWeights;
            idxOfNew = sp2.idxToSplit;
            // idxOfNew is either first or last; make idxOfOther it's neighbor
            // note: idxOfOther may not be used depending on ...
            idxOfOther = idxOfNew == 0 ? 1 : idxOfNew - 1;
        } else {
            if(sn.getChildCount() != sp.originalWeights.length + 1)
                return;
            idxOfNew = sn.getTargetIndex();
            assert idxOfNew == sp.idxToSplit || idxOfNew == sp.idxToSplit + 1;
            idxOfOther = idxOfNew == sp.idxToSplit ? idxOfNew + 1 : idxOfNew - 1;

            // make room for the weight of the new editor; back into an array
            List<Double> wl
                    = new ArrayList<Double>(sp.originalWeights.length + 1);
            for(double d : sp.originalWeights) {
                wl.add(d);
            }
            wl.add(idxOfNew, 0D);
            newWeights = new double[wl.size()];
            for(int i = 0; i < newWeights.length; i++) {
                newWeights[i] = wl.get(i);
            }
        }

        if(sp.addOuter && !sp.sizeSpecified) {
            // size not specified and doing addOuter doesn't split anything
            // so treat it like equal always
            sp.ea = true;
        } else if(sp.sizeSpecified) {
            // in vim if size specified then only change size of current window
            sp.ea = false;
        }

        double targetWeight = sp.targetWeight;

        // NEEDSWORK: when !ea and request bigger than current
        //            should take extra space from window
        //            as specified by splitbottom/splitright options.
        if(sp.ea) {
            // distribute evenly
            double distributedWeight = 1D / newWeights.length;
            for(int i = 0; i < newWeights.length; i++) {
                newWeights[i] = distributedWeight;
            }
        } else {
            if(sp.adjustOthersProportionally) {
                // keep target weight, keep existing proportions on the rest
                newWeights[idxOfNew] = targetWeight;
                double sumOtherWeights = 0;
                for(int i = 0; i < newWeights.length; i++) {
                    if(i != idxOfNew)
                        sumOtherWeights += newWeights[i];
                }
                double factor = (1 - newWeights[idxOfNew]) / sumOtherWeights;
                for(int i = 0; i < newWeights.length; i++) {
                    if(i != idxOfNew)
                        newWeights[i] *= factor;
                }
            } else if(targetWeight < newWeights[idxOfOther] - .03) {
                // If no size was specified, split current in half
                if(targetWeight == 0D)
                    targetWeight = newWeights[idxOfOther] / 2;
                // take space with current editor view
                newWeights[idxOfNew] = targetWeight;
                newWeights[idxOfOther] -= targetWeight;
            } else {
                //
                // asking for more weight than editor being split has.
                //
                // don't let it take more than 90% of everything
                if(targetWeight > .9)
                    targetWeight = .9;
                // take what weight asked for, evenly split the rest
                newWeights[idxOfNew] = targetWeight;
                double distributedWeight
                        = (1 - targetWeight)/(newWeights.length - 1);
                for(int i = 0; i < newWeights.length; i++) {
                    if(i != idxOfNew)
                        newWeights[i] = distributedWeight;
                }
            }
        }
        wp.setWeights(sn.getComponent(), newWeights);
    }

    private static class SplitParams {
        boolean ea;
        int idxToSplit;
        double[] originalWeights;

        double targetWeight;    // of new
        boolean sizeSpecified;
        boolean addOuter;       // NEEDSWORK: rename
        boolean operateOnCurrent;
        boolean adjustOthersProportionally;
        boolean resizeOperation;

        SplitParams()
        {
            ea = G.p_ea();
            // initialize as 1 thing, gets full weight, idxToSplit is 0
            originalWeights = new double[] { 1D };
        }
    }

    private static int getDim(Orientation orientation, Component c)
    {
        return orientation == Orientation.LEFT_RIGHT
                ? c.getWidth() : c.getHeight();
    }

    private static void finishSplitLater(final TopComponent tc,
                                         final SplitParams sp,
                                         final SplitterNode sn)
    {
        ViManager.nInvokeLater(1, new Runnable() {
            @Override
            public void run()
            {
                doEditorSplit2(tc, sp, sn);
            }
        });
    }

    private void win_split(Direction dir, int n) {
        NbAppView av = (NbAppView)getAppView();
        if(av == null) {
            G.dbgEditorActivation().println("win_split: NULL av");
            return;
        }

        ViWindowNavigator nav = ViManager.getFactory().getWindowNavigator();
        TopComponent clone = tcClone();

        SplitterNode sn = nav.getParentSplitter(av, dir.getOrientation());
        double targetWeight = getTargetWeight(n, dir.getOrientation(),
                                              new SizerArgs(sn));
        clone.open();
        SplitParams sp = doEditorSplit1(sn);
        sp.targetWeight = targetWeight;
        sp.sizeSpecified = n != 0;

        // create a new mode
        Mode m = WindowManager.getDefault().findMode(av.getTopComponent());
        wp.addModeOnSide(m, dir.getSplitSide(), new EH(clone));

        tcActivate(clone);
        finishSplitLater(clone, sp, null);
    }

    @Override
    public void win_split(Direction dir, int n, ViAppView _avToMove) {
        if(_avToMove == null) {
            // produce two views of the current file
            win_split(dir, n);
            return;
        }

        NbAppView avToMove = (NbAppView)_avToMove;
        NbAppView av = (NbAppView)getAppView();
        if(av == null) {
            G.dbgEditorActivation().println("win_split: NULL av");
            return;
        }

        // calculate targetWeight
        ViWindowNavigator nav = ViManager.getFactory().getWindowNavigator();
        SplitterNode sn = nav.getParentSplitter(av, dir.getOrientation());
        double targetWeight = getTargetWeight(n, dir.getOrientation(),
                                              new SizerArgs(sn));

        SplitParams sp = doEditorSplit1(sn);
        sp.targetWeight = targetWeight;
        sp.sizeSpecified = n != 0;

        // create a new mode
        Mode m = WindowManager.getDefault().findMode(av.getTopComponent());
        // and put the av into the new mode
        wp.addModeOnSide(m, dir.getSplitSide(), new EH(avToMove));

        tcActivate(avToMove.getTopComponent());
        finishSplitLater(avToMove.getTopComponent(), sp, null);
    }

    private TopComponent tcClone()
    {
        NbAppView av = (NbAppView)getAppView();
        TopComponent tc = av.getTopComponent();
        TopComponent clone = null;
        if(tc instanceof TopComponent.Cloneable) {
            clone = ((TopComponent.Cloneable)tc).cloneComponent();
        }
        return clone;
    }

    /**
     * Essentially taken from
     * core.windows/src/org/netbeans/core/windows/actions/ActionUtils
     * @param tc
     */
    @Override
    public void win_clone()
    {
        TopComponent tc = ((NbAppView)getAppView()).getTopComponent();
        TopComponent clone = tcClone();
        if(clone != null) {
            int openIndex = -1;
                        // original had: if (null != m) ....
            Mode m = WindowManager.getDefault().findMode(tc);
            TopComponent[] tcs = m.getTopComponents();
            // put the clone *before* the original in tab order,
            // then if you move the clone, the original will be selected
            // in the other window
            for( int i=0; i<tcs.length; i++ ) {
                if( tcs[i] == tc ) {
                    openIndex = i;
                    break;
                }
            }

            if( openIndex >= tcs.length )
                openIndex = -1;
            if( openIndex >= 0 ) {
                clone.openAtTabPosition(openIndex);
            } else {
                clone.open();
            }
            tcActivate(clone);
        }
    }

    @Override
    public void win_move(Direction dir, int n)
    {
        NbAppView av = (NbAppView)getAppView();
        if(av == null) {
            G.dbgEditorActivation().println("win_move: NULL av");
            return;
        }
        TopComponent tc = av.getTopComponent();
        Mode m = WindowManager.getDefault().findMode(tc);

        ViWindowNavigator nav = ViManager.getFactory().getWindowNavigator();

        // "true" means target must touch this window
        NbAppView avTarget = (NbAppView)nav.getTarget(dir, av, 1, true);

        if(avTarget != null) {
            // move the av's tc to the avTarget's mode
            TopComponent tcTarget = avTarget.getTopComponent();
            if(tcTarget != null) {
                m = WindowManager.getDefault().findMode(tcTarget);
                if(WindowManager.getDefault().isEditorMode(m)) {
                    wp.move(m, new EH(tc));
                } else
                    Msg.smsg("\"" + m.getSelectedTopComponent().getName()
                            + "\" target is not in an \"editor mode\"");

            }
        } else {
            //
            // Might do either addModeOnSide or addModeAround
            // Consider creating vertical splitter (move up/down jVi command);
            // if the window does not have a left/right neighbor which
            // it toches, then do a split.
            //

            // when addOuter false, same as win_split
            boolean addOuter
                = nav.getTarget(dir.getClockwise(), av, 1, true) != null
                    || nav.getTarget(dir.getClockwise().getOpposite(),
                                     av, 1, true) != null;

            SplitterNode sn = addOuter
                    ? nav.getRootSplitter(av, dir.getOrientation())
                    : nav.getParentSplitter(av, dir.getOrientation());
            double targetWeight = getTargetWeight(
                    n, dir.getOrientation(), new SizerArgs(sn));
            SplitParams sp = doEditorSplit1(sn);
            sp.targetWeight = targetWeight;
            sp.sizeSpecified = n != 0;

            if(addOuter) {
                wp.addModeAround(m, dir.getSplitSide(), new EH(tc));
                sp.addOuter = true;
                sp.operateOnCurrent = true;
                sp.adjustOthersProportionally = true;
            } else
                wp.addModeOnSide(m, dir.getSplitSide(), new EH(tc));

            finishSplitLater(tc, sp, null);
        }
    }

    @Override
    public void win_size(SIZOP op, Orientation orientation, int n)
    {
        if(getViewport() == null)
            return;

        if(n == 0)
            n = 1;
        double size = orientation == Orientation.UP_DOWN
                ? getViewport().getExtentSize().height/getLineHeight(1, 0)
                : getViewport().getExtentSize().width/getMaxCharWidth();
        if(op == SIZOP.ADJUST)
            size += n;
        else if(op == SIZOP.SET)
            size = n;
        if(size < 1)
            size = 1;

        NbAppView av = (NbAppView)getAppView();
        ViWindowNavigator nav = ViManager.getFactory().getWindowNavigator();
        SplitterNode sn = nav.getParentSplitter(av);

        // if can't resize in the direction, then bail
        // NEEDSWORK: find a splitter to adjust
        // if(op != SIZOP.SAME && orientation != sn.getOrientation())
        //     return;
        if(op != SIZOP.SAME && orientation != sn.getOrientation()) {
            sn = nav.getAncestorSplitter(av, orientation);
        }

        SplitParams sp = doEditorSplit1(sn);
        sp.resizeOperation = true;
        //sp.adjustOthersProportionally = true;
        if(op == SIZOP.SAME) {
            sp.targetWeight = 0;
            sp.ea = true;
        } else {
            // use the Component SizeArgs constructor
            // becuase this is not a split
            sp.targetWeight = getTargetWeight(size, orientation,
                                              new SizerArgs(sn.getComponent()));
            sp.sizeSpecified = true;
            sp.ea = false; // like vim
        }

        doEditorSplit2(av.getTopComponent(), sp, sn);
    }

    @Override
    public void win_quit() {
        // if split, close this half; otherwise close view
        win_close(false);
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

        if(avClose == null || avClose.getTopComponent() == null)
            return;
        
        // activate the previously active TC
        NbAppView avPrev = (NbAppView)AppViews.getMruAppView(1);
        TopComponent prevTC = null;
        if(avPrev != null)
                prevTC = avPrev.getTopComponent();
        if(prevTC != null)
            tcActivate(prevTC);
        
        // and close the requested TC
        if(!avClose.getTopComponent().close())
            Msg.emsg(getBuffer().getDisplayFileName() + " not closed");
    }

    public static Component findModePanel(Component c)
    {
        return NbWindows.findModePanel(c);
    }

    private static final class EH implements EditorHandle
    {
        final private TopComponent tc;

        EH(NbAppView av)
        {
            this(av.getTopComponent());
        }

        EH(TopComponent tc)
        {
            this.tc = tc;
        }


        @Override
        public TopComponent getTC()
        {
            return tc;
        }

        @Override
        public Component getEd()
        {
            // Might need something like this if split of an editor support in NB
            return null; // getEditor()
        }
    }

    private final class SizerArgs implements  EditorSizerArgs
    {
        final private Dimension resizeTargetContainer;

        private SizerArgs(Dimension resizeTargetContainer)
        {
            this.resizeTargetContainer = resizeTargetContainer;
        }

        /**
         * NOTE: when used for resize, resizeTargetContainer may be null
         * @param resizeTargetContainer
         */
        SizerArgs(Component resizeTargetContainer)
        {
            this(resizeTargetContainer == null
                        ? null : resizeTargetContainer.getSize());
        }

        /**
         * When splitter node is specified assume that this is part of a split;
         * and that means another divider will be in there.
         * So take away a few pixels from the container height
         * to compensate.
         */
        SizerArgs(SplitterNode sn)
        {
            this(new Dimension(sn.getComponent().getSize().width,
                               sn.getComponent().getSize().height
                                - 4));
        }

        @Override
        public Component getEditorToSplit()
        {
            return getEditor();
        }

        @Override
        public Dimension getResizeTargetContainer()
        {
            if(resizeTargetContainer != null)
                return resizeTargetContainer;
            return findModePanel(getEditorToSplit()).getSize();
        }

        @Override
        public double charHeight()
        {
            return NbTextView.this.getLineHeight(1, 0);
        }

        @Override
        public double charWidth()
        {
            return NbTextView.this.getMaxCharWidth();
        }

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

    private static DebugOption dbgHL() {
        return Module.dbgHL();
    }
    private static boolean dbgHL(BlocksHighlighter h) {
        // filter out stuff if wanted
        //if(h.name == VISUAL_MODE_LAYER)
        //    return false;
        //if(h.name == SEARCH_RESULTS_LAYER)
        //    return false;

        return true;
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
            if(dbgHL(discarded) && dbgHL().getBoolean())
                dbgHL().println(discarded.displayName()
                        + ": hookup discard");
            discarded.discard();
        }
        if(installed != null) {
            installed.tvTag = String.valueOf(w_num);
            if(dbgHL(installed) && dbgHL().getBoolean())
                dbgHL().println(installed.displayName()
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

        @Override
        public HighlightsLayer[] createLayers(Context context) {
            ArrayList<HighlightsLayer> layers
                    = new ArrayList<HighlightsLayer>();

            FileObject fo = NbEditorUtilities.getFileObject(
                    context.getDocument());
            if(dbgHL().getBoolean())
                dbgHL().println("Highlight Factory: "
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
        private final ColorOption selectColorOption;
        private final ColorOption selectFgColorOption;
        private Color selectColor;
        private Color selectFgColor;
        private AttributeSet selectAttribs;

        @SuppressWarnings("LeakingThisInConstructor")
        VisualSelectHighlighter(String name, JEditorPane ep) {
            super(name, ep);

            selectColorOption
                    = (ColorOption)Options.getOption(Options.selectColor);
            selectFgColorOption
                    = (ColorOption)Options.getOption(Options.selectFgColor);

            MyHl.putVisual(ep, this);
            if(dbgHL(this) && dbgHL().getBoolean())
                dbgHL().println(displayName() + " putVisual");

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

        @Override
        protected boolean isEnabled() {
            return G.VIsual_active() || G.drawSavedVisualBounds();
        }
    }

    // Take a look at TextSearchHighlighting.java in
    // lib2/src/org/netbeans/modules/editor/lib2/highlighting/
    private static class SearchResultsHighlighter
            //extends AbstractHighlightsContainer
            extends BlocksHighlighter
            implements HighlightsChangeListener,
                       DocumentListener {
        
        private final ColorOption searchColorOption;
        private final ColorOption searchFgColorOption;
        private Color searchColor;
        private Color searchFgColor;
        private AttributeSet searchAttribs;

        /** Creates a new instance of TextSearchHighlighter */
        @SuppressWarnings("LeakingThisInConstructor")
        public SearchResultsHighlighter(String name, JEditorPane ep) {
            super(name, ep);

            searchColorOption
                    = (ColorOption)Options.getOption(Options.searchColor);
            searchFgColorOption
                    = (ColorOption)Options.getOption(Options.searchFgColor);

            MyHl.putSearch(ep, this);
            if(dbgHL(this) && dbgHL().getBoolean())
                dbgHL().println(displayName() + " putSearch");

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

        @Override
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
        private final OffsetsBag bag;
        protected final JEditorPane ep;
        protected final Document document;
        protected final String name;
        protected boolean isDiscarded;
        protected int mygen;
        protected static int gen;
        String tvTag = "";

        @SuppressWarnings("LeakingThisInConstructor")
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
            if(dbgHL(this) && dbgHL().getBoolean())
                dbgHL().println(displayName() + " BlocksHighlighter reset:");
            fillInTheBag();
        }

        @Override
        public void highlightChanged(HighlightsChangeEvent event) {
            if(dbgHL(this) && dbgHL().getBoolean())
                dbgHL().println(displayName() + " highlightChanged: "
                    + event.getStartOffset() + "," + event.getEndOffset());
            fireHighlightsChange(event.getStartOffset(), event.getEndOffset());
        }

        @Override
        public HighlightsSequence getHighlights(int startOffset, int endOffset) {
            if(dbgHL(this) && dbgHL().getBoolean()) {
                dbgHL().println(displayName() + " getHighlights: "
                                   + startOffset + "," + endOffset);
                dumpHLSeq(displayName(),
                          bag.getHighlights(startOffset, endOffset));
            }
            return bag.getHighlights(startOffset, endOffset);
        }

        @Override
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

        @Override
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

        @Override
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
                @Override
                public void run() {
                    if(isDiscarded)
                        return;
                    OffsetsBag newBag = new OffsetsBag(document);
                    
                    NbTextView tv = getTv();
                    if (isEnabled() && tv != null) {

                        int [] blocks = getBlocks(tv, startOffset, endOffset);
                        if(dbgHL(BlocksHighlighter.this) && dbgHL().getBoolean())
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
        StringBuilder sb = new StringBuilder();
        sb.append(tag).append(" seq:");
        if(HighlightsSequence.EMPTY.equals(seq)) {
            sb.append(" EMPTY");
        }
        while(seq.moveNext()) {
            sb.append(" {").append(seq.getStartOffset())
                    .append(',').append(seq.getEndOffset()).append('}');
        }
        dbgHL().println(sb.toString());
    }

}
