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

package org.netbeans.modules.jvi.util;

import com.raelity.jvi.lib.MutableBoolean;
import com.raelity.jvi.lib.MutableInt;
import com.raelity.jvi.swing.LineMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.netbeans.api.editor.fold.Fold;
import org.netbeans.api.editor.fold.FoldHierarchy;
import org.netbeans.api.editor.fold.FoldHierarchyEvent;
import org.netbeans.api.editor.fold.FoldHierarchyListener;
import org.netbeans.api.editor.fold.FoldUtilities;
import org.netbeans.modules.jvi.impl.NbTextView;

/**
 *
 * @author Ernie Rael <err at raelity.com>
 */
public class NbLineMapFolding implements LineMap
{
    private NbTextView tv;
    private FoldHierarchy fh;
    private boolean valid;
    private ClosedFold[] fs;

    public NbLineMapFolding(NbTextView tv)
    {
        this.tv = tv;
        fh = FoldHierarchy.get(tv.getEditor());
        setupListeners();
    }

    @Override
    public boolean isFolding()
    {
        // NEEDSWORK: only return true if there are any collapsed folds
        return true;
    }

    @Override
    public int logicalLine(final int docLine) throws RuntimeException
    {
        final MutableInt mi = new MutableInt();
        runLocked(new Runnable()
        {
            @Override
            public void run()
            {
                ClosedFold cf = findDocLineFold(docLine);
                mi.setValue(cf != null ? cf.logicalLine(docLine) : docLine);
            }
        });
        return mi.getValue();
    }

    @Override
    public int docLine(final int logicalLine)
    {
        final MutableInt mi = new MutableInt();
        runLocked(new Runnable()
        {
            @Override
            public void run()
            {
                ClosedFold cf = findLogicalLineFold(logicalLine);
                mi.setValue(cf != null ? cf.docLine(logicalLine)
                                       : logicalLine);
            }
        });
        return mi.getValue();
    }
    
    @Override
    public boolean hasFolding(final int docLine,
                              final MutableInt pDocFirst,
                              final MutableInt pDocLast)
    {
        final MutableBoolean mb = new MutableBoolean();
        runLocked(new Runnable()
        {
            @Override
            public void run()
            {
                ClosedFold cf = findDocLineFold(docLine);
                if(cf != null) {
                    int docLast = cf.getBaseDocLine()
                                  + cf.getFoldLineCount() - 1;
                    if(docLine <= docLast) {
                        mb.setValue(true);
                        if(pDocFirst != null)
                            pDocFirst.setValue(cf.getBaseDocLine());
                        if(pDocLast != null)
                            pDocLast.setValue(docLast);
                    }
                }
            }
        });
        return mb.getValue();
    }

    /** describe a closed fold and the non-folded lines that follow it */
    private class ClosedFold
    {
        Fold fold;
        private int baseDocLine; // beginning document line of the fold
        private int baseLogicalLine; // logical line of the fold
        private int foldLineCount; // doc lines in fold

        ClosedFold(Fold fold, ClosedFold prevFold)
        {
            this.fold = fold;
            baseDocLine = tv.getBuffer().getLineNumber(fold.getStartOffset());

            foldLineCount = tv.getBuffer().getLineNumber(fold.getEndOffset())
                    - baseDocLine + 1;
            if(prevFold == null) {
                baseLogicalLine = baseDocLine;
            } else {
                this.baseLogicalLine = prevFold.getBaseLogicalLine()
                        + (baseDocLine - prevFold.getBaseDocLine())
                        - prevFold.getFoldLineCount() + 1;
            }
        }

        /** this is used only as something to use as a key in a binary search */
        ClosedFold(int v1)
        {
            this.baseDocLine = v1;
            this.baseLogicalLine = v1;
        }

        /** docLine must be in this area */
        int logicalLine(int docLine)
        {
            int lnum = docLine - getBaseDocLine() // delta of doc line
                    - getFoldLineCount() + 1; // adjust delta for folded
            return getBaseLogicalLine() + (lnum <= 0 ? 0 : lnum);
        }

