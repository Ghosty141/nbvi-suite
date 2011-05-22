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

import com.raelity.jvi.ViAppView;
import com.raelity.jvi.ViTextView.Orientation;
import com.raelity.jvi.core.lib.WindowTreeBuilder;
import com.raelity.jvi.manager.ViManager;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

/**
 *
 * @author Ernie Rael <err at raelity.com>
 */
public class NbWindowTreeBuilder extends WindowTreeBuilder
{
    private Set<JComponent> realToDo = new HashSet<JComponent>();

    public NbWindowTreeBuilder(List<ViAppView> avs)
    {
        super(avs);
        // readToDo may have top components
        for (ViAppView _av : avs) {
            NbAppView av = (NbAppView)_av;
            if(av.getEditor() != null)
                realToDo.add(av.getEditor());
            else
                realToDo.add(av.getTopComponent());
        }
    }

    @Override
    protected Component windowForAppView(ViAppView _av)
    {
        NbAppView av = (NbAppView)_av;
        JComponent c = av.getEditor();
        if(c == null)
            c = av.getTopComponent();
        return c.getRootPane().getContentPane();
    }

    // would like to use mode.getBounds, but it doesn't work
    @Override
    protected Rectangle getNodeRectangle(Node n)
    {
        // Since modeImpl.getBounds does not work,
        // looking for an enclosing ModelPanel work pretty good.
        // Using that, the distance between left-right is 4 (instead of ~70)
        Component c = NbTextView.findModePanel(n.getPeer());
        if(c != null) {
            Rectangle r = SwingUtilities.getLocalBounds(c);
            r = SwingUtilities.convertRectangle(c, r, null);
            return r;
        }
        return super.getNodeRectangle(n);
    }



    @Override
    protected void initTree()
    {
        super.initTree();
        realToDo = null;
    }

    @Override
    protected NbAppView getAppView(Component c)
    {
        // NEEDSWORK: peer may be top component
        return NbAppView.getAppView(c);
    }

    @Override
    @SuppressWarnings("element-type-mismatch")
    protected boolean isEditor(Component c)
    {
        return realToDo.contains(c);
    }

    @Override
    protected boolean removeFromToDo(Component c)
    {
        realToDo.remove((JComponent)c);
        return removeFromToDo(NbAppView.getAppView(c));
    }

    @Override
    protected Node createEditorNode(Component peer)
    {
        return new MyNode(peer);
    }

    @Override
    protected Node createSplitNode(Component peer, List<Node> children)
    {
        Orientation orientation = calcSplitterOrientation(
                            peer, children.get(0), children.get(1));
        if(children.size() >= 2) {
            final boolean isLeftRight = orientation == Orientation.LEFT_RIGHT;
            Collections.sort(children, new Comparator<Node>()
                {
                    @Override
                    public int compare(Node n01, Node n02)
                    {
                        Point p1 = getLocation(n01);
                        Point p2 = getLocation(n02);
                        return isLeftRight ? p1.x - p2.x : p1.y - p2.y;
                    }
                });
        }
        return new MyNode(orientation, peer, children);
    }

    protected class MyNode extends Node {

        public MyNode(
                Orientation orientation, Component peer, List<Node> children)
        {
            super(orientation, peer, children);
        }

        public MyNode(Component peer)
        {
            super(peer);
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder(super.toString());
            if(isEditor()) {
                NbAppView av = getAppView(getPeer());
                sb.append(" ").append(ViManager.getFS().getDisplayFileName(av));
                TopComponent tc = av.getTopComponent();
                if(tc != null) {
                    WindowManager wm = WindowManager.getDefault();
                    Mode m = wm.findMode(tc);
                    if(m != null) {
                        sb.append(" mode=").append(m.getName())
                                .append(" isEdMode=").append(wm.isEditorMode(m));
                    } else
                        sb.append("null-Mode");
                } else
                    sb.append(" null-TC");
            }
            return sb.toString();
        }

    }

    @Override
    protected void dumpWinAction(ActionEvent e, StringBuilder sb)
    {
        //
        // prepend a list of modes
        //

        sb = new StringBuilder();
        WindowManager wm = WindowManager.getDefault();
        for(Mode m : wm.getModes()) {
            sb.append("mode=").append(m.getName())
                    .append(" isEdMode=").append(wm.isEditorMode(m))
                    .append('\n');
        }
        super.dumpWinAction(e, sb);
    }

}
