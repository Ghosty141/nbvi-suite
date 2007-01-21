package org.netbeans.modules.jvi;

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
    public static final String NODE_KEYBINDINGS = "KeyBindings";
    public NbOptionsNode() throws IntrospectionException {
	super(new NbOptions(), new OptionsSubnodes());
    }    
    
    private static class OptionsSubnodes extends Children.Keys {
        protected Node[] createNodes(Object object) {
            Node[] nodes = new Node[1];
            try {
                if(object.equals(NODE_KEYBINDINGS)) {
		    nodes[0] = new KeyBindingNode();
                }
            } catch (IntrospectionException ex) {
                ErrorManager.getDefault().notify(ex);
            }
            return nodes;
        }

        protected void addNotify() {
           Collection c = new ArrayList();
           c.add(NODE_KEYBINDINGS);
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
}