        /** logical line must be in this area */
        int docLine(int logicalLine)
        {
            int lnum = logicalLine - getBaseLogicalLine();// 0 based within area
            if(lnum > 0)
                lnum += getFoldLineCount() - 1;
            return getBaseDocLine() + lnum;
        }

        private int getBaseDocLine()
        {
            return baseDocLine;
        }

        private int getBaseLogicalLine()
        {
            return baseLogicalLine;
        }

        int getFoldLineCount()
        {
            //   tv.getBuffer().getLineNumber(fold.getEndOffset())
            // - tv.getBuffer().getLineNumber(fold.getStartOffset());
            return foldLineCount;
        }

        @Override
        public String toString()
        {
            return "ClosedFold{"
                    + "log=" + getBaseLogicalLine()
                    + " doc=" + getBaseDocLine()
                    + " n=" + getFoldLineCount() + '}';
        }

    }

    private ClosedFold findDocLineFold(int docLine)
    {
        return findFold(new ClosedFold(docLine),
            new Comparator<ClosedFold>()
            {
                @Override
                public int compare(ClosedFold o1, ClosedFold o2)
                {
                    return o1.getBaseDocLine() - o2.getBaseDocLine();
                }
        });
    }

    private ClosedFold findLogicalLineFold(int logicalLine)
    {
        return findFold(new ClosedFold(logicalLine),
            new Comparator<ClosedFold>()
            {
                @Override
                public int compare(ClosedFold o1, ClosedFold o2)
                {
                    return o1.getBaseLogicalLine() - o2.getBaseLogicalLine();
                }
        });
    }

    private ClosedFold findFold(ClosedFold key, Comparator<ClosedFold> comp)
    {
        int i = Arrays.binarySearch(fs, key, comp);
        if(i < 0) {
            i = -i - 1;
            if(i == 0)
                return null; // near beginning of file, before first fold
            --i;
        }
        return fs[i];
    }

    /** note that this will build the hierarchy if needed before
     * running stuff. */
    private void runLocked(final Runnable r)
    {
        tv.getEditor().getDocument().render(new Runnable()
        {
            @Override
            public void run()
            {
                fh.render(new Runnable()
                {
                    @Override
                    public void run()
                    {
                        build();
                        r.run();
                    }
                });
            }
        });
    }

    private ClosedFold previousClosedFold;
    private void build()
    {
        if(valid)
            return;
        //FoldUtilities.collapsedFoldIterator(fh, 0, Integer.MAX_VALUE);
        List<ClosedFold> fl = new ArrayList<ClosedFold>();
        previousClosedFold = null;
        build(fh.getRootFold(), fl);
        fs = fl.toArray(new ClosedFold[0]);
        valid = true;
    }

    private void build(Fold parent, List<ClosedFold> fl)
    {
        if(parent.isCollapsed() && !FoldUtilities.isEmpty(parent)) {
            ClosedFold cf = new ClosedFold(parent, previousClosedFold);
            fl.add(cf);
            previousClosedFold = cf;
            return;
        }
        for (Fold f : FoldUtilities.childrenToArray(parent)) {
            build(f, fl);
        }
    }

    @Override
    public String toString()
    {
        build();
        StringBuilder sb = new StringBuilder();
        sb.append("FoldLineMap{valid=").append(valid).append('\n');
        for (ClosedFold closedFold : fs) {
            sb.append("    ").append(closedFold.toString()).append('\n');
        }
        sb.append('}');
        return sb.toString();
    }

    private void setupListeners()
    {
        fh.addFoldHierarchyListener(new FoldHierarchyListener()
        {
            @Override
            public void foldHierarchyChanged(FoldHierarchyEvent evt)
            {
                valid = false;
            }
        });
        tv.getEditor().getDocument().addDocumentListener(
            new DocumentListener()
            {
                @Override
                public void insertUpdate(DocumentEvent e) { docEvent(e); }
                @Override
                public void removeUpdate(DocumentEvent e) { docEvent(e); }
                @Override
                public void changedUpdate(DocumentEvent e) { docEvent(e); }
        });
    }

    private void docEvent(DocumentEvent e)
    {
        valid = false;
    }

}
