/*
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * The Original Code is jvi - vi editor clone.
 *
 * The Initial Developer of the Original Code is Ernie Rael.
 * Portions created by Ernie Rael are
 * Copyright (C) 2000-2010 Ernie Rael.  All Rights Reserved.
 *
 * Contributor(s): Ernie Rael <err@raelity.com>
 */
package org.netbeans.modules.jvi.impl;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Timer;

import org.netbeans.api.editor.fold.Fold;
import org.netbeans.api.editor.fold.FoldHierarchy;
import org.netbeans.api.editor.fold.FoldHierarchyEvent;
import org.netbeans.api.editor.fold.FoldStateChange;
import org.netbeans.api.editor.fold.FoldUtilities;
import org.netbeans.modules.jvi.Module;

import com.raelity.jvi.ViTextView.FOLDOP;
import com.raelity.jvi.core.G;
import com.raelity.jvi.core.Util;
import com.raelity.jvi.lib.MutableInt;
import com.raelity.jvi.manager.Scheduler;
import com.raelity.jvi.manager.ViManager;


/**
 * Operations on folds. Delegated to here from NbTextView. Could be there
 * but file is big and takes time for reparsing while editing.
 *
 * The NetBeans model and behavior for fold ops is different from vim.
 * For example, in vim 'zc' command collapses an expanded encolsing fold even
 * when sitting on a closed fold.
 *
 * @author Ernie Rael <err at raelity.com>
 */
public class FoldOps {
    private final NbTextView tv;
    private final FoldHierarchy fh;
    // use WeakHashMap ???
    private final Map<Fold, FoldLines> mapFolds;
    private static final boolean foldAsync
            = ViManager.getHackFlag(Module.HACK_FOLD_ASYNC);
    private static Timer needScrollTimer;

    enum SEARCH { COLLAPSED, EXPANDED, DEEPEST };

    public FoldOps(NbTextView tv)
    {
        this.tv = tv;
        fh = FoldHierarchy.get(tv.getEditor());
        this.mapFolds = new HashMap<Fold, FoldLines>();
    }

    public void foldOperation(FOLDOP op, int arg1, int arg2, boolean isVisual) {
        int line1, line2;
        int dot;

        switch(op) {
            case CLOSE:
                if(isVisual) {
                    collapseRange(arg1, arg2);
                } else {
                    collapseFold(arg1);
                }
                break;
            case CLOSE_R:
                if(isVisual) {
                    line1 = arg1;
                    line2 = arg2;
                } else {
                    line1 = tv.w_cursor.getLine();
                    line2 = line1;
                }
                opRecursively(line1, line2, SEARCH.EXPANDED);
                break;
            case OPEN:
                if(isVisual) {
                    expandRange(arg1, arg2);
                } else {
                    expandFold(arg1);
                }
                break;
            case OPEN_R:
                if(isVisual) {
                    line1 = arg1;
                    line2 = arg2;
                } else {
                    line1 = tv.w_cursor.getLine();
                    MutableInt pDocFirst = new MutableInt();
                    MutableInt pDocLast = new MutableInt();
                    if(tv.getLineMap().hasFolding(line1, pDocFirst, pDocLast)) {
                        line1 = pDocFirst.getValue();
                        line2 = pDocLast.getValue();
                    } else
                        line2 = line1;
                }
                opRecursively(line1, line2, SEARCH.COLLAPSED);
                break;
            case CLOSE_ALL:
                dot = tv.w_cursor.getOffset();
                FoldUtilities.collapseAll(fh);
                setCaretAfterCollapse(dot);
                break;
            case OPEN_ALL:
                dot = tv.w_cursor.getOffset();
                FoldUtilities.expandAll(fh);
                setCaretAfterExpand(dot);
                break;
            default:
                Util.beep_flush();
                break;
        }
    }

    /**
     * NB-7.4 requires the invokeLater after a fold close.
     */
    private void setCaretAfterCollapse(final int offset) {
        if(foldAsync) {
            EventQueue.invokeLater(new Runnable() {
                @Override public void run() {
                    tv.w_cursor.set(offset);
                }
            });
        } else
            tv.w_cursor.set(offset);
    }

