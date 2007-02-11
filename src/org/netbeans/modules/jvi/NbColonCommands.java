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
import com.raelity.jvi.Util;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViOutputStream;
import org.netbeans.modules.editor.java.JavaFastOpenAction;
import org.openide.util.actions.SystemAction;
import org.openide.windows.TopComponent;

public class NbColonCommands {

  public static void init() {
      setupCommands();
  }

  private NbColonCommands() {
  }

  /** Register some ":" commands */
  static void setupCommands() {
    ColonCommands.register("ts", "tselect", new GoToClassAction());
    
    ColonCommands.register("files","files", ColonCommands.ACTION_BUFFERS);
    ColonCommands.register("buffers","buffers", ColonCommands.ACTION_BUFFERS);
    ColonCommands.register("ls","ls", ColonCommands.ACTION_BUFFERS);

    // ColonCommands.register("N", "Next", Browser.ACTION_NavigateBack);
    // ColonCommands.register("n", "next", Browser.ACTION_NavigateForward);
    
    /*
    ColonCommands.register("mak", "make", makeAction);
    ColonCommands.register("bui", "build", buildAction);
    ColonCommands.register("run", "run",
                           RuntimeActionPool.ACTION_RunProject);
     */
    /*JB7 ColonCommands.register("deb", "debug",
                           RuntimeActionPool.ACTION_ProjectDebug); JB7*/
    // NEEDSWORK: run/debug without build/make

    /*
    initToggleCommand();
    ColonCommands.register("tog", "toggle", toggleAction);
    */
  }

    static private class GoToClassAction implements ActionListener {
        public void actionPerformed(ActionEvent e) {
            boolean ok = true;
            SystemAction sa = null;
            try {
                sa = SystemAction.get(JavaFastOpenAction.class);
            } catch(IllegalArgumentException ex) {
                ok = false;
            }
            if(ok) {
                if(sa.isEnabled()) {
                    sa.actionPerformed(e);
                } else {
                    ok = false;
                }
            }
            if(!ok) {
                Util.vim_beep();
            }
        }
    }

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
