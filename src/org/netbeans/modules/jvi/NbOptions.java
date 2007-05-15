/*
 * NbOptions.java
 *
 * Created on January 29, 2007, 9:16 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.netbeans.modules.jvi;

import com.raelity.jvi.Options;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import org.netbeans.editor.FindSupport;
import org.netbeans.editor.SettingsNames;

/**
 *
 * Sync options between NB and jVi. At startup NB options are read and the
 * corresponding jVi are set from them. During runtime, the options are kept
 * in sync as follows:
 * <ul>
 * <li> NB <--> jVi wrapScan,ignoreCase<br/>
 *      The NB options are tracked on FindSupport.
 *      Changes to either one are sent to the other.</li>
 * </ul>
 * <p>NEEDSWORK: NB6 cleanup.
 * 
 * @author erra
 */
public class NbOptions {
    
    static boolean ignoreChangeEvent = false;
    private static boolean enabled = false;
    
    static void enable() {
        if(enabled)
            return;
        enabled = true;
        
        init();
        
        jvil = new jViListener();
        fsl = new FindSupportListener();
        
        // Listen to jVi options, and propogate some to NB
        Options.addPropertyChangeListener(jvil);
        
        
        FindSupport fs = FindSupport.getFindSupport();
        fs.addPropertyChangeListener(fsl);
    }
    
    static void disable() {
        if(!enabled)
            return;
        enabled = false;
        
        Options.removePropertyChangeListener(jvil);
        
        FindSupport fs = FindSupport.getFindSupport();
        fs.removePropertyChangeListener(fsl);
        
        jvil = null;
        fsl = null;
    }
    
    private static PropertyChangeListener jvil;
    private static PropertyChangeListener fsl;
    
    private static void init() {
        
        // NB seems to handle read only files ok, so hide this one.
        Options.getOption(Options.readOnlyHack).setHidden(true);
        
        // Since we track NB's settings, make these expert.
        // Note: NB doesn't have tabStop, it has shift width only.
        Options.getOption(Options.expandTabs).setExpert(true);
        Options.getOption(Options.shiftWidth).setExpert(true);
        Options.getOption(Options.tabStop).setExpert(true);
        
        establishFindSupportOptions();
        
        //
        // For find options
        //
        // Use deprecated org.netbeans.editor.FindSupport,
        // add PropertyChangeListener and listen on some of the
        // properties with names listed in org.netbeans.editor.SettingsNames
        // (i.e. use property names starting with FIND_)
        //
    }
    
    private static void establishFindSupportOptions() {
        FindSupport fs = FindSupport.getFindSupport();
        Object o = fs.getFindProperty(SettingsNames.FIND_WRAP_SEARCH);
        setJviOption(Options.wrapScan, o.toString());
        o = fs.getFindProperty(SettingsNames.FIND_MATCH_CASE);
        // need to invert the sense of this one
        setJviOption(Options.ignoreCase, "" + !((Boolean)o).booleanValue());
    }
    
    private static void setJviOption(String optName, String val) {
        ignoreChangeEvent = true;
        try {
            Options.getOption(optName).setValue(val);
        } finally {
            ignoreChangeEvent = false;
        }
    }
    
    /** Listener to jVi properties to propagate changes to NB */
    
    private static class jViListener
    implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent evt) {
            if(ignoreChangeEvent)
                return;
            
            // propogate change to NB
            ignoreChangeEvent = true;
            try {
                if(evt.getPropertyName().equals(Options.wrapScan)) {
                    FindSupport fs = FindSupport.getFindSupport();
                    fs.putFindProperty(SettingsNames.FIND_WRAP_SEARCH,
                        evt.getNewValue());
                } else if(evt.getPropertyName().equals(Options.ignoreCase)) {
                    FindSupport fs = FindSupport.getFindSupport();
                    Boolean b = (Boolean)evt.getNewValue();
                    fs.putFindProperty(SettingsNames.FIND_MATCH_CASE,
                            new Boolean(!b));
                }
            } finally {
                ignoreChangeEvent = false;
            }
        }
    };
    
    private static class FindSupportListener
    implements PropertyChangeListener{
        public void propertyChange(PropertyChangeEvent evt) {
            if(ignoreChangeEvent)
                return;
            String prop = evt.getPropertyName();
            String optName = null;
            if(prop == SettingsNames.FIND_WRAP_SEARCH) {
                optName = Options.wrapScan;
            } else if(prop == SettingsNames.FIND_MATCH_CASE) {
                // Need to invert this one
                setJviOption(Options.ignoreCase,
                     "" + ! Boolean.getBoolean(evt.getNewValue().toString()));
            } else if(prop == null) {
                establishFindSupportOptions();
            }
            if(optName != null) {
                setJviOption(optName, evt.getNewValue().toString());
            }
        }
    }
}
