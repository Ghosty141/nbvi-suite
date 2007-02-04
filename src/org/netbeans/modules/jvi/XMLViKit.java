package org.netbeans.modules.jvi;

import com.raelity.jvi.swing.KeyBinding;
import javax.swing.JEditorPane;
import javax.swing.text.Caret;
import org.netbeans.editor.MultiKeymap;
import org.netbeans.modules.xml.text.syntax.XMLKit;


public class XMLViKit extends XMLKit
{ 
    public void install(JEditorPane editorPane)
    { 
        super.install(editorPane);

        Module.setupEditorPane(editorPane);
    } 

    public MultiKeymap getKeymap()
    {
        return new NbKeymap(super.getKeymap(), KeyBinding.getKeymap());
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