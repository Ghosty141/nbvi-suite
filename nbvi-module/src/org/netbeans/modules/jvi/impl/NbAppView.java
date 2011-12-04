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

import java.awt.Component;
import java.awt.EventQueue;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JEditorPane;

import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.jvi.Module;
import org.openide.util.WeakSet;
import org.openide.windows.TopComponent;

import com.raelity.jvi.ViAppView;
import com.raelity.jvi.manager.AppViews;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.swing.SwingFactory;

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
 * NEEDSWORK: isNomad() currently will return true if the editor's Doc isn't
 *            a file object. But that restricts certain navigations. So
 *            could make it not nomad if TC is an editor.
 * NEEDSWORK: when "INSTALLING ViCaret" could check to add av's
 *
 * In NB editors can be lazily added to top components.
 *
 * @author Ernie Rael <err at raelity.com>
 */
public class NbAppView implements ViAppView
{
    private static final Logger LOG = Logger.getLogger(NbAppView.class.getName());
    private TopComponent tc;
    private JEditorPane ep;
    private boolean isNomad;
    private int wnum;
    private boolean frozen;

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
        if(this.ep != ep && frozen)
            LOG.log(Level.SEVERE, "frozen and changing ep",
                    new IllegalStateException());
        if(ep != null)
            this.ep = ep;
    }

    private void setIsNomad(boolean isNomad)
    {
        if(this.isNomad != isNomad) {
            if(frozen)
                LOG.log(Level.SEVERE, "frozen and changing isNomad",
                        new IllegalStateException());
            wnum = isNomad ? --genNomadNum : ++genWNum;
        }
        this.isNomad = isNomad;
    }

    private void freeze()
    {
        if(frozen)
            LOG.log(Level.SEVERE, "already frozen",
                    new IllegalStateException());
        frozen = true;
    }

    @Override
    public JEditorPane getEditor()
    {
        return ep;
    }

    @Override
    public boolean isNomad()
    {
        if(tc == null || isNomad)
            return true;
        if(ep == null)
            return false; // actually, don't know yet and can't tell
        return NbEditorUtilities.getFileObject(ep.getDocument()) == null;
    }

    @Override
    public boolean isShowing()
    {
        if(ep != null)
            return ep.isShowing();
        if(tc != null)
            return tc.isShowing();
        LOG.severe("av with no tc or ep");
        return false;
    }

    @Override
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

    public static void closeTC(TopComponent tc)
    {
        tc.putClientProperty(SwingFactory.PROP_AV, null);
    }

    /**
     * This may be called when the app view already exists for the tc,ep pair.
     * A huge portion of this code looks for inconsistencies.
     *
     * The appView is registered with jVi with AppViews.open(...)
     *
     * @param tc
     * @param ep
     */
    public static NbAppView updateAppViewForTC(
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

    public static NbAppView updateAppViewForTC(
            String info, TopComponent tc, JEditorPane ep, boolean isNomad)
    {
        return updateAppViewForTC(info, tc, ep, isNomad, false);
    }

    /**
     * This version allows the appview to be forced as a nomad.
     * @param tc
     * @param ep
     * @param isNomad only used if appview is created
     * @return
     */
    private static NbAppView updateAppViewForTC(
            String info, TopComponent tc, JEditorPane ep,
            boolean isNomad, boolean logState)
    {
        assert EventQueue.isDispatchThread();

        Set<NbAppView> s = null;
        NbAppView av = null;
        tag = "";

        if(tc == null) {
            av = createAppViewOrphan(ep);
        } else {
            // make sure the TC has the set.
            {
                @SuppressWarnings("unchecked")
                Set<NbAppView> s01 = (Set<NbAppView>)
                        tc.getClientProperty(SwingFactory.PROP_AV);
                s = s01;
                if(s == null) {
                    s = new WeakSet<NbAppView>(1);
                    tc.putClientProperty(SwingFactory.PROP_AV, s);
                }
            }

            if(ep == null) {
                // if there's not an editor, then this tc has just been opened
                // and nothing has been assigned to it.
                if(!s.isEmpty())
                    LOG.severe("no editor and !s.isEmpty()");
                for (NbAppView _av : s) {
                    if(_av.getEditor() == null) {
                        av = _av;
                        addTag("WARN already waiting");
                        LOG.warning("WARN already waiting");
                        break;
                    } else {
                        // nightmare
                        addTag("ERROR found ep with tc");
                        LOG.severe("ERROR found ep with tc");
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
                        addTag("NOTE: already exist");
                        break;
                    }
                    if(_av.getEditor() == null) {
                        // There's an appview waiting to accept this editor
                        // The editor should not have an appview already.

                        av = getAppView(ep);

                        if(av == null) {
                            av = _av;
                            av.setEditor(ep); // NORMAL
                            addTag("add ep to tc");
                        } else {
                            // if the editor got an app view as an orphan,
                            // but somehow later was assoc'd with tc
                            addTag("ERROR: orphan added to appview");
                            LOG.severe("orphan added to TC/appview");
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
        if(av == null)
            LOG.severe("no AppView created");
        // set up the references to av from the JComponents
        if(ep != null)
            ep.putClientProperty(SwingFactory.PROP_AV, av);
        if(tc != null && av != null)
            s.add(av);

        if(logState || Module.dbgAct().getBoolean()) {
            String msg = String.format(
                    "updateAppView: (%s:%s) %s tc='%s' ep='%s' doc='%s' isNomad=%b",
                    tag, info,
                    ViManager.getFS().getDisplayFileName(av),
                    tc != null ? Module.cid(tc) : "",
                    ep != null ? Module.cid(ep) : "",
                    ep != null ? Module.cid(ep.getDocument()) : "",
                    av.isNomad);
            Module.dbgAct().println(msg);
            if(logState) {
                LOG.log(Level.SEVERE, msg);
            }
        }

        if(isNomad != av.isNomad) {
            av.setIsNomad(isNomad);
            if(Module.dbgAct().getBoolean())
                Module.dbgAct().println("updateAppView: CONVERT: isNomad "
                                        + av.isNomad);
        }
        AppViews.open(av, info);

        return av;
    }

    /**
     * Invoked when a new text view is created.
     * Will hookup the editor to a lazy appview
     * or create an av if needed. The appview may
     * be changed a little.
     *
     * By this time the editor cookie should be present,
     * if not then it is a nomad.
     *
     * @param jep
     * @return
     */
    // NEEDSWORK: this whole thing is messy...
    static NbAppView avLastChance(JEditorPane jep)
    {
        NbAppView av = (NbAppView)ViManager.getFactory().getAppView(jep);
        NbAppView av01 = av;
        TopComponent tc = null;
        if(av != null) {
            if((tc = av.getTopComponent()) != null) {
                // The editor has an appView with a top component
                // verify that the editor is in the top component
                boolean isEditor = Module.isEditor(tc);
                if(isEditor == av.isNomad()) {
                    // only nomad --> editor is "natural"
                    // log changing editor to nomad
                    if(!isEditor)
                        LOG.log(Level.SEVERE,
                                String.format("isEditor=%b isNomad=%b",
                                              isEditor, av.isNomad()),
                                new IllegalStateException("isEditor == isNomad"));
                    av = updateAppViewForTC("LC_FIXUP", tc, jep, !isEditor);
                }
            } else {
                // TC is null, can we find one?
                tc = Module.getKnownTopComponent(jep);
                boolean isEditor = Module.isEditor(tc);
                av = updateAppViewForTC("LC_UNEXPECTED", tc, jep, !isEditor);
            }
        } else {
            tc = Module.getKnownTopComponent(jep);
            boolean isEditor = Module.isEditor(tc);
            if(tc != null) {
                // turns out this might happen before activation (code eval)
                // so not much error checking.
                // make it a nomad if it is not an editor
                av = updateAppViewForTC("LC_TC", tc, jep, !isEditor);

                // NEEDSWORK: could do this at the end of method for any TC
                // This catches the other side of the diff windows
                // Could restrict to isEditor...
                for(JEditorPane jep01 : Module.getDescendentJviJep(tc)) {
                    if(jep01 != jep && jep01.isShowing())
                        updateAppViewForTC("LC_TC_LAZY", tc, jep01, !isEditor);
                }
            }
            else {
                // no editor top component, create a nomad
                av = updateAppViewForTC("LC_NOMAD", null, jep, true);
            }
        }

        if(av01 != null && av != av01)
            LOG.log(Level.SEVERE, "av changed",
                    new IllegalStateException("av changed"));
        av.freeze(); // freeze after first editor focus
        return av;
    }

    private static NbAppView createAppViewOrphan(JEditorPane ep)
    {
        NbAppView av = getAppView(ep);
        assert av == null;
        if(av == null) {
            av = new NbAppView(null, ep, true); // nomad by definition
            addTag("orphan");
        } else {
            addTag("ERROR: orphan already");
            LOG.severe("ERROR: orphan already");
        }

        return av;
    }

    public static Set<NbAppView> fetchAvFromTC(TopComponent tc) { // NEEDSWORK: into av
        @SuppressWarnings("unchecked")
        Set<NbAppView> s = (Set<NbAppView>)tc
                .getClientProperty(SwingFactory.PROP_AV);
        if(s != null)
            return s;
        return Collections.emptySet();
    }

    public static NbAppView fetchAvFromTC(TopComponent tc, JEditorPane ep) {
        if(tc == null)
            return getAppView(ep);
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
