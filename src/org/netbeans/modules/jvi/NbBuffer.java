/*
 * NbBuffer.java
 *
 * Created on March 6, 2007, 11:17 AM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package org.netbeans.modules.jvi;

import com.raelity.jvi.Buffer;
import com.raelity.jvi.ViTextView;
import java.util.List;
import org.netbeans.api.editor.mimelookup.MimeLookup;
import org.netbeans.editor.Settings;
import org.netbeans.editor.SettingsNames;
import org.netbeans.modules.editor.java.JavaIndentEngine;
import org.netbeans.modules.editor.options.BaseOptions;
import org.openide.text.IndentEngine;
import org.openide.util.Lookup;

/**
 *
 * @author erra
 */
public class NbBuffer extends Buffer {
    
    /** Creates a new instance of NbBuffer */
    public NbBuffer() {
    }

    public void viOptionChange(ViTextView tv, String name) {
        System.err.println("NbBuffer: viOptionChange: name = " + name );
        Class kit = tv.getEditorComponent().getEditorKit().getClass();
        
        BaseOptions bo = findBaseOptions(tv);
        
        if("b_p_ts".equals(name)) {
            if(bo == null) {
                Settings.setValue(kit, SettingsNames.TAB_SIZE, b_p_ts);
                Settings.setValue(kit, SettingsNames.SPACES_PER_TAB, b_p_ts);
            } else {
                bo.setTabSize(b_p_ts);
                bo.setSpacesPerTab(b_p_ts);
            }
        } else if("b_p_sw".equals(name)) {
            Settings.setValue(kit, SettingsNames.INDENT_SHIFT_WIDTH, b_p_sw);
        } else if("b_p_et".equals(name)) {
            if(bo == null)
                Settings.setValue(kit, SettingsNames.EXPAND_TABS, b_p_et);
            else
                bo.setExpandTabs(b_p_et);
        }
    }
    
    public void activateOptions(ViTextView tv) {
        System.err.println("NbBuffer: activateOptions");
        // put thenm all out there.
        Class kit = tv.getEditorComponent().getEditorKit().getClass();
        Settings.setValue(kit, SettingsNames.TAB_SIZE, b_p_ts);
        Settings.setValue(kit, SettingsNames.SPACES_PER_TAB, b_p_ts);
        Settings.setValue(kit, SettingsNames.INDENT_SHIFT_WIDTH, b_p_sw);
        Settings.setValue(kit, SettingsNames.EXPAND_TABS, b_p_et);
        
        BaseOptions bo = findBaseOptions(tv);
        if(bo != null) {
            bo.setTabSize(b_p_ts);
            bo.setSpacesPerTab(b_p_ts);
            bo.setExpandTabs(b_p_et);
        }
    }
    
    private BaseOptions findBaseOptions(ViTextView tv) {
        String content = tv.getEditorComponent().getContentType();
        BaseOptions bo = null;
        
        Lookup.Result result = MimeLookup.getMimeLookup(content)
                            .lookup(new Lookup.Template(BaseOptions.class));
        
        // Its really a list (see the docs)
        // and directly access the rest of the type casts.
        
        if(result != null) {
            List instances = (List)result.allInstances();
            if(instances != null && instances.size() > 0)
                bo = (BaseOptions)instances.get(0);
            //IndentEngine ie = bo.getIndentEngine();
        }
        return bo;
    }
}
