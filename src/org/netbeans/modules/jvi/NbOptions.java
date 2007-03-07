/*
 * NbOptions.java
 *
 * Created on January 29, 2007, 9:16 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.netbeans.modules.jvi;

import com.raelity.jvi.Option;
import com.raelity.jvi.Options;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.editor.FindSupport;
import org.netbeans.editor.SettingsNames;
import org.netbeans.editor.ext.java.JavaFormatter;
import org.netbeans.modules.editor.java.JavaIndentEngine;
import org.netbeans.modules.editor.options.BaseOptions;
import org.openide.util.Lookup;

/**
 * Sync options between NB and jVi. At startup NB options are read and the
 * corresponding jVi are set from them. During runtime, the options are kept
 * in sync as follows:
 * <ul>
 * <li> NB --> jVi expandTabs,shiftWidth <br/>
 *      The NB options are tracked on JavaIndentEngine. Note that change to the
 *      jVi options are not sync'd back to NB.</li>
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
    private static JavaIndentEngine ieHACK; /////////////////////////
    
    static void enable() {
        if(enabled)
            return;
        enabled = true;
        
        init();
        
        jvil = new jViListener();
        iel = new IndentEngineListener();
        fsl = new FindSupportListener();
        
        // Listen to jVi options, and propogate some to NB
        Options.getOptions().addPropertyChangeListener(jvil);
        
        JavaIndentEngine ie = findJavaIndentEngine();
        ie.addPropertyChangeListener(iel);
        
        FindSupport fs = FindSupport.getFindSupport();
        //fs.addPropertyChangeListener(SettingsNames.FIND_WRAP_SEARCH, l);
        //fs.addPropertyChangeListener(SettingsNames.FIND_MATCH_CASE, l);
        fs.addPropertyChangeListener(fsl);
    }
    
    static void disable() {
        if(!enabled)
            return;
        enabled = false;
        
        ieHACK = null;
        
        Options.getOptions().removePropertyChangeListener(jvil);
        
        JavaIndentEngine ie = findJavaIndentEngine();
        ie.removePropertyChangeListener(iel);
        
        FindSupport fs = FindSupport.getFindSupport();
        //fs.addPropertyChangeListener(SettingsNames.FIND_WRAP_SEARCH, l);
        //fs.addPropertyChangeListener(SettingsNames.FIND_MATCH_CASE, l);
        fs.removePropertyChangeListener(fsl);
        
        jvil = null;
        iel = null;
        fsl = null;
    }
    
    private static PropertyChangeListener jvil;
    private static PropertyChangeListener iel;
    private static PropertyChangeListener fsl;
    
    private static JavaIndentEngine findJavaIndentEngine() {
        // 
        // BaseOptions bo = MimeLookup.getLookup(MimePath.parse("text/x-java"))
        //                                    .lookup(BaseOptions.class);
        // IndentEngine ie = bo.getIndentEngine();
        // ie.addPropertyChangeListener(yourListener);
        // BUT MimePath does not exist on NB55
        //
        Lookup.Result result = MimeLookup.getMimeLookup("text/x-java")
                            .lookup(new Lookup.Template(BaseOptions.class));
        
        // Its really a list (see the docs)
        // and directly access the rest of the type casts.
        
        List instances = (List)result.allInstances();
        BaseOptions bo = (BaseOptions)instances.get(0);
        JavaIndentEngine ie = (JavaIndentEngine)bo.getIndentEngine();
        
        return ie;
    }
    
    private static void init() {
        if(true)
            return;
        
        // NB seems to handle read only files ok, so hide this one.
        Options.getOption(Options.readOnlyHack).setHidden(true);
        
        // Since we track NB's settings, make these expert.
        // Note: NB doesn't have tabStop, it has shift width only.
        Options.getOption(Options.expandTabs).setExpert(true);
        Options.getOption(Options.shiftWidth).setExpert(true);
        Options.getOption(Options.tabStop).setExpert(true);
        
        //
        // For indentation
        //
        // At least for now, take the simple course and use the Java
        // indent engine settings to propogate to jVi. Note that the
        // jVi settings are not propogated back.
        
        JavaIndentEngine ie = findJavaIndentEngine();
        ieHACK = ie; // so the listener will stick to something
        
        // fetch the options we care about
        int sw = ie.getSpacesPerTab();
        boolean et = ((JavaFormatter)ie.getFormatter()).expandTabs();
        
        //
        // Set the property from the current setting,
        // this seems to best way to handle it, at least for now.
        //
        setJviOption(Options.expandTabs, ""+et);
        setJviOption(Options.shiftWidth, ""+sw);
        
        //
        // For find options
        //
        // Use deprecated org.netbeans.editor.FindSupport,
        // add PropertyChangeListener and listen on some of the
        // properties with names listed in org.netbeans.editor.SettingsNames
        // (i.e. use property names starting with FIND_)
        //
        establishFindSupportOptions();
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
    
    private static class IndentEngineListener
    implements PropertyChangeListener {
        public void propertyChange(PropertyChangeEvent evt) {
            if(ignoreChangeEvent)
                return;
            String prop = evt.getPropertyName();
            String optName = null;
            if(prop == JavaIndentEngine.EXPAND_TABS_PROP) {
                optName = Options.expandTabs;
            } else if(prop == JavaIndentEngine.SPACES_PER_TAB_PROP) {
                optName = Options.shiftWidth;
            }
            if(optName != null) {
                setJviOption(optName, evt.getNewValue().toString());
            }
        }
    }
    
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
