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
import org.netbeans.modules.editor.FormatterIndentEngine;
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

    public void viOptionSet(ViTextView tv, String name) {
        Class kitC = tv.getEditorComponent().getEditorKit().getClass();
        String content = tv.getEditorComponent().getContentType();
        
        if("b_p_ts".equals(name)) {
            Settings.setValue(kitC, SettingsNames.TAB_SIZE, b_p_ts);
            Settings.setValue(kitC, SettingsNames.SPACES_PER_TAB, b_p_ts);
        } else if("b_p_sw".equals(name)) {
            //IndentEngine ie = IndentEngine.find(content);
            FormatterIndentEngine ie = fetchIndentEngine(tv);
            if(ie != null)
                ie.setSpacesPerTab(b_p_sw); // space per tab ??????
        } else if("b_p_et".equals(name)) {
            FormatterIndentEngine ie = fetchIndentEngine(tv);
            if(ie != null)
                ie.setExpandTabs(b_p_et);
        }
    }
    
    public void activateOptions(ViTextView tv) {
        // put them all out there.
        Class kitC = tv.getEditorComponent().getEditorKit().getClass();
        String content = tv.getEditorComponent().getContentType();
        
        Settings.setValue(kitC, SettingsNames.TAB_SIZE, b_p_ts);
        Settings.setValue(kitC, SettingsNames.SPACES_PER_TAB, b_p_ts);
        FormatterIndentEngine ie = fetchIndentEngine(tv);
        if(ie != null) {
            ie.setSpacesPerTab(b_p_sw); // space per tab ??????
            ie.setExpandTabs(b_p_et);
        }
    }
    
    private FormatterIndentEngine fetchIndentEngine(ViTextView tv) {
        FormatterIndentEngine fie = null;
        IndentEngine ie = IndentEngine.find(
                                    tv.getEditorComponent().getDocument());
        if(ie instanceof FormatterIndentEngine)
            fie = (FormatterIndentEngine) ie;
        return fie;
    }
    
    
    //////////////////////////////////////////////////////////////////////
    //
    // Following NOT USED but keep the code around for an example BaseOptions
    //
    
    private FormatterIndentEngine XXXfetchIndentEngine(ViTextView tv) {
        FormatterIndentEngine ie = null;
        BaseOptions bo = XXXfetchBaseOptions(tv);
        if(bo != null
            && bo.getIndentEngine() instanceof FormatterIndentEngine) {
            ie = (FormatterIndentEngine) bo.getIndentEngine();
        }
        return ie;
    }
    
    private BaseOptions XXXfetchBaseOptions(ViTextView tv) {
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
        }
        return bo;
    }
}