    /**
     * May take a while for view to be ready for scrolling after and expand.
     * Any version of NB. With only a single CPU need to use a timer ;-)
     */
    private void setCaretAfterExpand(final int offset) {
        tv.w_cursor.set(offset);

        class ScrollTimerAction implements ActionListener {
            private final boolean doScroll;
            public ScrollTimerAction(boolean doScroll) {
                this.doScroll = doScroll;
            }
            @Override public void actionPerformed(ActionEvent e) {
                if(doScroll)
                    G.scrollToLine(tv, tv.w_cursor.getLine());
                finishScrollTimer();
            }
        }

        finishScrollTimer();
        needScrollTimer = new Timer(100, new ScrollTimerAction(true));
        needScrollTimer.setRepeats(false);
        needScrollTimer.start();
        // If a key gets pressed, kill the timer let the user handle things
        Scheduler.putKeyStrokeTodo(new ScrollTimerAction(false));
    }
    private void finishScrollTimer() {
        if(needScrollTimer != null) {
            needScrollTimer.stop();
            needScrollTimer = null;
        }
    }

    private void collapseFold(final int count)
    {
        runFoldOp(new Runnable() {
            @Override public void run() {
                int dot = tv.w_cursor.getOffset();

                int line = tv.getBuffer().getLineNumber(dot);

                for(int i = count; i > 0; i--) {
                    final Collection<Fold> folds = jViFindLineFolds(
                            line, SEARCH.EXPANDED);
                    if(folds.isEmpty())
                        break;
                    fh.collapse(folds);
                }
                setCaretAfterCollapse(dot);
            }
        });
    }

    private void expandFold(final int count)
    {
        runFoldOp(new Runnable() {
            @Override public void run() {
                int dot = tv.w_cursor.getOffset();

                final int line = tv.getBuffer().getLineNumber(dot);

                for(int i = count; i > 0; i--) {
                    final Collection<Fold> folds = jViFindLineFolds(
                            line, SEARCH.COLLAPSED);
                    if(folds.isEmpty())
                        break;
                    fh.expand(folds);
                }
                setCaretAfterExpand(dot);
            }
        });
    }

    /**
     * This is the zc command for a visual selection. Do it in two passes.
     * First collapse highest level fully contained folds. Then handle partially
     * selected folds as vim does.
     *
     * The results in vim are implementation dependant and not obvious from the
     * description of the zc command. vim starts at the beginning of the
     * selection and closes the fold under it; then checks the location after
     * what it just folded.
     */

    private void collapseRange(final int rangeStartLine, final int rangeEndLine)
    {
        runFoldOp(new Runnable() {
            @Override public void run() {
                int dot = tv.w_cursor.getOffset();

                final Set<Fold> initialFolds = new HashSet<Fold>();
                collectFullyContainedExpanded(fh.getRootFold(),
                                  rangeStartLine, rangeEndLine, initialFolds);
                List<Fold> folds = collectPartiallyContainedExpanded(
                        rangeStartLine, rangeEndLine, initialFolds);
                fh.collapse(folds);
                setCaretAfterCollapse(dot);
            }
        });
    }

    private void collectFullyContainedExpanded(
            Fold fold, int rangeStartLine, int rangeEndLine,
            Set<Fold> folds)
    {
        // Note that found fold may be after the range
        int index = findFoldEndIndexLine(fold, rangeStartLine);
        while(index < fold.getFoldCount()) {
            Fold childFold = fold.getFold(index);
            if(getStartLine(childFold) > rangeEndLine) {
                // child fold not within range
                break;
            }
            if(!childFold.isCollapsed()) {
                if(getStartLine(childFold) >= rangeStartLine
                        && getEndLine(childFold) <= rangeEndLine) {
                    folds.add(childFold);
                } else {
                    // childFold is partially contained, check its children
                    collectFullyContainedExpanded(childFold,
                                           rangeStartLine, rangeEndLine,
                                           folds);
                }
            }
            index++;
        }
    }

