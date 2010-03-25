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
import com.raelity.jvi.core.WindowTreeBuilder;
import com.raelity.jvi.manager.ViManager;
import java.awt.Component;
import java.awt.Point;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.JComponent;

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

    @Override
    public List<ViAppView> processAppViews()
    {
        List<ViAppView> avs = super.processAppViews();
        realToDo = null;
        return avs;
    }

    @Override
    protected ViAppView getAppView(Component c)
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
        if(children.size() >= 2) {
            final boolean isLeftRight = calcOrientation(
                    children.get(0), children.get(1)) == Orientation.LEFT_RIGHT;
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
        return new MyNode(peer, children);
    }

    protected class MyNode extends Node {

        public MyNode(Component peer, List<Node> children)
        {
            super(peer, children);
        }

        public MyNode(Component peer)
        {
            super(peer);
        }

        @Override
        public String toString()
        {
            String s = super.toString();
            if(isEditor())
                s += " " + ViManager.getFS().getDisplayFileName(
                        getAppView(getPeer()));
            return s;
        }

    }

}
