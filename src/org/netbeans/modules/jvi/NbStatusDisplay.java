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
import java.awt.Font;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import org.netbeans.editor.Coloring;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.StatusBar;
import org.netbeans.editor.Utilities;
import org.openide.awt.StatusDisplayer;

/**
 * This status display displays status messages in the EditorUI's StatusBar.
 * A few fields are added to the status bar dependign on useMyCells constant.
 *
 * @author erra
 */
public final class NbStatusDisplay implements ViStatusDisplay {
    // don't/do share pre-defined cell in StatusBar
    private static final boolean useMyCells = false;
    
    private ViTextView textView;
    private String lastMode = "";
    private String lastMsg = "";
    private String lastCmd = "";
    private Coloring lastMsgColoring = null;
    private String mode = "";
    private boolean fFrozen;

    // a few things for working with the netbeans status bar.
    public static final String CELL_STATUS = "vi-status";
    public static final String CELL_COMMAND = "vi-command";
    private static Coloring red = new Coloring(null, Color.red, null);
    private static Coloring green = new Coloring(new Font(null, Font.BOLD, 0),
                                                 Coloring.FONT_MODE_APPLY_STYLE,
                                                 Color.green.darker().darker(),
                                                 null);
    
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
        boolean jviOnlyMode = false;
        String tip = "";
	if(mode.equals(Edit.VI_MODE_INSERT)) {
	    nbMode = "INS";
	} else if(mode.equals(Edit.VI_MODE_REPLACE)) {
	    nbMode = "OVR";
	} else {
            jviOnlyMode = true;
            if(mode.equals("")) {
                tip = "Command Mode";
            } else if(G.VIsual_active){
                if (G.VIsual_select) {
                    nbMode = "VIS";
                    tip = "Visual Select Mode";
                } else {
                    // It may be "VISUAL, "VISUAL BLOCK" or "VISUAl LINE"
                    if (G.VIsual_mode == (0x1f & (int)('V'))) { // Ctrl('V')
                        nbMode = "VIB";
                        tip = "Visual Block Mode";
                    } else if (G.VIsual_mode == 'V') {
                        nbMode = "VIL";
                        tip = "Visual Line Mode";
                    } else {
                        nbMode = "VI";
                        tip = "Visual Mode";
                    }
                }

            }
            else {
                nbMode = "???";
                tip = "Unexpected Mode";
            }
	}
	if(!jviOnlyMode) {
	    setText(StatusBar.CELL_TYPING_MODE, nbMode);
	} else {
	    StatusBar sb = getStatusBar();
	    if(sb != null) {
		JLabel cell = sb.getCellByName(StatusBar.CELL_TYPING_MODE);
		cell.setText(nbMode);
		cell.setToolTipText(tip);
	    }
	}
    }

    public void displayCommand(String text) {
        if(useMyCells)
            setText(CELL_COMMAND, text);
        else {
            text = text.trim();
            if(text.length() != 0)
                lastMsgColoring = null;
            if(text.length() != 0)
                text = " [ " + text + " ]";
            lastCmd = text;
            refresh();
        }
    }

    public void displayStatusMessage(String text) {
        fFrozen = false;
        setMessageText(text);
    }

    public void displayErrorMessage(String text) {
        fFrozen = false;
        lastMsg = text;
        lastMsgColoring = red;
	setText(CELL_STATUS, modeString() + text, lastMsgColoring);
    }

    public void displayWarningMessage(String text) {
        fFrozen = false;
        lastMsg = text;
        lastMsgColoring = red;
	setText(CELL_STATUS, modeString() + text, lastMsgColoring);
    }

    public void displayFrozenMessage(String text) {
        fFrozen = true;
        setMessageText(text);
    }

    public void clearMessage() {
        if(fFrozen)
            return;
	setMessageText("");
    }

    public void refresh() {
        // NetBeans insertDefaultKeyAction clears the status bar after every
        // document addition. But we need to keep the "-- INSERT --" visible.
        if(!useMyCells)
            setText(CELL_STATUS, modeString() + lastMsg + lastCmd, lastMsgColoring);
    }

    private void setMessageText(String text) {
        lastMsg = text;
        lastMsgColoring = null;
	setText(CELL_STATUS, modeString() + text);
    }

    private void setText(String cellName, String text) {
        setText(cellName, text, null);
    }

    private void setText(String cellName, String text, Coloring coloring) {
        if(!useMyCells) {
            // direct CELL_STATUS messsages to the pre-defined location
            if(cellName.equals(CELL_STATUS))
                cellName = StatusBar.CELL_MAIN;
         }
	StatusBar sb = getStatusBar();
	if(sb != null) {
	    sb.setText(cellName, text, coloring);
	}

        if(!sb.getPanel().isShowing()) {
            boolean allBlank = true;
            for(int i = 0; i < text.length(); i++) {
                if(text.charAt(i) != ' ') {
                    allBlank = false;
                    break;
                }
            }
            if(!allBlank)
                StatusDisplayer.getDefault().setStatusText(text);
        }
    }

    private StatusBar getStatusBar() {
	StatusBar sb = null;
        JEditorPane ep = textView.getEditorComponent();
        if(ep != null) {
            EditorUI ui = Utilities.getEditorUI(ep);
            if(ui != null) {
                sb = ui.getStatusBar();
                if(useMyCells) {
                    // If the StatusBar does not have nbvi stuff, then add it
                    if(sb != null && sb.getCellByName(CELL_COMMAND) == null) {
                        // after StatusBar.CELL_TYPING_MODE
                        sb.addCell(2, CELL_COMMAND, new String[] {"123yy'adff"});
                        // after CELL_COMMAND
                        sb.addCell(3, CELL_STATUS,new String[] { "             "
                           + "                                               "});
                    }
                }
            }
        }
	return sb;
    }
}

// vi: ts=8 sw=4