    /**
     * TODO: this could probably be optimized... but it may not be too bad...
     *
     * @param fold
     * @param rangeStartLine
     * @param rangeEndLine
     * @param initialFolds starts out with the fully contained folds found by
     *          findFullyContainedExpanded.
     */
    private List<Fold> collectPartiallyContainedExpanded(
            int rangeStartLine, int rangeEndLine, Set<Fold> initialFolds)
    {
        List<Fold> foundFolds = new ArrayList<Fold>();
        while(rangeStartLine <= rangeEndLine) {
            // find expanded fold after rangeStartLine
            Fold fold = findPartiallyContainedExpandedFold(rangeStartLine,
                                                           rangeEndLine);
            if(fold == null)
                break;

            // TODO: check for any other folds on the line

            // add fold to set of folds
            foundFolds.add(fold);
            //
            initialFolds.remove(fold);
            rangeStartLine = getEndLine(fold) + 1;
        }
        foundFolds.addAll(initialFolds);
        return foundFolds;
    }

    private Fold findPartiallyContainedExpandedFold(
            int rangeStartLine, int rangeEndLine
            ) {
        Fold rootFold = fh.getRootFold();
        Fold fold = rootFold;

        while (rangeStartLine <= rangeEndLine) {
            int childIndex = findFoldEndIndexLine(fold, rangeStartLine);
            if (childIndex >= 0 && childIndex < fold.getFoldCount()) {
                Fold childFold = fold.getFold(childIndex);
                int childFoldStartLine = getStartLine(childFold);
                int childFoldEndLine = getEndLine(childFold);
                if(rootFold == fold) {
                    if(childFold.isCollapsed()) {
                        rangeStartLine = childFoldEndLine + 1;
                        continue;
                    }
                    // since rootFold is not valid for collapsing
                    // make sure that rangeStartLine is on a valid fold
                    if(rangeStartLine < childFoldStartLine)
                        rangeStartLine = childFoldStartLine;
                    if(rangeStartLine > rangeEndLine)
                        break; // it's a null return since root fold
                }
                if(childFoldStartLine > rangeEndLine)
                    break;
                // childFold is at least partially covered
                if(childFold.isCollapsed()
                        || rangeStartLine < childFoldStartLine
                           && getStartLine(fold) < childFoldStartLine) {
                    // child is collapsed
                    // or parent and range start before the child
                    break; // return the parent
                }
                fold = childFold;
            } else {
                break;
            }
        }
        return (fold != rootFold) ? fold : null;
    }

    private void expandRange(final int rangeStartLine, final int rangeEndLine)
    {
        runFoldOp(new Runnable() {
            @Override
            public void run() {
                int dot = tv.w_cursor.getOffset();

                int startOffset = getStartOffset(rangeStartLine);
                int endOffset = getEndOffset(rangeEndLine);
                List<Fold> folds = new ArrayList<Fold>();
                for(@SuppressWarnings("unchecked") Iterator<Fold> it =
                        FoldUtilities.collapsedFoldIterator(
                                fh, startOffset, endOffset);
                        it.hasNext();) {
                    folds.add(it.next());
                }
                fh.expand(folds);

                setCaretAfterExpand(dot);
            }
        });
    }

    private void opRecursively(final int rangeStartLine,
                               final int rangeEndLine,
                               final SEARCH search) {
        runFoldOp(new Runnable() {
            @Override
            public void run() {
                int dot = tv.w_cursor.getOffset();

                Fold root = fh.getRootFold();
                List<Fold> folds = new ArrayList<Fold>();
                collectRec(root, rangeStartLine, rangeEndLine, search, folds);
                if(search == SEARCH.EXPANDED) {
                    fh.collapse(folds);
                    setCaretAfterCollapse(dot);
                } else if(search == SEARCH.COLLAPSED) {
                    fh.expand(folds);
                    setCaretAfterExpand(dot);
                }

            }
        });
    }

