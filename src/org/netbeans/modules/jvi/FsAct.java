/*
 * FsAct.java
 * 
 * Created on Sep 14, 2007, 5:08:14 PM
 * 
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.netbeans.modules.jvi;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;

/**
 * This class contains the paths for NetBeans actions used by jVi.
 * 
 * @author erra
 */
public enum FsAct {
        EDITOR_PIN		(s.EDITOR_PIN),
        UNDO			(s.UNDO),
        REDO			(s.REDO),
        GO_TYPE			(s.GO_TYPE),
        TAB_NEXT		(s.TAB_NEXT),
        TAB_PREV		(s.TAB_PREV),
        BM_TOGGLE		(s.BM_TOGGLE),
        BM_NEXT			(s.BM_NEXT),
        BM_PREV			(s.BM_PREV),
        WHERE_USED		(s.WHERE_USED),
        JUMP_NEXT		(s.JUMP_NEXT),
        JUMP_PREV		(s.JUMP_PREV),
        WORD_MATCH_NEXT		(s.WORD_MATCH_NEXT),
        WORD_MATCH_PREV		(s.WORD_MATCH_PREV),
        RF_RENAME		(s.RF_RENAME),
        RF_MOVE			(s.RF_MOVE),
        RF_COPY			(s.RF_COPY),
        RF_SAFE_DELETE		(s.RF_SAFE_DELETE),
        RF_CHANGE_PARAMETERS	(s.RF_CHANGE_PARAMETERS),
        RF_ENCAPSULATE_FIELD	(s.RF_ENCAPSULATE_FIELD),
        RF_PULL_UP		(s.RF_PULL_UP),
        RF_PUSH_DOWN		(s.RF_PUSH_DOWN),
        RF_INTRODUCE_VARIABLE	(s.RF_INTRODUCE_VARIABLE),
        RF_INTRODUCE_CONSTANT	(s.RF_INTRODUCE_CONSTANT),
        RF_INTRODUCE_FIELD	(s.RF_INTRODUCE_FIELD),
        RF_INTRODUCE_METHOD	(s.RF_INTRODUCE_METHOD),
        MK_M_BUILD		(s.MK_M_BUILD),
        MK_M_CLEAN		(s.MK_M_CLEAN),
        MK_M_REBUILD		(s.MK_M_REBUILD),
        MK_M_RUN		(s.MK_M_RUN),
        MK_M_DEBUG		(s.MK_M_DEBUG),
        MK_M_TEST		(null),
        MK_M_DBGTEST		(null),
        MK_M_DOC		(null),
        MK_P_BUILD		(s.MK_P_BUILD),
        MK_P_CLEAN		(s.MK_P_CLEAN),
        MK_P_REBUILD		(s.MK_P_REBUILD),
        MK_P_RUN		(s.MK_P_RUN),
        MK_P_DEBUG		(null),
        MK_P_TEST		(s.MK_P_TEST),
        MK_P_DBGTEST		(null),
        MK_P_DOC		(s.MK_P_DOC),
        MK_F_BUILD		(s.MK_F_BUILD),
        MK_F_CLEAN		(null),
        MK_F_REBUILD		(null),
        MK_F_RUN		(s.MK_F_RUN),
        MK_F_DEBUG		(s.MK_F_DEBUG),
        MK_F_TEST		(s.MK_F_TEST),
        MK_F_DBGTEST		(s.MK_F_DBGTEST),
        MK_F_DOC		(null),
        ;

        private String path;
        private boolean checked;

        private FsAct(String path)
        {
            this.path = path;
        }

