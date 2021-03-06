/**
 * Title:        jVi<p>
 * Description:  A VI-VIM clone.
 * Use VIM as a model where applicable.<p>
 * Copyright:    Copyright (c) Ernie Rael<p>
 * Company:      Raelity Engineering<p>
 * @author Ernie Rael
 * @version 1.0
 */
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
 * Copyright (C) 2000 Ernie Rael.  All Rights Reserved.
 *
 * Contributor(s): Ernie Rael <err@raelity.com>
 */
package org.netbeans.modules.jvi;

import java.awt.Component;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.text.JTextComponent;

import org.netbeans.modules.editor.NbEditorUtilities;
import org.netbeans.modules.jvi.impl.NbTextView;
import org.netbeans.modules.jvi.spi.WindowsProvider;
import org.openide.util.ContextAwareAction;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.WindowManager;

import com.raelity.jvi.core.ColonCommands;
import com.raelity.jvi.core.ColonCommands.AbstractColonAction;
import com.raelity.jvi.core.ColonCommands.ColonAction;
import com.raelity.jvi.core.ColonCommands.ColonEvent;
import com.raelity.jvi.core.Msg;
import com.raelity.jvi.core.Util;
import com.raelity.jvi.core.lib.AbbrevLookup;
import com.raelity.jvi.core.lib.CcFlag;
import com.raelity.jvi.core.lib.ColonCommandItem;
import com.raelity.jvi.manager.ViManager;

import static org.netbeans.modules.jvi.Module.dbgNb;

public class NbColonCommands {
    private static final
            Logger LOG = Logger.getLogger(NbColonCommands.class.getName());

    public static void init()
    {
        //
        delegate("pin", "pin", FsAct.EDITOR_PIN, null);

        // goto editor tab
        ColonCommands.register("tabn", "tabnext", ACTION_tabnext, null);
        ColonCommands.register("tabp", "tabprevious", ACTION_tabprevious, null);
        ColonCommands.register("tabN", "tabNext", ACTION_tabprevious, null);

        // next previous in current list
        delegate("cn","cnext", FsAct.JUMP_NEXT, null);
        delegate("cp","cprevious", FsAct.JUMP_PREV, null);
        delegate("cN","cNext", FsAct.JUMP_PREV, null);

        // NEEDSWORK: when available, use this for local syntax errors
        delegate("ln","lnext", FsAct.JUMP_NEXT, null);
        delegate("lp","lprevious", FsAct.JUMP_PREV, null);
        delegate("lN","lNext", FsAct.JUMP_PREV, null);

        ColonCommands.register("gr","grep", ACTION_fu, null);

        ColonCommands.register("fixi","fiximports", ACTION_fiximports, null);

        // Make
        ColonCommands.register("mak","make", new Make(), null);

        // Refactoring
        delegate("rfr","rfrename", FsAct.RF_RENAME, null);
        delegate("rfm","rfmove", FsAct.RF_MOVE, null);
        delegate("rfc","rfcopy", FsAct.RF_COPY, null);
        delegate("rfsa", "rfsafedelete", FsAct.RF_SAFE_DELETE, null);
        delegate("rfde", "rfdelete", FsAct.RF_SAFE_DELETE, null);

        delegate("rfch","rfchangemethodparameters", FsAct.RF_CHANGE_PARAMETERS, null);
        delegate("rfenc","rfencapsulatefields", FsAct.RF_ENCAPSULATE_FIELD, null);

        delegate("rfpul","rfpullup", FsAct.RF_PULL_UP, null);
        delegate("rfup","rfup", FsAct.RF_PULL_UP, null);
        delegate("rfpus","rfpushdown", FsAct.RF_PUSH_DOWN, null);
        delegate("rfdo","rfdown", FsAct.RF_PUSH_DOWN, null);

        // These are deprecated, hide them in the list
        delegate("rfvar","rfvariable", FsAct.RF_INTRODUCE_VARIABLE,
                 EnumSet.of(CcFlag.DEPRECATED));
        delegate("rfcon","rfconstant", FsAct.RF_INTRODUCE_CONSTANT,
                 EnumSet.of(CcFlag.DEPRECATED));
        delegate("rffie","rffield", FsAct.RF_INTRODUCE_FIELD,
                 EnumSet.of(CcFlag.DEPRECATED));
        delegate("rfmet","rfmethod", FsAct.RF_INTRODUCE_METHOD,
                 EnumSet.of(CcFlag.DEPRECATED));

        delegate("rfintrovar","rfintrovariable", FsAct.RF_INTRODUCE_VARIABLE, null);
        delegate("rfintrocon","rfintroconstant", FsAct.RF_INTRODUCE_CONSTANT, null);
        delegate("rfintrofie","rfintrofield", FsAct.RF_INTRODUCE_FIELD, null);
        delegate("rfintromet","rfintromethod", FsAct.RF_INTRODUCE_METHOD, null);

        ColonCommands.register("tog", "toggle", new ToggleAction(), null);
    }

