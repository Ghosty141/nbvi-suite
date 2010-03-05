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

package org.netbeans.modules.jvi;

import com.raelity.jvi.ViAppView;
import com.raelity.jvi.manager.AppViews;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.swing.SwingFactory;
import java.awt.Component;
import java.awt.EventQueue;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JEditorPane;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.windows.TopComponent;

/**
 * The appview for netbeans. Note that equals is based on '=='.
 * The appaview is referenced as a swing clientproperty from both
 * the editor and top component. There may be more than one
 * editor in a TC, so the TC has a SetOfEditors, usually with one element.
 *
 * It is assumed that both the TC and editor can be hooked together
 * before the editor is actually used. In particular this means before
 * switchto the editor. A lazy hookup can happen when the jVi edtior
 * actions are installed in the editor pane.
 *
 * It is assumed that an editor is not moved from one topcomponent to another.
 *
 * In NB editors can be lazily added to top components.
 *
 * @author Ernie Rael <err at raelity.com>
 */
public class NbAppView implements ViAppView
{
    private TopComponent tc;
    private JEditorPane ep;
    private boolean isNomad;
    private int wnum;

    private static int genWNum; // for the generation of the unique nums
    private static int genNomadNum; // give nomads a unique num

    private NbAppView(TopComponent tc, JEditorPane ep, boolean isNomad)
    {
        this.tc = tc;
        this.ep = ep;
        this.isNomad = isNomad;
        // give nomads negative numbers
        wnum = isNomad ? --genNomadNum : ++genWNum;
    }

    public TopComponent getTopComponent()
    {
        return tc;
    }

    private void setEditor(JEditorPane ep) {
        assert this.ep == null;
        if(ep != null)
            this.ep = ep;
    }

    public JEditorPane getEditor()
    {
        return ep;
    }

    public boolean isNomad()
    {
        if(tc == null || isNomad)
            return true;
        if(ep == null)
            return false; // actually, don't know yet and can't tell
        return NbEditorUtilities.getFileObject(ep.getDocument()) == null;
    }

    public boolean isShowing()
    {
        if(ep != null)
            return ep.isShowing();
        if(tc != null)
            return tc.isShowing();
        assert false;
        return false;
    }

    public int getWNum()
    {
        return wnum;
    }

    @Override
    public String toString()
    {
        return String.format("%s focus(%s,%b)",
                ViManager.getFS().getDisplayFileName(this),
                tc != null ? tc.hasFocus() : "null",
                ep != null ? ep.hasFocus() : "null");
    }

    // public void sort(List<ViAppView> avs)
    // {
    //     if(true) {
    //         List<ViAppView> avs01 = ViManager.getFactory()
    //                 .getWindowTreeBuilder(avs).processAppViews();
    //         avs.clear();
    //         avs.addAll(avs01);
    //     } else {
    //         if(offsetTcToEp == 0) {
    //             // There is a margin between a top component and the editor.
    //             // Determine that offset so that when an AppView without and
    //             // editor is encountered, the offset can be added to the tc.
    //             for (ViAppView _av : avs) {
    //                 NbAppView av = (NbAppView)_av;
    //                 if(av.getEditor() != null && av.getTopComponent() != null)
    //                     offsetTcToEp = av.getEditor().getLocationOnScreen().x
    //                             - av.getTopComponent().getLocationOnScreen().x;
    //             }
    //         }
    //         Collections.sort(avs);
    //     }
    // }

    // public int compareTo(ViAppView o)
    // {
    //     Point w1 = getLocation(this);
    //     Point w2 = getLocation(o);

    //     // if the x coords are with 25 then consider them
    //     // to be aligned vertically. This is because the margin
    //     // can be different for each editor
    //     int rv;
    //     if(Math.abs(w1.x - w2.x) > 25)
    //         rv = w1.x - w2.x;
    //     else
    //         rv = w1.y - w2.y;
    //     System.err.format("Comp rv %d\n    %s%s\n    %s%s\n",
    //             rv, this, w1, o, w2);
    //     return rv;
    // }

    // private static int offsetTcToEp;

    // private static Point getLocation(ViAppView av)
    // {
    //     Point p;
    //     if(av.getEditor() != null)
    //         p = av.getEditor().getLocationOnScreen();
    //     else {
    //         p = ((NbAppView)av).getTopComponent().getLocationOnScreen();
    //         p.x += offsetTcToEp;
    //     }
    //     return p;
    // }

    /**
     * This may be called when the app view already exists for the tc,ep pair.
     * A huge portion of this code looks for inconsistencies.
     *
     * The appView is registered with jVi with AppViews.open(...)
     *
     * @param tc
     * @param ep
     */
    static NbAppView updateAppViewForTC(
            String info, TopComponent tc, JEditorPane ep) {
        return updateAppViewForTC(info, tc, ep, false);
    }

