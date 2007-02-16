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
import com.raelity.jvi.ViStatusDisplay;
import com.raelity.jvi.ViTextView;
import java.awt.Color;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import org.netbeans.editor.Coloring;
import org.netbeans.editor.EditorUI;
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
    private ViTextView textView;
    private String lastMode = "";
    private String lastMsg = "";
    private Coloring lastMsgColoring = null;
    private String mode = "";

    // a few things for working with the netbeans status bar.
    // public static final String CELL_MODE = "vi-mode";
    public static final String CELL_STATUS = "vi-status";
    public static final String CELL_COMMAND = "vi-command";
    private static Coloring red = new Coloring(null, Color.red, null);
    
    /** Creates a new instance of NbStatusDisplay */
    public NbStatusDisplay(ViTextView textView) {
        this.textView = textView;
        displayMode(""); // Start out in command mode
    }

    private String modeString() {
        return mode + (G.Recording  ? "recording " : "");
    }

    /**
     * Use the NB JLabel and mimic the NB usage.
     * This is not localized. NB's status label needs a cleaner interface.
     */
    public void displayMode(String mode) {
	// setText(CELL_MODE, text);
        
        // Keep track of the mode we're in
        if( ! mode.equals(lastMode))
            lastMode = mode;
        if( ! mode.equals(""))
            this.mode = "-- " + mode + " -- ";
        else
            this.mode = "";
        lastMsg = "";       // clear lastMsg when mode is set
	setText(CELL_STATUS, modeString());
        
        //
        // Do that 3 char label in NetBeans' StatusBar
        // Poke at some internal StatusBar structures
        // 
	String nbMode = mode; // may be blank for command mode
	if(mode.equals(Edit.VI_MODE_INSERT)) {
	    nbMode = "INS";
	} else if(mode.equals(Edit.VI_MODE_REPLACE)) {
	    nbMode = "OVR";
	} else if(!mode.equals("")) {
	    nbMode = "???";
	}
	if(nbMode != "") {
	    setText(StatusBar.CELL_TYPING_MODE, nbMode);
	} else {
	    StatusBar sb = getStatusBar();
	    if(sb != null) {
		JLabel cell = sb.getCellByName(StatusBar.CELL_TYPING_MODE);
		cell.setText(nbMode);
		cell.setToolTipText("Command Mode");
	    }
	}
    }

    public void displayCommand(String text) {
	setText(CELL_COMMAND, text);
    }

    public void displayStatusMessage(String text) {
        lastMsg = text;
        lastMsgColoring = null;
	setText(CELL_STATUS, modeString() + text);
    }

    public void displayErrorMessage(String text) {
        lastMsg = text;
        lastMsgColoring = red;
	setText(CELL_STATUS, modeString() + text, red);
    }

    public void clearMessage() {
	displayStatusMessage("");
    }

    private void setText(String cellName, String text) {
        setText(cellName, text, null);
        /*
	StatusBar sb = getStatusBar();
	if(sb != null) {
	    sb.setText(cellName, text);
	}
         */
    }

    private void setText(String cellName, String text, Coloring coloring) {
        /*
        // use this to direct CELL_STATUS messsages to the default location
        if(cellName == CELL_STATUS)
            cellName = StatusBar.CELL_MAIN;
         */
	StatusBar sb = getStatusBar();
	if(sb != null) {
	    sb.setText(cellName, text, coloring);
	}
    }

    private StatusBar getStatusBar() {
	StatusBar sb = null;
        JEditorPane ep = textView.getEditorComponent();
        if(ep != null) {
            EditorUI ui = Utilities.getEditorUI(ep);
            if(ui != null) {
                sb = ui.getStatusBar();
                // If the StatusBar does not have nbvi stuff, then add it
                if(sb != null && sb.getCellByName(CELL_COMMAND) == null) {
                    int pos = sb.getCellCount(); // should position at end
                    sb.addCell(pos, CELL_COMMAND, new String[] {"123yy'adff"});
                    // this should be before CELL_COMMAND
                    sb.addCell(pos, CELL_STATUS,new String[]
                        {"                                                            "});
                    // sb.addCell(1, CELL_MODE, new String[] {"Recording REPLACE"});
                }
            }
        }
	return sb;
    }
}
