/*
 * FsAct.java
 * 
 * Created on Sep 14, 2007, 5:08:14 PM
 * 
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.netbeans.modules.jvi;

import java.util.ArrayList;
import java.util.List;

/**
 * This class contains the paths for NetBeans actions used by jVi.
 * 
 * @author erra
 */
public class FsAct {
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
                = "/Actions/Edit/bookmark-toggle.instance";
    public static final String BM_NEXT
                = "/Actions/Edit/bookmark-next.instance";
    public static final String BM_PREV
                = "/Actions/Edit/bookmark-previous.instance";
    
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
        = "Menu/Refactoring/RenameAction.instance";
    public static final String RF_MOVE
        = "Menu/Refactoring/MoveAction.instance";
    public static final String RF_COPY
        = "Menu/Refactoring/CopyAction.instance";
    public static final String RF_SAFE_DELETE
        = "Menu/Refactoring/SafeDeleteAction.instance";
    //
    public static final String RF_CHANGE_PARAMETERS
        = "Menu/Refactoring/"
        + "org-netbeans-modules-refactoring-java-api-ui-ChangeParametersAction.instance";
    public static final String RF_ENCAPSULATE_FIELD
        = "Menu/Refactoring/"
        + "org-netbeans-modules-refactoring-java-api-ui-EncapsulateFieldAction.instance";
    //
    public static final String RF_PULL_UP
        = "Menu/Refactoring/"
        + "org-netbeans-modules-refactoring-java-api-ui-PullUpAction.instance";
    public static final String RF_PUSH_DOWN
        = "Menu/Refactoring/"
        + "org-netbeans-modules-refactoring-java-api-ui-PushDownAction.instance";
    //
    public static final String RF_INTRODUCE_VARIABLE
        = "Actions/Refactoring/"
        + "org-netbeans-modules-java-hints-introduce-IntroduceVariableAction.instance";
    public static final String RF_INTRODUCE_CONSTANT
        = "Actions/Refactoring/"
        + "org-netbeans-modules-java-hints-introduce-IntroduceConstantAction.instance";
    public static final String RF_INTRODUCE_FIELD
        = "Actions/Refactoring/"
        + "org-netbeans-modules-java-hints-introduce-IntroduceFieldAction.instance";
    public static final String RF_INTRODUCE_METHOD
        = "Actions/Refactoring/"
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

    // This is used to check that all listed actions can be found only once.
    public static List<String> getFsActList() {
        List<String> l = new ArrayList<String>();
        
        l.add(UNDO);
        l.add(REDO);

        l.add(GO_TYPE);

        l.add(TAB_NEXT);
        l.add(TAB_PREV);

        l.add(WHERE_USED);

        l.add(BM_TOGGLE);
        l.add(BM_NEXT);
        l.add(BM_PREV);

        l.add(JUMP_NEXT);
        l.add(JUMP_PREV);

        l.add(RF_RENAME);
        l.add(RF_MOVE);
        l.add(RF_COPY);
        l.add(RF_SAFE_DELETE);
        l.add(RF_CHANGE_PARAMETERS);
        l.add(RF_ENCAPSULATE_FIELD);
        l.add(RF_PULL_UP);
        l.add(RF_PUSH_DOWN);
        l.add(RF_INTRODUCE_VARIABLE);
        l.add(RF_INTRODUCE_CONSTANT);
        l.add(RF_INTRODUCE_FIELD);
        l.add(RF_INTRODUCE_METHOD);

        l.add(MK_M_BUILD);
        l.add(MK_M_CLEAN);
        l.add(MK_M_REBUILD);
        l.add(MK_M_RUN);
        l.add(MK_M_DEBUG);
        l.add(MK_M_TEST);
        l.add(MK_M_DBGTEST);
        l.add(MK_M_DOC);
        l.add(MK_P_BUILD);
        l.add(MK_P_CLEAN);
        l.add(MK_P_REBUILD);
        l.add(MK_P_RUN);
        l.add(MK_P_DEBUG);
        l.add(MK_P_TEST);
        l.add(MK_P_DBGTEST);
        l.add(MK_P_DOC);
        l.add(MK_F_BUILD);
        l.add(MK_F_CLEAN);
        l.add(MK_F_REBUILD);
        l.add(MK_F_RUN);
        l.add(MK_F_DEBUG);
        l.add(MK_F_TEST);
        l.add(MK_F_DBGTEST);
        l.add(MK_F_DOC);

        // some nulls may have been added.
        while(l.remove(null)) { }
        return l;
    }
}