    private NbColonCommands() {
    }

    /**
     * This class delegates an action to an Action which is found
     * in the file system.
     */
    public static class DelegateFileSystemAction extends AbstractColonAction {
        FsAct fsAct;
        DelegateFileSystemAction(FsAct fsAct) {
            this.fsAct = fsAct;
        }

        @Override
        public EnumSet<CcFlag> getFlags()
        {
            return EnumSet.of(CcFlag.NO_ARGS);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            Module.execFileSystemAction(fsAct, e);
        }

        @Override
        public boolean isEnabled()
        {
            Action act = Module.fetchFileSystemAction(fsAct);
            if(act != null)
                return Module.fetchFileSystemAction(fsAct).isEnabled();
            else
                return false;
        }
    }

    static private void delegate(String abrev, String name, FsAct fsAct,
                                 Set<CcFlag> flags) {
        ColonCommands.register(abrev, name, new DelegateFileSystemAction(fsAct),
                               flags);
    }

    public static ColonAction ACTION_fiximports = new FixImports();

    static private class FixImports extends AbstractColonAction {

        @Override
        public EnumSet<CcFlag> getFlags()
        {
            return EnumSet.of(CcFlag.NO_ARGS);
        }
        // new Module.DelegateFileSystemAction(
        // "Menu/Source/org-netbeans-modules-editor-java"
        // + "-JavaFixAllImports$MainMenuWrapper.instance"));
        
        @Override
        public void actionPerformed(ActionEvent e) {
            Object source = e.getSource();
            if(source instanceof JEditorPane) {
                Object o = ViManager.getFactory()
                            .getTextView((JEditorPane)source);
                NbTextView tv = (NbTextView)o;
                tv.getOps().xact("fix-imports");
            }
        }
    }

    public static ActionListener ACTION_fu = new FindUsages();

    private static void doWhereUsed() {
        Action act = Module.fetchFileSystemAction(FsAct.WHERE_USED);
        if(act != null) {
            TopComponent tc = TopComponent.getRegistry().getActivated();
            act = ((ContextAwareAction) act)
                    .createContextAwareInstance(tc.getLookup());
            ActionEvent ev = new ActionEvent(tc,
                                             ActionEvent.ACTION_PERFORMED,
                                             "");//Utilities.keyToString(ks));
            if(act != null && act.isEnabled())
                act.actionPerformed(ev);
            else
                act = null;
        }
        if(act == null)
            Util.beep_flush();
    }

