package org.netbeans.modules.jvi;

import com.raelity.jvi.ViFS;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViOutputStream;
import com.raelity.jvi.ViTextView;
import com.raelity.jvi.swing.CommandLine;
import com.raelity.jvi.swing.DefaultViFactory;
import com.raelity.jvi.swing.ViCaret;
import java.awt.Container;
import java.util.prefs.Preferences;
import javax.swing.Action;
import javax.swing.JEditorPane;
import javax.swing.SwingUtilities;
import javax.swing.text.Caret;
import org.netbeans.editor.BaseAction;
import org.openide.windows.TopComponent;

final public class NbFactory extends DefaultViFactory {
    public static final String PROP_JEP = "ViJEditorPane";
    
    NbFS fs = new NbFS();
    
    public NbFactory() {
        super((CommandLine)null);
    }
    
    public ViFS getFS() {
        return fs;
    }
    
    public void updateKeymap() {
        Module.updateKeymap();
    }
    
    public ViOutputStream createOutputStream(ViTextView tv,
                                             Object type, Object info) {
        return new NbOutputStream(tv, type.toString(), info.toString());
    }
    
    public Preferences getPreferences() {
        // return NbPreferences.forModule(ViManager.class); Not in 5.5
        Preferences retValue;
        
        retValue = super.getPreferences();
        return retValue;
    }
    
    protected ViTextView createViTextView(JEditorPane editorPane) {
        // Set up some linkage so we can clean up the editorpane
        // when the TopComponent closes.
        TopComponent tc = getEditorTopComponent(editorPane);
        if(tc != null)
            tc.putClientProperty(PROP_JEP, editorPane);
        else
            ViManager.log("createViTextView: not isBuffer");
        
        return new NbTextView(editorPane);
    }
    
    public void registerEditorPane(JEditorPane editorPane) {
        // Cursor is currently installed by editor kit
        // install cursor if neeeded
        Caret c = editorPane.getCaret();
        if( ! (c instanceof ViCaret)) {
            int offset = 0;    // current location
            int blinkRate = 500;
            if(c != null) {
                offset = c.getDot();    // current location
                blinkRate = c.getBlinkRate();
            }
            NbCaret caret = new NbCaret();
            editorPane.setCaret(caret);
            caret.setDot(offset);
            caret.setBlinkRate(blinkRate);
        }
    }
    
    public String getDisplayFilename(Object o) {
        if(o instanceof TopComponent)
            return ((TopComponent)o).getDisplayName();
        return "";
    }
    
    /** Find a TopComponent that has been activated as an editor */
    public static TopComponent getEditorTopComponent(JEditorPane editorPane) {
        TopComponent tc = null;
        Container parent = SwingUtilities
                .getAncestorOfClass(TopComponent.class, editorPane);
        while (parent != null) {
            tc = (TopComponent)parent;
            if(ViManager.isBuffer(tc))
                break;
            parent = SwingUtilities.getAncestorOfClass(TopComponent.class, tc);
        }
        return tc;
    }

    public Action createCharAction(String name) {
        Action a;
        
        a = super.createCharAction(name);
        // Don't want jVi keys treated as options
        a.putValue(BaseAction.NO_KEYBINDING, Boolean.TRUE);
        return a;
    }

    public Action createKeyAction(String name, int key) {
        Action a;
        
        a = super.createKeyAction(name, key);
        // Don't want jVi keys treated as options
        a.putValue(BaseAction.NO_KEYBINDING, Boolean.TRUE);
        return a;
    }
}
