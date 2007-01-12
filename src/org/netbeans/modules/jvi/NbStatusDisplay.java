/*
 * NbStatusDisplay.java
 *
 * Created on December 31, 2006, 6:39 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.netbeans.modules.jvi;

import com.raelity.jvi.Edit;
import com.raelity.jvi.G;
import com.raelity.jvi.Options;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViStatusDisplay;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import org.netbeans.editor.StatusBar;
import org.netbeans.editor.Utilities;

/**
 * This status display displays status messages in the EditorUI's StatusBar.
 * A few fields are added to the status bar.
 *
 * NEEDSWORK: Handle "Recording"
 * NEEDSWORK: NB StatusBar interface needs cleanup.
 *
 * @author erra
 */
public class NbStatusDisplay implements ViStatusDisplay {
    JEditorPane editorPane;

    public static final String CELL_MODE = "vi-mode";
    public static final String CELL_COMMAND = "vi-command";

    // NEEDSWORK: UU status
    public static final String CELL_UU = "vi-uu";
    public static final String UU_UU = "UMESSY";
    public static final String UU_LOCK = "ULOCK";
    
    /** Creates a new instance of NbStatusDisplay */
    public NbStatusDisplay() {
        // NEEDSWORK: UU status, monitor classic undo preference for status
        Preferences prefs = ViManager.getViFactory().getPreferences();
        prefs.addPreferenceChangeListener(new PreferenceChangeListener() {
            public void preferenceChange(PreferenceChangeEvent evt) {
                if(evt.getKey().equals(Options.classicUndoOption)) {
                    adjustUU();
                }
            }
        });
    }
    
    /**
     * NEEDSWORK: Maintain the LOCK/UU field.
     */
    public void adjustUU() {
        if(G.isClassicUndo.getBoolean()) {
            setText(CELL_UU, UU_LOCK);
        } else {
            setText(CELL_UU, UU_UU);
        }
    }

    /**
     * Use the NB JLabel and mimic the NB usage.
     * This is not localized. NB's status label needs a cleaner interface.
     */
    public void displayMode(String text) {
	// setText(CELL_MODE, text);
	String mode = text; // may be blank for command mode
	if(text.equals(Edit.VI_MODE_INSERT)) {
	    mode = "INS";
	} else if(text.equals(Edit.VI_MODE_REPLACE)) {
	    mode = "OVR";
	} else if(!text.equals("")) {
	    mode = "???";
	}
	if(mode != "") {
	    setText(StatusBar.CELL_TYPING_MODE, mode);
	} else {
	    StatusBar sb = getStatusBar();
	    if(sb != null) {
		JLabel cell = sb.getCellByName(StatusBar.CELL_TYPING_MODE);
		cell.setText(mode);
		cell.setToolTipText("Command Mode");
	    }
	}
    }

    public void displayCommand(String text) {
	setText(CELL_COMMAND, text);
    }

    public void displayStatusMessage(String text) {
	setText(StatusBar.CELL_MAIN, text);
    }

    public void displayErrorMessage(String text) {
	// NEEDSWORK: add color
	setText(StatusBar.CELL_MAIN, text);
    }

    public void clearMessage() {
	setText(StatusBar.CELL_MAIN, "");
    }

    private void setText(String cellName, String text) {
	StatusBar sb = getStatusBar();
	if(sb != null) {
	    sb.setText(cellName, text);
	}
    }

    private StatusBar getStatusBar() {
	StatusBar sb = Utilities.getEditorUI(editorPane).getStatusBar();
	if(sb != null && sb.getCellByName(CELL_COMMAND) == null) {
	    int pos = sb.getCellCount(); // should position at end
	    sb.addCell(pos, CELL_COMMAND, new String[] {"123yy'adff"});
	    // sb.addCell(1, CELL_MODE, new String[] {"Recording REPLACE"});
            
            // NEEDSWORK: undo status.
            sb.addCell(1, CELL_UU, new String[] {UU_UU, UU_LOCK});
	}
	return sb;
    }
}