    static private class FindUsages implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
            // The execution must be defered for the focus transfer to
            // the JEP to complete.
            EventQueue.invokeLater(new Runnable() {
                @Override
                public void run() {
                    doWhereUsed();
                }
            });
        }
    }

    private static final ActionListener ACTION_tabnext
            = new TabNext(true);
    private static final ActionListener ACTION_tabprevious
            = new TabNext(false);

    private static class TabNext implements ActionListener { // NEEDSWORK: count
        boolean goForward;

        TabNext(boolean goForward) {
            this.goForward = goForward;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            FsAct fsAct;
            fsAct = goForward ? FsAct.TAB_NEXT : FsAct.TAB_PREV;
            Module.execFileSystemAction(fsAct, e);
        }
    }

    ///////////////////////////////////////////////////////////////////////
    //
    // Make
    //

    private static final int MK_BUILD = 0;
    private static final int MK_CLEAN = 1;
    private static final int MK_REBUILD = 2;
    private static final int MK_RUN = 3;
    private static final int MK_DEBUG = 4;
    private static final int MK_TEST = 5;
    private static final int MK_DEBUG_TEST = 6;
    private static final int MK_DOC = 7;

    private static final int MK_MAIN = 0;
    private static final int MK_PROJECT = 1;
    private static final int MK_FILE = 2;

    private static final int MK_NONE = -1;

    static private FsAct[][] mkActions() {
        // NOTE: some entries are null
        return new FsAct[][] {
            { FsAct.MK_M_BUILD,   FsAct.MK_P_BUILD,   FsAct.MK_F_BUILD,   },
            { FsAct.MK_M_CLEAN,   FsAct.MK_P_CLEAN,   FsAct.MK_F_CLEAN,   },
            { FsAct.MK_M_REBUILD, FsAct.MK_P_REBUILD, FsAct.MK_F_REBUILD, },
            { FsAct.MK_M_RUN,     FsAct.MK_P_RUN,     FsAct.MK_F_RUN,     },
            { FsAct.MK_M_DEBUG,   FsAct.MK_P_DEBUG,   FsAct.MK_F_DEBUG,   },
            { FsAct.MK_M_TEST,    FsAct.MK_P_TEST,    FsAct.MK_F_TEST,    },
            { FsAct.MK_M_DBGTEST, FsAct.MK_P_DBGTEST, FsAct.MK_F_DBGTEST, },
            { FsAct.MK_M_DOC,     FsAct.MK_P_DOC,     FsAct.MK_F_DOC,     },
        };
    }
    static private String[] mkOpDesc() {
        return new String[] {
            "build",
            "clean",
            "rebuild",
            "run",
            "debug",
            "test",
            "debug-test",
            "doc-java",
        };
    }

    static private String[] mkThingDesc() {
        return new String[] {
            "main",
            "project",
            "%"
        };
    }

    static private int parseMkThing(String a) {
        int thing = MK_NONE;
        if("main".startsWith(a))
            thing = MK_MAIN;
        else if("project".startsWith(a))
            thing = MK_PROJECT;
        else if("%".startsWith(a))
            thing = MK_FILE;
        return thing;
    }
    
    /** Make
     * :mak[e] [ b[uild] | c[lean] | r[ebuild]| d[oc] de[bug] | ru[n]] \
     *         [ m[ain] | p[roject] | % ]
     */
    static private class Make extends AbstractColonAction {
        @Override
        public void actionPerformed(ActionEvent e) {
            ColonEvent ce = (ColonEvent)e;
            boolean fError = false;
            // make [c[lean]] [a[ll]]

            int mkThing = MK_NONE;
            int mkOp = MK_NONE;

            if(ce.getNArg() > 0) {
                for(int i = 1; i <= ce.getNArg(); i++) {
                    String a = ce.getArg(i);
                    int mkThing01 = MK_NONE;
                    int mkOp01;
                    //
                    // NOTE: debug and run are after doc and rebuild,
                    //       so they require two character match
                    //
                    if("build".startsWith(a))
                        mkOp01 = MK_BUILD;
                    else if("clean".startsWith(a))
                        mkOp01 = MK_CLEAN;
                    else if("rebuild".startsWith(a))
                        mkOp01 = MK_REBUILD;
                    else if("doc".startsWith(a))
                        mkOp01 = MK_DOC;
                    else if("debug".startsWith(a))
                        mkOp01 = MK_DEBUG;
                    else if("run".startsWith(a))
                        mkOp01 = MK_RUN;
                    else if((mkThing01 = parseMkThing(a)) != MK_NONE)
                        mkOp01 = MK_NONE;
                    else {
                        mkOp01 = MK_NONE;
                        fError = true;
                        ce.getViTextView().getStatusDisplay().displayErrorMessage(
                            "syntax: mak[e]"
                            + "     [ b[uild] | c[lean] | r[ebuild] | d[oc]"
                            + " | de[bug] | ru[n] ]"
                            + "     [ m[ain] | p[project] | % ]");
                    }
                    if(mkOp01 != MK_NONE) {
                        if(mkOp == MK_NONE)
                            mkOp = mkOp01;
                        else {
                            ce.getViTextView().getStatusDisplay()
                                    .displayErrorMessage(
                                    "only one of build, clean, rebuild, doc,"
                                    + " debug, run");
                            fError = true;
                        }
                    }
                    if(mkThing01 != MK_NONE) {
                        if(mkThing == MK_NONE)
                            mkThing = mkThing01;
                        else {
                            ce.getViTextView().getStatusDisplay()
                                    .displayErrorMessage(
                                    "only one of main, project, %");
                            fError = true;
                        }
                    }
                }
            }
            if(mkOp == MK_NONE)
                mkOp = MK_BUILD;
            if(mkThing == MK_NONE)
                mkThing = MK_MAIN;

            FsAct fsAct = mkActions()[mkOp][mkThing];
            if(fsAct == null || fsAct.path() == null) {
                ce.getViTextView().getStatusDisplay().displayErrorMessage(
                        "no action for \"make " + mkOpDesc()[mkOp]
                        + " " + mkThingDesc()[mkThing] + "\"");
                fError = true;
            }
            if(dbgNb().getBoolean()) {
                dbgNb().println( "\"make " + mkOpDesc()[mkOp]
                        + " " + mkThingDesc()[mkThing] + "\"");
            }
            
            if(!fError)
                Module.execFileSystemAction(fsAct, e);
        }
    }

    ///////////////////////////////////////////////////////////////////////
    //
    // :tog[gle] bo[ttom] | ou[tput] | de[bug]
    //
    // In NB7.1 there are window group minimize/restore actions;
    // use reflection to get at the functionality.
    // special case toggle with no arg to do the window group.
    //
    //

    /**
     * This is the toggle command action.
     * It bounces to the right action as indicate by args.
     *
     */
    private static class ToggleAction extends AbstractColonAction {
        @Override
        public void actionPerformed(ActionEvent ev) {
            boolean doBeep = false;
            initToggleCommand();
            ColonEvent cev = (ColonEvent)ev;

            if( cev.getNArg() == 0 || cev.getNArg() == 1) {
                String arg = cev.getNArg() == 0 ? "output" : cev.getArg(1);
                ColonCommandItem ce = toggles.lookupCommand(arg);
                if(ce != null) {
                    if(ViManager.getHackFlag(Module.HACK_WINDOW_GROUP))
                        toggleOutputWindowGroup(ce.getName());
                    else
                        ((ActionListener)ce.getValue()).actionPerformed(cev);
                    Object source = cev.getSource();
                    if(source instanceof Component) {
                        ((Component)source).requestFocusInWindow();
                    }
                } else {
                    Msg.emsg("Unknown toggle argument: " + cev.getArg(1));
                    doBeep = true;
                }
            } else {
                Msg.emsg("Only zero or one argument allowed");
                doBeep = true;
            }
            if(doBeep)
                Util.beep_flush();
        }
    };

    // Mode: topSlidingSide
    // Mode: navigator
    // Mode: output
    // Mode: leftSlidingSide
    // Mode: rightSlidingSide
    // Mode: bottomSlidingSide
    // Mode: webpreview
    // Mode: code_evaluator_js
    // Mode: editor
    // Mode: explorer
    // Mode: commonpalette
    // Mode: properties
    // Mode: code_evaluator
    // Mode: anonymousMode_1

    // following are default grouping.
    // Want to make it configurable and/or dynamic/automatic
    private static final String M_OUTPUT = "output";

    private static final String M_LEFT_SLIDE = "leftSlidingSide";
    private static final String M_RIGHT_SLIDE = "rightSlidingSide";
    private static final String M_TOP_SLIDE = "topSlidingSide";
    private static final String M_BOTTOM_SLIDE = "bottomSlidingSide";

    /**
     * It is assumed that the visible modes slide into the slider mode.
     *
     * @param slider mode that the visible modes slide into
     * @param visible visible modes
     */
    private static void toggleOutputWindowGroup(String slideSide) {

        if("output".equals(slideSide))
            slideSide = "bottom";

        WindowManager wm = WindowManager.getDefault();
        WindowsProvider wp = Module.getWindowsProvider();

        boolean hasSliderTC = false; // is some TC in the slider
        boolean hasVisibleTC = false; // is some TC in the visible modes
        List<Mode> mVisible = new ArrayList<Mode>();

        // Set<TopComponent> tcOpened = new HashSet<TopComponent>();

        // for(TopComponent tc : wm.getRegistry().getOpened()) {
        //     tcOpened.add(tc);
        // }

        for(Mode m : wm.getModes()) {
            String mSlideSide = wp.getSlideSideForMode(m);
            String modeName = m.getName();
            if(M_LEFT_SLIDE.equals(modeName)
                    || M_RIGHT_SLIDE.equals(modeName)
                    || M_TOP_SLIDE.equals(modeName)
                    || M_BOTTOM_SLIDE.equals(modeName))
                continue;
            if(slideSide.equals(mSlideSide)
                    && !wm.isEditorMode(m))
                mVisible.add(m);
        }
        // mVisible is list of modes that hide into slide side

        String slider;
        if(slideSide.equals("left"))
            slider = M_LEFT_SLIDE;
        else if(slideSide.equals("right"))
            slider = M_RIGHT_SLIDE;
        else if(slideSide.equals("top"))
            slider = M_TOP_SLIDE;
        else if(slideSide.equals("bottom"))
            slider = M_BOTTOM_SLIDE;
        else
            return;
        Mode mSlider = wm.findMode(slider);
        if(mSlider == null)
            return;

        for(TopComponent tc : mSlider.getTopComponents()) {
            if(tc.isOpened()) {
                hasSliderTC = true;
                break;
            }
        }

        checkVisible: {
            for(Mode mVis : mVisible) {
                if(!hasVisibleTC) {
                    for(TopComponent tc : mVis.getTopComponents()) {
                        if(tc.isOpened()) {
                            hasVisibleTC = true;
                            break checkVisible;
                        }
                    }
                }
            }
        }

        // first check if anything is in the sliders
        // ifso, make them all visible (like closing the slider)
        // otherwise, stash into the slider
        if(hasSliderTC) {
            for(Mode mVis : mVisible) {
                wp.restoreMode(mSlider, mVis);
            }
        } else if(hasVisibleTC) {
            for(Mode mVis : mVisible) {
                wp.minimizeMode(mVis);
            }
        } // else ???
    }

    private static AbbrevLookup toggles;

    private static final String M_DBG = "debugger";

    /**
     * set up the sub-actions for the toggle command
     */
    private static void initToggleCommand() {
        if(toggles  != null)
            return;
        toggles = new AbbrevLookup();

        if(ViManager.getHackFlag(Module.HACK_WINDOW_GROUP)) {
            // output not really used, turns into bottom
            toggles.add("ou", "output", Module.HACK_WINDOW_GROUP, null);
            toggles.add("t", "top", Module.HACK_WINDOW_GROUP, null);
            toggles.add("b", "bottom", Module.HACK_WINDOW_GROUP, null);
            toggles.add("l", "left", Module.HACK_WINDOW_GROUP, null);
            toggles.add("r", "right", Module.HACK_WINDOW_GROUP, null);
        } else {
            toggles.add("ou", "output", new SimpleToggleOutput(), null);
        }
        /*
        toggleOutput = new ToggleOutput(M_OUT);

        toggles.add("ou", "output", toggleOutput, null);
        toggles.add("bo", "bottom", new ToggleBottom(), null);
        toggles.add("de", "debug", new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e)
            {
                ToggleGroup tg = new ToggleGroup(M_DBG);
                if(tg.isOpen())
                    tg.doClose();
                else
                    tg.doOpen();
                ToggleStuff.focusEditor(e.getSource());
            }
        }, null);
         */
    }

    /**
     * This sub-action handles the output window.
     */
    private static class SimpleToggleOutput implements ActionListener
    {
        String restoreToModeName;
        String hideToModeName = "bottomSlidingSide";

        public SimpleToggleOutput()
        {
        }

        @Override
        public void actionPerformed(ActionEvent e)
        {
            TopComponent tc = Module.getOutput();
            WindowManager wm = WindowManager.getDefault();
            Mode mHide = wm.findMode(hideToModeName);
            Mode mCurrent = wm.findMode(tc);
            if(mHide.equals(mCurrent)) {
                // make the output visible
                Mode restore = null;
                if(restoreToModeName != null)
                    restore = wm.findMode(restoreToModeName);
                if(restore == null)
                    restore = wm.findMode(M_OUTPUT);
                if(restore != null) {
                    restoreToModeName = null;
                    restore.dockInto(tc);
                    tc.open();
                    tc.requestVisible();
                    //
                    if(e.getSource() instanceof JTextComponent) {
                        // need this because if there had not been something
                        // in the output, then the "Quick Search in the System"
                        // ends up with the focus. Seems like NB bug.
                        JTextComponent jtc = (JTextComponent)e.getSource();
                        TopComponent t = NbEditorUtilities.getTopComponent(jtc);
                        t.requestActive();
                    }
                    //System.err.println("TOGGLE: SHOW");
                }
            } else {
                // hide the output
                restoreToModeName = mCurrent.getName();
                mHide.dockInto(tc);
                tc.open();
                //System.err.println("TOGGLE: HIDE");
            }
        }

    }
}
