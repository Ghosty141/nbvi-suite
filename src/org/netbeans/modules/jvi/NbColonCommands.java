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

import static org.netbeans.modules.jvi.Module.dbgNb;
import org.netbeans.modules.jvi.impl.NbTextView;
import com.raelity.jvi.core.AbbrevLookup;
import com.raelity.jvi.core.ColonCommands;
import com.raelity.jvi.core.ColonCommands.ColonAction;
import com.raelity.jvi.core.ColonCommands.ColonEvent;
import com.raelity.jvi.core.Msg;
import com.raelity.jvi.core.Util;
import com.raelity.jvi.manager.ViManager;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.lang.ref.WeakReference;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import javax.swing.JEditorPane;
import org.openide.util.ContextAwareAction;
import org.openide.windows.Mode;
import org.openide.windows.TopComponent;
import org.openide.windows.TopComponentGroup;
import org.openide.windows.WindowManager;

public class NbColonCommands {
    private static final
            Logger LOG = Logger.getLogger(NbColonCommands.class.getName());

    public static void init()
    {
        // goto editor tab
        ColonCommands.register("tabn", "tabnext", ACTION_tabnext);
        ColonCommands.register("tabp", "tabprevious", ACTION_tabprevious);
        ColonCommands.register("tabN", "tabNext", ACTION_tabprevious);

        // next previous in current list
        delegate("cn","cnext", FsAct.JUMP_NEXT);
        delegate("cp","cprevious", FsAct.JUMP_PREV);
        delegate("cN","cNext", FsAct.JUMP_PREV);

        // NEEDSWORK: when available, use this for local syntax errors
        delegate("ln","lnext", FsAct.JUMP_NEXT);
        delegate("lp","lprevious", FsAct.JUMP_PREV);
        delegate("lN","lNext", FsAct.JUMP_PREV);

        ColonCommands.register("gr","grep", ACTION_fu);

        ColonCommands.register("fixi","fiximports", ACTION_fiximports);

        // Make
        ColonCommands.register("mak","make", new Make());

        // Refactoring
        delegate("rfr","rfrename", FsAct.RF_RENAME);
        delegate("rfm","rfmove", FsAct.RF_MOVE);
        delegate("rfc","rfcopy", FsAct.RF_COPY);
        delegate("rfsa", "rfsafedelete", FsAct.RF_SAFE_DELETE);
        delegate("rfde", "rfdelete", FsAct.RF_SAFE_DELETE);

        delegate("rfch","rfchangemethodparameters", FsAct.RF_CHANGE_PARAMETERS);
        delegate("rfenc","rfencapsulatefields", FsAct.RF_ENCAPSULATE_FIELD);

        delegate("rfpul","rfpullup", FsAct.RF_PULL_UP);
        delegate("rfup","rfup", FsAct.RF_PULL_UP);
        delegate("rfpus","rfpushdown", FsAct.RF_PUSH_DOWN);
        delegate("rfdo","rfdown", FsAct.RF_PUSH_DOWN);

        delegate("rfvar","rfvariable", FsAct.RF_INTRODUCE_VARIABLE);
        delegate("rfcon","rfconstant", FsAct.RF_INTRODUCE_CONSTANT);
        delegate("rffie","rffield", FsAct.RF_INTRODUCE_FIELD);
        delegate("rfmet","rfmethod", FsAct.RF_INTRODUCE_METHOD);

        /* run and debug are now make targets
        ColonCommands.register("run", "run",
        ColonCommands.register("deb", "debug",
         */

        ColonCommands.register("tog", "toggle", toggleAction);
    }

    private NbColonCommands() {
    }

    static private void delegate(String abrev, String name, String actionPath) {
        ColonCommands.register(abrev, name,
                               new Module.DelegateFileSystemAction(actionPath));
    }

    public static ColonAction ACTION_fiximports = new FixImports();
    static private class FixImports extends ColonAction {
        // new Module.DelegateFileSystemAction(
        // "Menu/Source/org-netbeans-modules-editor-java"
        // + "-JavaFixAllImports$MainMenuWrapper.instance"));
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

    public static ColonAction ACTION_fu = new FindUsages();

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
            Util.vim_beep();
    }

    static private class FindUsages extends ColonAction {
        public void actionPerformed(ActionEvent e) {
            // The execution must be defered for the focus transfer to
            // the JEP to complete.
            EventQueue.invokeLater(new Runnable() {
                public void run() {
                    doWhereUsed();
                }
            });
        }
    }

    private static ActionListener ACTION_tabnext = new TabNext(true);
    private static ActionListener ACTION_tabprevious = new TabNext(false);

    private static class TabNext implements ActionListener { // NEEDSWORK: count
        boolean goForward;

        TabNext(boolean goForward) {
            this.goForward = goForward;
        }

