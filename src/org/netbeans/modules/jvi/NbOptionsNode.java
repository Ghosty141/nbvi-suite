package org.netbeans.modules.jvi;

import com.raelity.jvi.OptionsBean;
import com.raelity.jvi.swing.KeyBindingBean;
import com.raelity.jvi.swing.KeypadBindingBean;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import org.openide.ErrorManager;
import org.openide.nodes.BeanNode;
import org.openide.nodes.Children;
import org.openide.nodes.Node;

public class NbOptionsNode extends BeanNode {
    private static final String NODE_MISC = "Misc";
    private static final String NODE_SEARCH = "Search";
    private static final String NODE_CURSOR_WRAP = "CursorWrap";
    private static final String NODE_BANG = "Bang (!) Commands";
    private static final String NODE_KEY_BINDINGS = "KeyBindings";
    private static final String NODE_KEYPAD_BINDINGS = "KeypadBindings";
    private static final String NODE_DEBUG = "Debug";
    
    private static void putEx(PropertyVetoException pve) {
        String msg = pve.getMessage();
        RuntimeException iae = new IllegalArgumentException( msg); //NOI18N
        ErrorManager.getDefault().annotate(iae,
	        ErrorManager.USER, msg,
	        msg, pve, new java.util.Date());
        throw iae;
    }
    
    public NbOptionsNode() throws IntrospectionException {
	super(new OptionsBean.General() {
                  protected void put(String name, int val) {
                      try {
                          super.put(name, val);
                      } catch (PropertyVetoException pve) {
                          putEx(pve);
                      }
                  }
                  protected void put(String name, String val) {
                      try {
                          super.put(name, val);
                      } catch (PropertyVetoException pve) {
                          putEx(pve);
                      }
                  }
              },
              new OptionsSubnodes());
    }    
    
    private static class OptionsSubnodes extends Children.Keys {
        protected Node[] createNodes(Object object) {
            Node[] nodes = new Node[1];
            try {
                if(object.equals(NODE_KEY_BINDINGS)) {
		    nodes[0] = new KeyBindingNode();
                } else if(object.equals(NODE_KEYPAD_BINDINGS)) {
		    nodes[0] = new KeypadBindingNode();
                } else if(object.equals(NODE_MISC)) {
		    nodes[0] = new MiscNode();
                } else if(object.equals(NODE_SEARCH)) {
		    nodes[0] = new SearchNode();
                } else if(object.equals(NODE_CURSOR_WRAP)) {
		    nodes[0] = new CursorWrapNode();
                } else if(object.equals(NODE_BANG)) {
		    nodes[0] = new BangNode();
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
           c.add(NODE_SEARCH);
           c.add(NODE_CURSOR_WRAP);
           c.add(NODE_BANG);
           c.add(NODE_KEY_BINDINGS);
           c.add(NODE_KEYPAD_BINDINGS);
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
    
    private static class KeypadBindingNode extends BeanNode {
	public KeypadBindingNode() throws IntrospectionException {
	    super(new KeypadBindingBean());
	}    
    }
    
    private static class SearchNode extends BeanNode {
	public SearchNode() throws IntrospectionException {
	    super(new OptionsBean.Search() {
                protected void put(String name, int val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
                protected void put(String name, String val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
            });
	}    
    }
    
    private static class MiscNode extends BeanNode {
	public MiscNode() throws IntrospectionException {
	    super(new OptionsBean.Misc() {
                protected void put(String name, int val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
                protected void put(String name, String val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
            });
	}    
    }
    
    private static class CursorWrapNode extends BeanNode {
	public CursorWrapNode() throws IntrospectionException {
	    super(new OptionsBean.CursorWrap() {
                protected void put(String name, int val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
                protected void put(String name, String val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
            });
	}    
    }

    private static class BangNode extends BeanNode {
	public BangNode() throws IntrospectionException {
	    super(new OptionsBean.BangOptions() {
                protected void put(String name, int val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
                protected void put(String name, String val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
            });
	}    
    }

    private static class DebugNode extends BeanNode {
	public DebugNode() throws IntrospectionException {
	    super(new NbDebugOptions() {
                protected void put(String name, int val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
                protected void put(String name, String val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
            });
	}    
    }
    
    // the names for the getters/setters
    private static final String GETSET_DBG_MODULE = "DebugModule";
    private static final String GETSET_DBG_TC = "DebugTC";
    
    public static class NbDebugOptions extends OptionsBean.Debug {
        public PropertyDescriptor[] getPropertyDescriptors() {
            PropertyDescriptor[]  descriptors = super.getPropertyDescriptors();
            PropertyDescriptor d01 = null;
            PropertyDescriptor d02 = null;
            try {
                d01 = createPropertyDescriptor(Module.DBG_MODULE,
                                               GETSET_DBG_MODULE,
                                               NbDebugOptions.class);
                d02 = createPropertyDescriptor(Module.DBG_TC,
                                               GETSET_DBG_TC,
                                               NbDebugOptions.class);
            } catch (IntrospectionException ex) {
                return descriptors;
            }
            PropertyDescriptor[]  d00
                    = new PropertyDescriptor[descriptors.length +2];
            System.arraycopy(descriptors, 0, d00, 0, descriptors.length);
            d00[descriptors.length] = d01;
            d00[descriptors.length +1] = d02;
            return d00;
        }
        
        public void setDebugModule(boolean arg) {
            put(Module.DBG_MODULE, arg);
        }

        public boolean getDebugModule() {
            return getboolean(Module.DBG_MODULE);
        }
        
        public void setDebugTC(boolean arg) {
            put(Module.DBG_TC, arg);
        }

        public boolean getDebugTC() {
            return getboolean(Module.DBG_TC);
        }
    }
}
