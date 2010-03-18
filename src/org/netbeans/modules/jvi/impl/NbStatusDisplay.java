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

package org.netbeans.modules.jvi.impl;

import com.raelity.jvi.core.Edit;
import com.raelity.jvi.core.G;
import com.raelity.jvi.core.Util;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.ViStatusDisplay;
import com.raelity.jvi.ViTextView;
import java.awt.Color;
import java.awt.Font;
import javax.swing.JEditorPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.netbeans.editor.Coloring;
import org.netbeans.editor.EditorUI;
import org.netbeans.editor.StatusBar;
import org.netbeans.editor.Utilities;
import org.openide.awt.StatusDisplayer;

/**
 * This status display displays status messages in the EditorUI's StatusBar.
 * In some cases the Editor's StatusBar is not available, so use the
 * common StatusDisplayer.
 *
 * @author erra
 */
public final class NbStatusDisplay implements ViStatusDisplay {
    private ViTextView textView;
    private String lastMode = "";
    private String lastMsg = "";
    private String lastCmd = "";
    private Coloring lastMsgColoring = null;
    private String mode = "";
    private boolean fFrozen;

    // NOTE: StatusDisplayer.Message
    private static StatusDisplayer.Message sdMsg;
    private static ChangeListener listener;

    // a few things for working with the netbeans status bar.
    private static Coloring red = new Coloring(null, Color.red, null);
    private static Coloring green = new Coloring(new Font(null, Font.BOLD, 0),
                                                 Coloring.FONT_MODE_APPLY_STYLE,
                                                 Color.green.darker().darker(),
                                                 null);

    // NEEDSWORK: it looks like in NB6.7 a JEditorPane always has
    //            a StatusBar, though it may delegate to StatusDisplayer
    
    /** Creates a new instance of NbStatusDisplay */
    public NbStatusDisplay(ViTextView textView) {
        this.textView = textView;
        displayMode(""); // Start out in command mode
        if(false) {
            if(listener == null)
                listener = new ChangeListener()
                {
                    public void stateChanged(ChangeEvent e)
                    {
                        StatusDisplayer sd = (StatusDisplayer)e.getSource();
                    }
                };
            StatusDisplayer.getDefault().addChangeListener(listener);
        }
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
	setText(StatusBar.CELL_MAIN, modeString());
        
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
	} else if(mode.equals(ViManager.getFactory()
                .getPlatformSelectionDisplayName())) {
            nbMode = "";
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
                    if (G.VIsual_mode == (Util.ctrl('V'))) {
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
	    setText(StatusBar.CELL_TYPING_MODE, nbMode);

            // Forget the tooltip for both 6.5 and 6.7
	    // StatusBar sb = getStatusBar();
	    // if(sb != null) {
	    //     JLabel cell = sb.getCellByName(StatusBar.CELL_TYPING_MODE);
	    //     cell.setText(nbMode);
	    //     cell.setToolTipText(tip);
	    // }
	}
    }

    public void displayCommand(String text) {
        text = text.trim();
        if(text.length() != 0)
            lastMsgColoring = null;
        if(text.length() != 0)
            text = " [ " + text + " ]";
        lastCmd = text;
        refresh();
    }

    public void displayStatusMessage(String text) {
        fFrozen = false;
        setMessageText(text);
    }

    public void displayErrorMessage(String text) {
        fFrozen = false;
        lastMsg = text;
        lastMsgColoring = red;
	setText(StatusBar.CELL_MAIN, modeString() + text, lastMsgColoring);
    }

    public void displayWarningMessage(String text) {
        fFrozen = false;
        lastMsg = text;
        lastMsgColoring = red;
	setText(StatusBar.CELL_MAIN, modeString() + text, lastMsgColoring);
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

    public void clearDisplay() {
        // don't change any status state, just do it
        setText(StatusBar.CELL_MAIN, "");
    }

    public void refresh() {
        setText(StatusBar.CELL_MAIN, modeString() + lastMsg + lastCmd, lastMsgColoring);
    }

    private void setMessageText(String text) {
        lastMsg = text;
        lastMsgColoring = null;
	setText(StatusBar.CELL_MAIN, modeString() + text);
    }

    private void setText(String cellName, String text) {
        setText(cellName, text, null);
    }

    private void setText(String cellName, String text, Coloring coloring) {
	StatusBar sb = getStatusBar();
        if(sb != null) {
            // Only use alternate for CELL_MAIN and when sb not visible
            boolean useAlternate = false;
            if(StatusBar.CELL_MAIN.equals(cellName)
                    && !sb.isVisible())
                useAlternate = true;

            boolean allBlank = true;
            for(int i = 0; i < text.length(); i++) {
                if(text.charAt(i) != ' ') {
                    allBlank = false;
                    break;
                }
            }
            if(sdMsg != null && StatusBar.CELL_MAIN.equals(cellName)) {
                // clear a previous message
                sdMsg.clear(0);
                sdMsg = null;
            }
            if(useAlternate) {
                // message was just cleared, so nothing to do if new msg blank
                if(!allBlank) {
                    sdMsg = StatusDisplayer.getDefault()
                            .setStatusText(text, 1);
                }
            } else {
                sb.setText(cellName, text, coloring);
            }
        }
    }

    private StatusBar getStatusBar() {
	StatusBar sb = null;
        JEditorPane ep = (JEditorPane)textView.getEditorComponent();
        if(ep != null) {
            EditorUI ui = Utilities.getEditorUI(ep);
            if(ui != null) {
                sb = ui.getStatusBar();
            }
        }
	return sb;
    }
}

// vi: ts=8 sw=4