        public void actionPerformed(ActionEvent e) {
            String fsAct;
            if(goForward)
                fsAct = FsAct.TAB_NEXT;
            else
                fsAct = FsAct.TAB_PREV;
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

    static private String[][] mkActions() {
        // NOTE: some entries are null
        return new String[][] {
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
    static private class Make extends ColonAction {
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
                    int mkOp01 = MK_NONE;
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
                        ;
                    else {
                        fError = true;
                        ce.getViTextView().getStatusDisplay().displayErrorMessage(
                                "syntax: mak[e] [b[uild]|c[lean]|r[ebuild]|d[oc]"
                                + "|de[bug]|ru[n]]"
                                + " [m[ain]|p[project]|%]");
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

            String path = mkActions()[mkOp][mkThing];
            if(path == null) {
                ce.getViTextView().getStatusDisplay().displayErrorMessage(
                        "no action for \"make " + mkOpDesc()[mkOp]
                        + " " + mkThingDesc()[mkThing] + "\"");
                fError = true;
            }
            if(dbgNb()) {
                System.err.println( "\"make " + mkOpDesc()[mkOp]
                        + " " + mkThingDesc()[mkThing] + "\"");
            }
            
            if(!fError)
                Module.execFileSystemAction(path, e);
        }
    }

    ///////////////////////////////////////////////////////////////////////
    //
    // :tog[gle] bo[ttom] | ou[tput] | de[bug]
    //
    // This is one hack after another...
    //

    private static AbbrevLookup toggles;

    private static final String M_OUT = "output";
    private static final String M_DBG = "debugger";
    private static ToggleStuff toggleOutput;

    static void initToggleCommand() {
        if(toggles  != null)
            return;
        toggles = new AbbrevLookup();

        toggleOutput = new ToggleOutput(M_OUT);

        toggles.add("ou", "output", toggleOutput);
        toggles.add("bo", "bottom", new ToggleBottom());
        toggles.add("de", "debug", new ActionListener() {
            public void actionPerformed(ActionEvent e)
            {
                ToggleGroup tg = new ToggleGroup(M_DBG);
                if(tg.isOpen())
                    tg.doClose();
                else
                    tg.doOpen();
                ToggleStuff.focusEditor(e.getSource());
            }
        });
    }

    //private static class ToggleTC implements ActionListener {
    //}

    /** Do both output and debugger
     */
    private static class ToggleBottom implements ActionListener {
        boolean didCloseDebug;

        public void actionPerformed(ActionEvent e) {
            ToggleGroup toggleDebug = new ToggleGroup(M_DBG);
            boolean tOutputWindowFlag = toggleOutput.isOpen();
            boolean tDebugWindowFlag = toggleDebug.isOpen();
            if(tOutputWindowFlag || tDebugWindowFlag) {
                if(tDebugWindowFlag) {
                    toggleDebug.doClose();
                    didCloseDebug = true;
                }
                if(tOutputWindowFlag)
                    toggleOutput.doClose();
            } else {
                // only open the debug window if we previously closed it
                if(didCloseDebug) {
                    toggleDebug.doOpen();
                    didCloseDebug = false;
                }
                toggleOutput.doOpen();
            }
            ToggleStuff.focusEditor(e.getSource());
        }
    }

    static Object runMethod(TopComponentGroup tcg, String methodName)
    {
        Object o = null;
        Exception ex1 = null;

        try {
            Class c = tcg.getClass();
            Method getTopComponentsMethod = c.getMethod(methodName);
            o = getTopComponentsMethod.invoke(tcg);
        } catch (IllegalAccessException ex) {
            ex1 = ex;
        } catch (IllegalArgumentException ex) {
            ex1 = ex;
        } catch (InvocationTargetException ex) {
            ex1 = ex;
        } catch (NoSuchMethodException ex) {
            ex1 = ex;
        } catch (SecurityException ex) {
            ex1 = ex;
        }
        if(ex1 != null)
            LOG.log(Level.SEVERE, "Can't run method " + methodName, ex1);

        return o;
    }

    /**
     * @param tcg
     * @return null if can't determine
     */
    static boolean isOpened(TopComponentGroup tcg)
    {
        return (Boolean) runMethod(tcg, "isOpened");
    }

    /**
     * @param tcg
     * @return null if can't determine
     */
    static Set<TopComponent> getTopComponents(TopComponentGroup tcg)
    {
        return (Set<TopComponent>) runMethod(tcg, "getTopComponents");
    }

    private abstract static class ToggleStuff
    {
        abstract boolean isOpen();

        abstract void doOpen();
        abstract void doClose();

        static boolean isMainOutputWindow(TopComponent tc)
        {
            //String cName = tc.getClass().getName();
            return tc.getName().equals("Output");
            //   && (cName.equals("org.netbeans.core.io.ui"
            //                    + ".IOWindow$IOWindowImpl")
            //       || cName.equals("org.netbeans.core.output2.OutputWindow"));
        }

        static void focusEditor(Object o)
        {
            if(o instanceof JEditorPane) {
                JEditorPane ep = (JEditorPane) o;
                ep.requestFocus();
            }
        }

        /** keep track of what has been closed */
        //static void closeMode(String modeName,
        //                      List<WeakReference<TopComponent>> closedList) {
        //    closedList.clear();
        //    Mode mode = WindowManager.getDefault().findMode(modeName);
        //    for (TopComponent tc : mode.getTopComponents()) {
        //        if(tc.isOpened()) {
        //            closedList.add(new WeakReference<TopComponent>(tc));
        //            tc.close();
        //        }
        //    }
        //}

        //static void openMode(String modeName,
        //                     List<WeakReference<TopComponent>> closedList) {
        //    for (WeakReference<TopComponent> wr : closedList) {
        //        TopComponent tc = wr.get();
        //        if(tc != null)
        //            tc.open();
        //    }
        //    closedList.clear();
        //}
        
        // static boolean isOpenMode(String modeName) {
        //     boolean open = false;
        //     Mode mode = WindowManager.getDefault().findMode(modeName);
        //     if(mode != null) {
        //         for (TopComponent tc : mode.getTopComponents()) {
        //             if(tc.isOpened()) {
        //                 open = true;
        //                 break;
        //             }
        //         }
        //     }
        //     return open;
        // }
    }
    
    private static class ToggleOutput extends ToggleStuff
                                    implements ActionListener {
        String modeName;
        List<WeakReference<TopComponent>> closedList;

        public ToggleOutput(String modeName) {
            this.modeName = modeName;
            closedList = new ArrayList<WeakReference<TopComponent>>();
        }

        boolean isOpen() {
            return closedList.isEmpty() && isOpenOutput();
        }

        @Override
        void doOpen() {
            for (WeakReference<TopComponent> wr : closedList) {
                TopComponent tc = wr.get();
                if(tc != null)
                    tc.open();
            }
            closedList.clear();
        }

        @Override
        void doClose() {
            // Close output stuff while keeping track of what is closed.
            // But do NOT close if it is part of the debugger.
            // Except close main output window even though part fo debug stuff.
            ToggleGroup tg = new ToggleGroup(M_DBG);
            Set<TopComponent> dbgSet = getTopComponents(tg.tcg);
            closedList.clear();
            Mode mode = WindowManager.getDefault().findMode(modeName);
            for (TopComponent tc : mode.getTopComponents()) {
                if(dbgSet.contains(tc) && !isMainOutputWindow(tc))
                    continue;
                if(tc.isOpened()) {
                    closedList.add(new WeakReference<TopComponent>(tc));
                    tc.close();
                }
            }
        }

        private boolean isOpenOutput() {
            boolean open = false;
            Mode mode = WindowManager.getDefault().findMode(modeName);
            if(mode != null) {
                ToggleGroup tg = new ToggleGroup(M_DBG);
                Set<TopComponent> dbgSet = getTopComponents(tg.tcg);

                for (TopComponent tc : mode.getTopComponents()) {
                    if(dbgSet.contains(tc) && !isMainOutputWindow(tc))
                        continue;
                    if(tc.isOpened()) {
                        open = true;
                        break;
                    }
                }
            }
            return open;
        }

        public void actionPerformed(ActionEvent e) {
            if(isOpen()) {
                doClose();
            } else {
                doOpen();
            }
            focusEditor(e.getSource());
        }
    }
    
    /**
     * Create this on demand, do not keep it around; it holds references
     * to all the top components in the group.
     */
    private static class ToggleGroup extends ToggleStuff {
        String groupName;
        TopComponentGroup tcg;
        
        ToggleGroup(String groupName) {
            this.groupName = groupName;
            tcg = findGroup(groupName);
        }

        @Override
        boolean isOpen() {
            return tcg != null ? isOpened(tcg) : false;
        }

        @Override
        void doOpen() {
            if(tcg != null)
                tcg.open();
        }

        @Override
        void doClose() {
            if(tcg != null)
                tcg.close();
        }

        /**
         * Find the group. Also get the list of TopComponents in the group.
         * @return the group
         */
        private TopComponentGroup findGroup(String name)
        {
            TopComponentGroup t
                = WindowManager.getDefault().findTopComponentGroup(name);

            if(t == null)
                ViManager.dumpStack("Unknown TCGroup: " + name);

            return t;
        }
    }
    
    /** hide/show stuff as seen in view menu */
    static ColonAction toggleAction = new ColonAction() {
        public void actionPerformed(ActionEvent ev) {
            initToggleCommand();
            ColonEvent cev = (ColonEvent)ev;
            if( cev.getNArg() == 0 || cev.getNArg() == 1) {
                String arg = cev.getNArg() == 0 ? "bottom" : cev.getArg(1);
                AbbrevLookup.CommandElement ce = toggles.lookupCommand(arg);
                if(ce != null) {
                    // pass on the same event that we got
                    ((ActionListener)ce.getValue()).actionPerformed(ev);
                } else {
                    Msg.emsg("Unknown toggle option: " + cev.getArg(1));
                }
            } else {
                Msg.emsg("Only zero or one argument allowed");
            }
        }
    };
}
