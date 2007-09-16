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

import com.raelity.jvi.ColonCommands;
import com.raelity.jvi.ColonCommands.ColonAction;
import com.raelity.jvi.ColonCommands.ColonEvent;
import com.raelity.jvi.Util;
import com.raelity.jvi.ViManager;
import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.Action;
import org.openide.util.ContextAwareAction;
import org.openide.windows.TopComponent;

public class NbColonCommands {

    public static void init() {
        setupCommands();
    }

    private NbColonCommands() {
    }

    /** Register some ":" commands */
    static void setupCommands() {
        ColonCommands.register("n", "next", ACTION_next);
        ColonCommands.register("N", "Next", ACTION_Next);
        ColonCommands.register("prev", "previous", ACTION_Next);
        
        ColonCommands.register("files","files", ColonCommands.ACTION_BUFFERS);
        ColonCommands.register("buffers","buffers", ColonCommands.ACTION_BUFFERS);
        ColonCommands.register("ls","ls", ColonCommands.ACTION_BUFFERS);

        // goto editor tab
        ColonCommands.register("tabn", "tabnext", ACTION_tabnext);
        ColonCommands.register("tabp", "tabprevious", ACTION_tabprevious);
        ColonCommands.register("tabN", "tabNext", ACTION_tabprevious);
    
        // next previous in current list
        delegate("cn","cnext", FsAct.JUMP_NEXT);
        delegate("cp","cprevious", FsAct.JUMP_PREV);

        // NEEDSWORK: when available, use this for local syntax errors
        delegate("ln","lnext", FsAct.JUMP_NEXT);
        delegate("lp","lprevious", FsAct.JUMP_PREV);
        
        ColonCommands.register("gr","grep", ACTION_fu);

        ColonCommands.register("fixi","fiximports", ACTION_fiximports);
        
        // Make
        ColonCommands.register("mak","make", new Make());

        // Refactoring
        delegate("rfr","rfrename", FsAct.RF_RENAME);
        delegate("rfm","rfmove", FsAct.RF_MOVE);
        delegate("rfc","rfcopy", FsAct.RF_COPY);
        delegate("rfsa", "rfsafelydelete", FsAct.RF_SAFE_DELETE);
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
        
        /*
        ColonCommands.register("run", "run",
        ColonCommands.register("deb", "debug",
         */
        
        /*
        initToggleCommand();
        ColonCommands.register("tog", "toggle", toggleAction);
        */
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
            Object o = ViManager.getViFactory()
                        .getExistingViTextView(e.getSource());
            NbTextView tv = (NbTextView)o;
            tv.getOps().xact("fix-imports");
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
    
    private static ColonAction ACTION_next = new Next(true);
    private static ColonAction ACTION_Next = new Next(false);
    
    /** next/Next/previous */
    static private class Next extends ColonAction {
        boolean goForward;
        
        Next(boolean goForward) {
            this.goForward = goForward;
        }
        public void actionPerformed(ActionEvent e) {
            ColonEvent ce = (ColonEvent)e;
            int offset;
            if(ce.getAddrCount() == 0)
                offset = 1;
            else
                offset = ce.getLine1();
            if(!goForward)
                offset = -offset;
            TopComponent tc = (TopComponent)ViManager.relativeMruBuffer(offset);
            if(tc != null) {
                ViManager.ignoreActivation(tc); // don't want mru list to change
                tc.requestActive();
            }
        }
    }

    private static ActionListener ACTION_tabnext = new TabNext(true);
    private static ActionListener ACTION_tabprevious = new TabNext(false);

    private static class TabNext implements ActionListener {
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
                int mkThing01 = MK_NONE;
                int mkOp01 = MK_NONE;
                for(int i = 1; i <= ce.getNArg(); i++) {
                    String a = ce.getArg(i);
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
            if(true) {
                System.err.println( "\"make " + mkOpDesc()[mkOp]
                        + " " + mkThingDesc()[mkThing] + "\"");
            }
            
            if(!fError)
                Module.execFileSystemAction(path, e);
        }
    }

    ///////////////////////////////////////////////////////////////////////
    //

  /*
  private static AbbrevLookup toggles = new AbbrevLookup();
  static void initToggleCommand() {
    toggles.add("cur", "curtain", BrowserView.ACTION_ToggleCurtain);
    toggles.add("mes", "messages", BrowserView.STATE_MessagePaneVisible);
    toggles.add("con", "content", Browser.STATE_ContentPaneVisible);
    toggles.add("pro", "project", Browser.STATE_ProjectPaneVisible);
    toggles.add("str", "structure", Browser.STATE_StructurePaneVisible);
    toggles.add("sta", "statusbar", Browser.STATE_StatusPaneVisible);
    tryCosmetics();
  }
  */

  /**
   * If McGrath's open tool is available, then set up a toggle for the
   * show/hide tabs action.
   */
  /*
  static private void tryCosmetics() {
    try {
      Class cosm = Class.forName("mcgrath.opentools.cosmetics.ContentTabs");
      Field f = cosm.getField("STATE_ContentTabsVisible");
      BrowserStateAction act = (BrowserStateAction)f.get(null);
      toggles.add("tab", "tabs", act);
    } catch(Throwable t) {}
  }
  */

  /** hide/show stuff as seen in view menu */
  /*
  static ColonAction toggleAction = new ColonAction() {
    public void actionPerformed(ActionEvent ev) {
      ColonEvent cev = (ColonEvent)ev;
      if(cev.getNArg() == 1) {
        AbbrevLookup.CommandElement ce = toggles.lookupCommand(cev.getArg(1));
        if(ce != null) {
          // pass on the same event that we got
          ((ActionListener)ce.getValue()).actionPerformed(ev);
        } else {
          Msg.emsg("Unknown toggle option: " + cev.getArg(1));
        }
      } else {
        Msg.emsg("Only single argument allowed");
      }
    }
  };
  */

  /*
  static ColonAction makeAction = new ColonAction() {
    public void actionPerformed(ActionEvent ev) {
      ColonEvent cev = (ColonEvent)ev;
      if(cev.getNArg() == 0) {
        if(JBOT.has44()) {
          BuildActionPool.ACTION_ProjectFirstBuildAction.actionPerformed(ev);
        } else {
          //JB7 BuildActionPool.ACTION_ProjectMake.actionPerformed(ev);
          Msg.emsg("JB7");
        }
      } else if(cev.getNArg() == 1 && cev.getArg(1).equals("%")) {
        BuildActionPool.ACTION_ProjectNodeMake.actionPerformed(ev);
      } else {
        Msg.emsg("Only single argument '%' allowed");
      }
    }
  };

  static ColonAction buildAction = new ColonAction() {
    public void actionPerformed(ActionEvent ev) {
      ColonEvent cev = (ColonEvent)ev;
      if(cev.getNArg() == 0) {
        if(JBOT.has44()) {
          BuildActionPool.ACTION_ProjectSecondBuildAction.actionPerformed(ev);
          //BuildActionPool.ACTION_ProjectSecondBuildAction
          //                .actionPerformed(Browser.getActiveBrowser());
        } else {
          //JB7 BuildActionPool.ACTION_ProjectRebuild.actionPerformed(ev);
          Msg.emsg("JB7");
        }
      } else if(cev.getNArg() == 1 && cev.getArg(1).equals("%")) {
        BuildActionPool.ACTION_ProjectNodeRebuild.actionPerformed(ev);
      } else {
        Msg.emsg("Only single argument '%' allowed");
      }
    }
  };
  */

  /**
   * Determines if JB's curtain is open or not.
   */
  /*
  boolean hasCurtain(Object o) {
    Browser b = Browser.findBrowser(o);
    return Browser.STATE_ProjectPaneVisible.getState(b)
           || Browser.STATE_StructurePaneVisible.getState(b);
  }
  */
}
