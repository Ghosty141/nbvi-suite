package org.netbeans.modules.jvi;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import org.openide.nodes.BeanNode;
import org.openide.nodes.Node;
import org.openide.nodes.Sheet;

public class NbOptionsNode extends BeanNode {
    public NbOptionsNode() throws IntrospectionException {
	super(new NbOptions());
    }    
}
