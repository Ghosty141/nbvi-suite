package org.netbeans.modules.jvi;

import java.util.Map;
import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.text.JTextComponent;
import javax.swing.text.Keymap;

public class TracingNbViKeymap extends NbKeymap 
{
    
    public TracingNbViKeymap(Keymap nbMap, Keymap viMap) {
        super(nbMap, viMap);
        System.err.println("TracingNbViKeymap.<init>("+nbMap+","+viMap+")");
    }
    
    public void setResolveParent(Keymap parent) {
        System.err.println("TracingNbViKeymap.setResolveParent("+parent+")");
        super.setResolveParent(parent);
    }

    public void load(JTextComponent.KeyBinding[] bindings, Action[] actions) {
        System.err.println("TracingNbViKeymap.load("+bindings+","+actions+")");
        super.load(bindings, actions);
    }

    public void addActionForKeyStroke(KeyStroke key, Action a) {
        System.err.println("TracingNbViKeymap.addActionForKeyStroke("+key+","+a+")");
        super.addActionForKeyStroke(key, a);
    }

    public void setContextKeyNotFoundAction(Action a) {
        System.err.println("TracingNbViKeymap.setContextKeyNotFoundAction("+a+")");
        super.setContextKeyNotFoundAction(a);
    }

    public KeyStroke[] getKeyStrokesForAction(Action a) {
        KeyStroke[] retValue;
        retValue = super.getKeyStrokesForAction(a);
        System.err.println("TracingNbViKeymap.getKeyStrokesForAction("+a+") := "+retValue);        
        return retValue;
    }

    public void setDefaultAction(Action a) {
        System.err.println("TracingNbViKeymap.setDefaultAction("+a+")");        
        super.setDefaultAction(a);
    }

    public void load(JTextComponent.KeyBinding[] bindings, Map actions) {
        System.err.println("TracingNbViKeymap.load("+bindings+","+actions+")");
        super.load(bindings, actions);
    }

    public Action getAction(KeyStroke key) {
        Action retValue;        
        retValue = super.getAction(key);
        System.err.println("TracingNbViKeymap.getAction("+key+"):="+retValue);        
        return retValue;
    }

    public void removeKeyStrokeBinding(KeyStroke keys) {
        System.err.println("TracingNbViKeymap.removeKeyStrokeBinding("+keys+")");        
        super.removeKeyStrokeBinding(keys);
    }

    public boolean isLocallyDefined(KeyStroke key) {
        boolean retValue;        
        retValue = super.isLocallyDefined(key);
        System.err.println("TracingNbViKeymap.isLocallyDefined("+key+"):="+retValue);        
        return retValue;
    }

    public Keymap getResolveParent() {
        Keymap retValue;        
        retValue = super.getResolveParent();
        System.err.println("TracingNbViKeymap.getResolveParent():="+retValue);        
        return retValue;
    }

    public void resetContext() {
        System.err.println("TracingNbViKeymap.resetContext()");        
        super.resetContext();
    }

    public void removeBindings() {
        System.err.println("TracingNbViKeymap.removeBindings()");        
        super.removeBindings();
    }

    public KeyStroke[] getBoundKeyStrokes() {
        KeyStroke[] retValue;        
        retValue = super.getBoundKeyStrokes();
        System.err.println("TracingNbViKeymap.getBoundKeyStrokes():="+retValue);        
        return retValue;
    }

    public Action[] getBoundActions() {
        Action[] retValue;
        retValue = super.getBoundActions();
        System.err.println("TracingNbViKeymap.getBoundActions():="+retValue);        
        return retValue;
    }

    public Action getDefaultAction() {
        Action retValue;        
        retValue = super.getDefaultAction();
        System.err.println("TracingNbViKeymap.getDefaultAction():="+retValue);        
        return retValue;
    }

    public String getName() {
        String retValue;        
        retValue = super.getName();
        System.err.println("TracingNbViKeymap.getName():="+retValue);        
        return retValue;
    }

}
