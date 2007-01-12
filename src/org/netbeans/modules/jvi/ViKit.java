package org.netbeans.modules.jvi;

import com.raelity.jvi.swing.KeyBinding;
import javax.swing.text.Caret;
import org.netbeans.modules.editor.NbEditorKit;
import org.netbeans.editor.MultiKeymap;
import javax.swing.JEditorPane;

public class ViKit extends NbEditorKit
{ 
    public void install( JEditorPane editorPane )
    { 
        super.install(editorPane);

        Module.setupEditorPane(editorPane);
    } 

    public MultiKeymap getKeymap() 
    {
        return new /*Tracing*/NbKeymap(
                super.getKeymap(), 
                KeyBinding.getKeymap());
    }

    public Caret createCaret() {
        Caret retValue;
        
        // NEEDSWORK: don't want to require editor kits
        // Can't seem to add caret in NbViFactory.RegisterEditorPane(), 
        // it results in many exceptions. Look at that code So do it here
        retValue = new NbCaret();
        return retValue;
    }
} 
