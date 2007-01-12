/*
 * NbOps.java
 *
 * Created on January 1, 2007, 1:41 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.netbeans.modules.jvi;

import com.raelity.jvi.ViTextView;
import com.raelity.jvi.swing.OpsBase;
import javax.swing.Action;
import javax.swing.text.EditorKit;
import org.netbeans.editor.BaseKit;
import org.netbeans.modules.editor.NbEditorKit;

/**
 *
 * @author erra
 */
public class NbOps extends OpsBase {
    
    /** Creates a new instance of NbOps */
    public NbOps(ViTextView textView) {
        super(textView);
    }
    
    public void xop(int op) {
        String actionName;
        switch(op) {
            case INSERT_TEXT:
                actionName = NbEditorKit.insertContentAction;
                break;
            case KEY_TYPED:
                actionName = NbEditorKit.defaultKeyTypedAction;
                break;
            case INSERT_NEW_LINE:
                actionName = NbEditorKit.insertBreakAction;
                break;
            case INSERT_TAB:
                actionName = NbEditorKit.insertTabAction;
                break;
            case DELETE_PREVIOUS_CHAR:
                actionName = NbEditorKit.deletePrevCharAction;
                break;
            default:
                super.xop(op);
                return;
        }
        xact(actionName);
    }

    protected Action findAction(String actionName) {
        EditorKit editorKit = textView.getEditorComponent().getEditorKit();
        if(!(editorKit instanceof BaseKit)) {
            return super.findAction(actionName);
        }
        
        Action action = ((BaseKit)editorKit).getActionByName(actionName);
        if(action == null) {
	    throw new RuntimeException("Action " + actionName + "not found");
	}
        return action;
    }
}