    private static String tag;
    private static void addTag(String t)
    {
        if(tag.isEmpty())
            tag = t;
        else
            tag += "--" + t;
    }

    /**
     * This version allows the appview to be forced as a nomad.
     * @param tc
     * @param ep
     * @param isNomad only used if appview is created
     * @return
     */
    static NbAppView updateAppViewForTC(
            String info, TopComponent tc, JEditorPane ep, boolean isNomad)
    {
        assert EventQueue.isDispatchThread();

        Set<NbAppView> s = null;
        NbAppView av = null;
        tag = "";

        if(tc == null) {
            av = createAppViewOrphan(ep);
        } else {
            // make sure the TC has the set.
            s = (Set<NbAppView>)tc.getClientProperty(SwingFactory.PROP_AV);
            if(s == null) {
                s = new HashSet<NbAppView>();
                tc.putClientProperty(SwingFactory.PROP_AV, s);
            }

            if(ep == null) {
                // if there's not an editor, then this tc has just been opened
                // and nothing has been assigned to it.
                assert s.size() == 0;
                for (NbAppView _av : s) {
                    if(_av.getEditor() == null) {
                        av = _av;
                        addTag("WARN already waiting");
                        break;
                    } else {
                        // nightmare
                        addTag("ERROR found ep with tc");
                    }
                }

                if(av == null)
                    av = new NbAppView(tc, null, isNomad); // NORMAL
                addTag("with no ep");
            } else {
                // check if there is already an app view for this editor
                // or if there is an appview without an editor
                for (NbAppView _av : s) {
                    if(_av.getEditor() == ep) {
                        av = _av;
                        addTag("WARN: already exist");
                        break;
                    }
                    if(_av.getEditor() == null) {
                        // There's an appview waiting to accept this editor
                        // The editor should not have an appview already.
                        // This assert would file if the editor got an app view
                        // as an orphan, but somehow later was assoc'd with tc

                        av = (NbAppView)ep.getClientProperty(SwingFactory.PROP_AV);
                        assert av == null;

                        if(av == null) {
                            av = _av;
                            av.setEditor(ep); // NORMAL
                            addTag("add ep to tc");
                        } else {
                            addTag("ERROR: orphan added to appview");
                        }
                        break;
                    }
                }
                if(av == null) {
                    av = new NbAppView(tc, ep, isNomad); // NORMAL
                    s.add(av);
                    addTag("tc/ep");
                }
            }
        }
        assert av != null;
        // set up the references to av from the JComponents
        if(ep != null)
            ep.putClientProperty(SwingFactory.PROP_AV, av);
        if(tc != null && av != null)
            s.add(av);

        if(Module.dbgAct.getBoolean())
            System.err.printf(
                    "updateAppView: (%s) %s tc '%s' ep '%s' nad %b\n",
                    tag,
                    ViManager.getFS().getDisplayFileName(av),
                    tc != null ? Module.cid(tc) : "",
                    ep != null ? Module.cid(ep) : "",
                    isNomad);

        AppViews.open(av, info);

        return av;
    }

    private static NbAppView createAppViewOrphan(JEditorPane ep)
    {
        NbAppView av = (NbAppView)ep.getClientProperty(SwingFactory.PROP_AV);
        assert av == null;
        if(av == null) {
            av = new NbAppView(null, ep, true); // nomad by definition
            addTag("orphan");
        } else
            addTag("ERROR: orphan already");

        return av;
    }

    static Set<NbAppView> fetchAvFromTC(TopComponent tc) { // NEEDSWORK: into av
        @SuppressWarnings("unchecked")
        Set<NbAppView> s = (Set<NbAppView>)tc
                .getClientProperty(SwingFactory.PROP_AV);
        if(s != null)
            return s;
        return Collections.emptySet();
    }

    static NbAppView fetchAvFromTC(TopComponent tc, JEditorPane ep) {
        if(tc == null)
            return (NbAppView)ep.getClientProperty(SwingFactory.PROP_AV);
        for (NbAppView av : fetchAvFromTC(tc)) {
            if(av.getEditor() == ep)
                return av;

        }
        return null;
    }

    static NbAppView getAppView(Component c)
    {
        NbAppView av = null;
        if(c instanceof TopComponent) {
            Set<NbAppView> avs = fetchAvFromTC((TopComponent)c);
            if(avs.size() == 1)
                av = avs.iterator().next();
        } else {
            av = (NbAppView)ViManager.getFactory().getAppView(c);
        }
        return av;
    }

}
