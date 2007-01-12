package org.netbeans.modules.jvi;

import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.text.Keymap;
import org.netbeans.editor.MultiKeymap;

/**
 * Need an instance of MultiKeymap to return from getKeymap() in
 * the vi editor kits.
 */
public class NbKeymap extends MultiKeymap
{ 
    public NbKeymap(Keymap nbMap, Keymap viMap)
    { 
        super(viMap.getName());
        m_nbMap = nbMap;
        m_viMap = viMap;
    } 

    public void addActionForKeyStroke(KeyStroke key, Action a)
    { 
        m_nbMap.addActionForKeyStroke(key, a);
    } 

    public Action getAction(KeyStroke key)
    { 
        Action a = m_viMap.getAction(key);
        return (a != null) ? a : m_nbMap.getAction(key);
    } 

    public Action[] getBoundActions()
    { 
        Action[] a = m_viMap.getBoundActions();
        return (a != null) ? a : m_nbMap.getBoundActions();
    } 

    public KeyStroke[] getBoundKeyStrokes()
    { 
        KeyStroke[] result = m_viMap.getBoundKeyStrokes();
        return (result != null) ? result : m_nbMap.getBoundKeyStrokes();
    } 

    public Action getDefaultAction()
    { 
        Action result = m_viMap.getDefaultAction();
        return (result != null) ? result : m_nbMap.getDefaultAction();
    } 

    public KeyStroke[] getKeyStrokesForAction(Action a)
    { 
        KeyStroke[] result = m_viMap.getKeyStrokesForAction(a);
        return (result != null) ? result : m_nbMap.getKeyStrokesForAction(a);
    } 

    public String getName()
    { 
        String result = m_viMap.getName();
        return (result != null) ? result : m_nbMap.getName();
    } 

    public Keymap getResolveParent()
    { 
        Keymap result = m_viMap.getResolveParent();
        return (result != null) ? result : m_nbMap.getResolveParent();
    } 

    public boolean isLocallyDefined(KeyStroke key)
    { 
        return m_viMap.isLocallyDefined(key);
    } 

    public void removeBindings()
    { 
        m_nbMap.removeBindings();
        m_viMap.removeBindings();
    } 

    public void removeKeyStrokeBinding(KeyStroke keys)
    { 
        m_nbMap.removeKeyStrokeBinding(keys);
        m_viMap.removeKeyStrokeBinding(keys);
    } 

    public void setDefaultAction(Action a)
    { 
        m_viMap.setDefaultAction(a);
    } 

    public void setResolveParent(Keymap parent)
    { 
        m_viMap.setResolveParent(parent);
    } 


    protected Keymap m_nbMap, m_viMap;
} 
