package org.netbeans.modules.jvi;

import com.raelity.jvi.OptionsBean;
import com.raelity.jvi.swing.KeyBindingBean;
import java.beans.IntrospectionException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.openide.ErrorManager;
import org.openide.nodes.BeanNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;

public class NbOptionsNode extends BeanNode {
    private static final String NODE_MISC = "Misc";
    private static final String NODE_CURSOR_WRAP = "CursorWrap";
    private static final String NODE_KEYBINDINGS = "KeyBindings";
    private static final String NODE_DEBUG = "Debug";
    
    public NbOptionsNode() throws IntrospectionException {
	super(new OptionsBean.General(), new OptionsSubnodes());
    }    
    
    private static class OptionsSubnodes extends Children.Keys {
        protected Node[] createNodes(Object object) {
            Node[] nodes = new Node[1];
            try {
                if(object.equals(NODE_KEYBINDINGS)) {
		    nodes[0] = new KeyBindingNode();
                } else if(object.equals(NODE_MISC)) {
		    nodes[0] = new MiscNode();
                } else if(object.equals(NODE_CURSOR_WRAP)) {
		    nodes[0] = new CursorWrapNode();
                } else if(object.equals(NODE_DEBUG)) {
		    nodes[0] = new DebugNode();
                }
            } catch (IntrospectionException ex) {
                ErrorManager.getDefault().notify(ex);
            }
            return nodes;
        }

        protected void addNotify() {
           Collection c = new ArrayList();
           c.add(NODE_MISC);
           c.add(NODE_CURSOR_WRAP);
           c.add(NODE_KEYBINDINGS);
           c.add(NODE_DEBUG);
           setKeys(c);
        }

        protected void removeNotify() {
            setKeys(Collections.EMPTY_SET);
        }
    }
    
    private static class KeyBindingNode extends BeanNode {
	public KeyBindingNode() throws IntrospectionException {
	    super(new KeyBindingBean());
	}    
    }
    
    private static class MiscNode extends BeanNode {
	public MiscNode() throws IntrospectionException {
	    super(new OptionsBean.Misc());
	}    
    }
    
    private static class CursorWrapNode extends BeanNode {
	public CursorWrapNode() throws IntrospectionException {
	    super(new OptionsBean.CursorWrap());
	}    
    }
    
    private static class DebugNode extends BeanNode {
	public DebugNode() throws IntrospectionException {
	    super(new OptionsBean.Debug());
	}    
    }
}