        String path()
        {
            if(!(checked || path == null)) {
                FileObject fo = FileUtil.getConfigFile(path);
                if(fo == null) {
                    Logger.getLogger(FsAct.class.getName())
                        .log(Level.INFO, "delegate {0} action not found: {1}",
                            new Object[] {name(), path});
                }
                checked = true;
            }
            return path;
        }

private static class s {
public static final String EDITOR_PIN
    = "Actions/Edit/com-raelity-nbm-editor_pin-Pin.instance";

public static final String UNDO
    = "Actions/Edit/org-openide-actions-UndoAction.instance";
public static final String REDO
    = "Actions/Edit/org-openide-actions-RedoAction.instance";

public static final String GO_TYPE
    = "Actions/Edit/org-netbeans-modules-jumpto-type-GoToType.instance";

public static final String TAB_NEXT
    = "Actions/Window/"
    + "org-netbeans-core-windows-actions-NextTabAction.instance";
public static final String TAB_PREV
    = "Actions/Window/"
    + "org-netbeans-core-windows-actions-PreviousTabAction.instance";

public static final String BM_TOGGLE
    = "Actions/Edit/bookmark-toggle.instance";
public static final String BM_NEXT
    = "Actions/Edit/bookmark-next.instance";
public static final String BM_PREV
    = "Actions/Edit/bookmark-previous.instance";

public static final String WHERE_USED
    = "Actions/Refactoring/"
    + "org-netbeans-modules-refactoring-api-ui-WhereUsedAction.instance";

public static final String JUMP_NEXT
    = "Actions/System/org-netbeans-core-actions-JumpNextAction.instance";
public static final String JUMP_PREV
    = "Actions/System/org-netbeans-core-actions-JumpPrevAction.instance";

public static final String WORD_MATCH_NEXT
    = "Menu/Source/org-netbeans-modules-editor-"
    + "MainMenuAction$WordMatchNextAction.instance";
public static final String WORD_MATCH_PREV
    = "Menu/Source/org-netbeans-modules-editor-"
    + "MainMenuAction$WordMatchPrevAction.instance";

//
// refactoring
//
public static final String RF_RENAME
    = "Actions/Refactoring/"
      + "org-netbeans-modules-refactoring-api-ui-RenameAction.instance";
public static final String RF_MOVE
    = "Actions/Refactoring/"
      + "org-netbeans-modules-refactoring-api-ui-MoveAction.instance";
public static final String RF_COPY
    = "Actions/Refactoring/"
      + "org-netbeans-modules-refactoring-api-ui-CopyAction.instance";
public static final String RF_SAFE_DELETE
    = "Actions/Refactoring/"
      + "org-netbeans-modules-refactoring-api-ui-SafeDeleteAction.instance";
public static final String RF_CHANGE_PARAMETERS
    = "Actions/Refactoring/"
      + "org-netbeans-modules-refactoring-java-api-ui-ChangeParametersAction.instance";
public static final String RF_ENCAPSULATE_FIELD
    = "Actions/Refactoring/"
      + "org-netbeans-modules-refactoring-java-api-ui-EncapsulateFieldAction.instance";
//
public static final String RF_PULL_UP
    = "Actions/Refactoring/"
      + "org-netbeans-modules-refactoring-java-api-ui-PullUpAction.instance";
public static final String RF_PUSH_DOWN
    = "Actions/Refactoring/"
      + "org-netbeans-modules-refactoring-java-api-ui-PushDownAction.instance";
//
// Note: the RF_INTRODUCE_* changed for NB7
// Directory location changed, not name.
// Note, these are java only.
public static final String RF_INTRODUCE_VARIABLE
    = "Editors/text/x-java/Actions/"
    + "org-netbeans-modules-java-hints-introduce-IntroduceVariableAction.instance";
public static final String RF_INTRODUCE_CONSTANT
    = "Editors/text/x-java/Actions/"
    + "org-netbeans-modules-java-hints-introduce-IntroduceConstantAction.instance";
public static final String RF_INTRODUCE_FIELD
    = "Editors/text/x-java/Actions/"
    + "org-netbeans-modules-java-hints-introduce-IntroduceFieldAction.instance";
public static final String RF_INTRODUCE_METHOD
    = "Editors/text/x-java/Actions/"
    + "org-netbeans-modules-java-hints-introduce-IntroduceMethodAction.instance";

// build/run and such operate on three types of things
//      MK_M_xx is main project
//      MK_P_xx current projet
//      MK_F_xx current file
// note the thing-type vs operation matrix is sparse
//
// Make for main project
//
public static final String MK_M_BUILD
    = "Actions/Project/"
    + "org-netbeans-modules-project-ui-BuildMainProject.instance";
public static final String MK_M_CLEAN
    = "Actions/Project/"
    + "org-netbeans-modules-project-ui-CleanMainProject.instance";
public static final String MK_M_REBUILD
    = "Actions/Project/"
    + "org-netbeans-modules-project-ui-RebuildMainProject.instance";
public static final String MK_M_RUN
    = "Actions/Project/"
    + "org-netbeans-modules-project-ui-RunMainProject.instance";
public static final String MK_M_DEBUG
    = "Actions/Debug/"
    + "org-netbeans-modules-debugger-ui-actions-DebugMainProjectAction.instance";
public static final String MK_M_TEST = null;
public static final String MK_M_DBGTEST = null;
public static final String MK_M_DOC = null;
//
// Make for current project
//
public static final String MK_P_BUILD
    = "Actions/Project/"
    + "org-netbeans-modules-project-ui-BuildProject.instance";
public static final String MK_P_CLEAN
    = "Actions/Project/"
    + "org-netbeans-modules-project-ui-CleanProject.instance";
public static final String MK_P_REBUILD
    = "Actions/Project/"
    + "org-netbeans-modules-project-ui-RebuildProject.instance";
public static final String MK_P_RUN
    = "Actions/Project/"
    + "org-netbeans-modules-project-ui-RunProject.instance";
public static final String MK_P_DEBUG = null;
public static final String MK_P_TEST
    = "Actions/Project/"
    + "org-netbeans-modules-project-ui-TestProject.instance";
public static final String MK_P_DBGTEST = null;
public static final String MK_P_DOC
    = "Actions/Project/"
    + "org-netbeans-modules-project-ui-JavadocProject.instance";
//
// Make for current file actions
//
public static final String MK_F_BUILD
    = "Actions/Project/"
    + "org-netbeans-modules-project-ui-CompileSingle.instance";
public static final String MK_F_CLEAN = null;
public static final String MK_F_REBUILD = null;
public static final String MK_F_RUN
    = "Actions/Project/"
    + "org-netbeans-modules-project-ui-RunSingle.instance";
public static final String MK_F_DEBUG
    = "Actions/Debug/"
    + "org-netbeans-modules-debugger-ui-actions-DebugFileAction.instance";
public static final String MK_F_TEST
    = "Actions/Project/"
    + "org-netbeans-modules-project-ui-TestSingle.instance";
public static final String MK_F_DBGTEST
    = "Actions/Debug/"
    + "org-netbeans-modules-debugger-ui-actions-DebugTestFileAction.instance";
public static final String MK_F_DOC = null;

	private s()
	{
	}
    }
}