    private void collectRec(Fold f,
                            int rangeStartLine, int rangeEndLine,
                            SEARCH search,
                            Collection<Fold> folds)
    {
        int count = f.getFoldCount();
        for(int i = 0; i < count ; i++) {
            Fold child = f.getFold(i);
            if(getEndLine(child) < rangeStartLine)
                continue;
            if(getStartLine(child) > rangeEndLine)
                break;
            if(search == SEARCH.EXPANDED && !child.isCollapsed()
                    || search == SEARCH.COLLAPSED && child.isCollapsed())
                folds.add(child);
            collectRec(child, rangeStartLine, rangeEndLine, search, folds);
        }
    }

    void makeVisible(final int line)
    {
        // get the fold containing the offset,
        // expand it and all its parents
        runFoldOp(new Runnable() {
            @Override
            public void run() {
                int dot = tv.w_cursor.getOffset();

                List<Fold> foundFolds
                        = jViFindLineFolds(line, SEARCH.DEEPEST);
                List<Fold> folds = new ArrayList<Fold>();
                for(Fold f : foundFolds) {
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
                            if(f.isCollapsed())
                                folds.add(f);
                        }
                        f = f.getParent();
                    }
                }
                Collections.reverse(folds);
                fh.expand(folds);

                setCaretAfterExpand(dot);
            }
        });
    }

    private void runFoldOp(final Runnable r)
    {
        tv.getEditor().getDocument().render(new Runnable()
        {
            @Override
            public void run()
            {
                fh.render(r);
            }
        });
    }

    /**
     * Descend the hierarchy, find the nested fold in the hierarchy which
     * contains the line and is either expand or collapsed as indicated.
     * Accumulate any sibling folds that are on the line.
     * <br>
     * The search deep-dives into hierarchy. But note that it does not go to the
     * bottom. For example, if looking for an expanded fold, it descends through
     * expanded folds until it encounters a collapsed fold.
     *
     * @param line &gt=0 line in a document.
     * @param search what to search for
     * @param findCollapsed return first collapsed fold
     */
    private List<Fold> jViFindLineFolds(int line, SEARCH search) {
        @SuppressWarnings("unchecked")
        List<Fold> folds = Collections.EMPTY_LIST;

        // true ==> first in line
        Fold fold = jViFindLineFold(line, true, search);
        if(fold != null) {
            folds = findSiblingsContainingLine(
                    fold, line, search);
        }

        return folds;
    }

    private List<Fold> findSiblingsContainingLine(
            Fold fold, int line, SEARCH search) {

        List<Fold> folds = new ArrayList<Fold>();

        Fold parent = fold.getParent();
        int index = parent.getFoldIndex(fold);
        // back up to first fold containing this line
        index--;
        while(index >= 0) {
            fold = parent.getFold(index);
            if(getEndLine(fold) < line) {
                break;
            }
            index--;
        }
        index++;
        // Pick up any sibling folds that have this line.
        for(; index < parent.getFoldCount(); index++) {
            fold = parent.getFold(index);
            if(getStartLine(fold) <= line && line <= getEndLine(fold)) {
                if(search == SEARCH.DEEPEST
                        || search == SEARCH.EXPANDED && !fold.isCollapsed()
                        || search == SEARCH.COLLAPSED && fold.isCollapsed())
                folds.add(fold);
            } else
                break;
        }

        return folds;
    }

    private Fold jViFindLineFold(int line, boolean first, SEARCH search) {
        Fold rootFold = fh.getRootFold();
        Fold fold = rootFold;

        while (true) {
            if(search == SEARCH.COLLAPSED && fold.isCollapsed())
                break;
            int childIndex = findFoldStartIndexLine(fold, line, first);
            if (childIndex >= 0) {
                Fold wrapFold = fold.getFold(childIndex);
                int startLine = getStartLine(wrapFold);
                int endLine = getEndLine(wrapFold);
                if (startLine <= line && line <= endLine) {
                    if(search == SEARCH.EXPANDED && wrapFold.isCollapsed()) {
                        break;
                    }
                    fold = wrapFold;
                }else{
                    break;
                }
            } else { // no children => break
                break;
            }
        }
        return (fold != rootFold) ? fold : null;
    }

    private int findFoldStartIndexLine(Fold fold, int line, boolean first) {
        int foldCount = fold.getFoldCount();
        int low = 0;
        int high = foldCount - 1;

        while (low <= high) {
            int mid = (low + high) / 2;
            Fold midFold = fold.getFold(mid);
            int midFoldStartLine = getStartLine(midFold);

            if (midFoldStartLine < line) {
                low = mid + 1;
            } else if (midFoldStartLine > line) {
                high = mid - 1;
            } else {
                // fold starting exactly at the given line found
                if (first) { // search for first fold
                    mid--;
                    while (mid >= 0 && getStartLine(fold.getFold(mid)) == line) {
                        mid--;
                    }
                    mid++;

                } else { // search for last fold
                    mid++;
                    // Search for fold with startLine greater than line
                    while (mid < foldCount
                            && getStartLine(fold.getFold(mid)) == line) {
                        mid++;
                    }
                    mid--;
                }
                return mid;
            }
        }
        return high;
    }

    /**
     *  Find fold containing or after given line.
     *
     * @param fold
     * @param line
     * @return
     */
    private int findFoldEndIndexLine(Fold fold, int line) {
        int foldCount = fold.getFoldCount();
        int low = 0;
        int high = foldCount - 1;

        while (low <= high) {
            int mid = (low + high) / 2;
            Fold midFold = fold.getFold(mid);
            int midFoldEndLine = getEndLine(midFold);

            if (midFoldEndLine < line) {
                low = mid + 1;
            } else if (midFoldEndLine > line) {
                high = mid - 1;
            } else {
                // Fold ending exactly at the given offset found.
                // That's a good candidate (unlike with offsets)
                // NOTE: 2nd condition changed from '<=' to '<'
                while (mid < foldCount && getEndLine(fold.getFold(mid)) < line) {
                    mid++;
                }
                return mid;
            }
        }
        return low;
    }

    //
    // We want to use line numbers for folds
    //

    private int getStartLine(Fold fold)
    {
        FoldLines foldLines = mapFold(fold);
        return foldLines.startLine;
    }

    private int getEndLine(Fold fold)
    {
        FoldLines foldLines = mapFold(fold);
        return foldLines.endLine;
    }

    private int getLineCount(Fold fold)
    {
        return getEndLine(fold) - getStartLine(fold) + 1;
    }

    private int getStartOffset(int line)
    {
        return tv.getBuffer().getLineStartOffset(line);
    }

    private int getEndOffset(int line)
    {
        return tv.getBuffer().getLineEndOffset2(line);
    }

    private class FoldLines {
        final int startLine;
        final int endLine;

        FoldLines(Fold fold)
        {
            startLine = tv.getBuffer().getLineNumber(fold.getStartOffset());
            endLine = tv.getBuffer().getLineNumber(fold.getEndOffset());
        }
    }

    private FoldLines mapFold(Fold fold)
    {
        FoldLines foldLines = mapFolds.get(fold);
        if(foldLines == null) {
            foldLines = new FoldLines(fold);
            mapFolds.put(fold, foldLines);
        }

        return foldLines;
    }

    /**
     * Process event. Ignore added folds. Remove the rest of the folds
     * from mapFolds.
     */
    void invalidate(FoldHierarchyEvent evt) {
        if(evt == null) {
            // doc changes (like adding a line)
            // do not generate FoldHierarchy events. So...
            mapFolds.clear();
            return;
        }

        int count;

        count = evt.getRemovedFoldCount();
        for(int i = 0; i < count; i++) {
            mapFolds.remove(evt.getRemovedFold(i));
        }
        count = evt.getFoldStateChangeCount();
        for(int i = 0; i < count; i++) {
            FoldStateChange fsc = evt.getFoldStateChange(i);
            if(fsc.isStartOffsetChanged() || fsc.isEndOffsetChanged()) {
                mapFolds.remove(fsc.getFold());
            }
        }

        // mapFolds.clear(); // TODO: REMOVE THIS
    }

}
