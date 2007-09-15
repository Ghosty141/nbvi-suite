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
    public static final String TABNEXT
        = "Actions/Window/"
        + "org-netbeans-core-windows-actions-NextTabAction.instance";
    public static final String TABPREV
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
    
    public static final String RF_RENAME
        = "Menu/Refactoring/RenameAction.instance";
    public static final String RF_MOVE
        = "Menu/Refactoring/MoveAction.instance";
    public static final String RF_COPY
        = "Menu/Refactoring/CopyAction.instance";
    public static final String RF_SAFE_DELETE
        = "Menu/Refactoring/SafeDeleteAction.instance";
    
    public static final String RF_CHANGE_PARAMETERS
        = "Menu/Refactoring/"
        + "org-netbeans-modules-refactoring-java-api-ui-ChangeParametersAction.instance";
    public static final String RF_ENCAPSULATE_FIELD
        = "Menu/Refactoring/"
        + "org-netbeans-modules-refactoring-java-api-ui-EncapsulateFieldAction.instance";
    
    public static final String RF_PULL_UP
        = "Menu/Refactoring/"
        + "org-netbeans-modules-refactoring-java-api-ui-PullUpAction.instance";
    public static final String RF_PUSH_DOWN
        = "Menu/Refactoring/"
        + "org-netbeans-modules-refactoring-java-api-ui-PushDownAction.instance";
    
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

    public static List<String> getFsActList() {
        List<String> l = new ArrayList<String>();
        
        l.add(TABNEXT);
        l.add(TABPREV);
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
        return l;
    }
}
