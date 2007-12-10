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
    private static final String NODE_GENERAL = "General";
    private static final String NODE_MODIFY = "Modify";
    private static final String NODE_SEARCH = "Search";
    private static final String NODE_CURSOR_WRAP = "CursorWrap";
    private static final String NODE_EXTERNAL_PROCESS = "External Process";
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
	super(new OptionsBean.Platform() {
            @Override
                  protected void put(String name, int val) {
                      try {
                          super.put(name, val);
                      } catch (PropertyVetoException pve) {
                          putEx(pve);
                      }
                  }
            @Override
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
        @Override
        protected Node[] createNodes(Object object) {
            Node[] nodes = new Node[1];
            try {
                if(object.equals(NODE_KEY_BINDINGS)) {
		    nodes[0] = new KeyBindingNode();
                } else if(object.equals(NODE_KEYPAD_BINDINGS)) {
		    nodes[0] = new KeypadBindingNode();
                } else if(object.equals(NODE_GENERAL)) {
		    nodes[0] = new GeneralNode();
                } else if(object.equals(NODE_MODIFY)) {
		    nodes[0] = new ModifyNode();
                } else if(object.equals(NODE_SEARCH)) {
		    nodes[0] = new SearchNode();
                } else if(object.equals(NODE_CURSOR_WRAP)) {
		    nodes[0] = new CursorWrapNode();
                } else if(object.equals(NODE_EXTERNAL_PROCESS)) {
		    nodes[0] = new ExternalProcessNode();
                } else if(object.equals(NODE_DEBUG)) {
		    nodes[0] = new DebugNode();
                }
            } catch (IntrospectionException ex) {
                ErrorManager.getDefault().notify(ex);
            }
            return nodes;
        }

        @Override
        protected void addNotify() {
           Collection<String> c = new ArrayList<String>();
           c.add(NODE_GENERAL);
           c.add(NODE_MODIFY);
           c.add(NODE_SEARCH);
           c.add(NODE_CURSOR_WRAP);
           c.add(NODE_EXTERNAL_PROCESS);
           c.add(NODE_KEY_BINDINGS);
           c.add(NODE_KEYPAD_BINDINGS);
           c.add(NODE_DEBUG);
           setKeys(c);
        }

        @Override
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
    
    private static class GeneralNode extends BeanNode {
	public GeneralNode() throws IntrospectionException {
	    super(new OptionsBean.General() {
                @Override
                protected void put(String name, int val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
                @Override
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
    
    private static class SearchNode extends BeanNode {
	public SearchNode() throws IntrospectionException {
	    super(new OptionsBean.Search() {
                @Override
                protected void put(String name, int val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
                @Override
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
    
    private static class ModifyNode extends BeanNode {
	public ModifyNode() throws IntrospectionException {
	    super(new OptionsBean.Modify() {
                @Override
                protected void put(String name, int val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
                @Override
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
                @Override
                protected void put(String name, int val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
                @Override
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

    private static class ExternalProcessNode extends BeanNode {
	public ExternalProcessNode() throws IntrospectionException {
	    super(new OptionsBean.ExternalProcess() {
                @Override
                protected void put(String name, int val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
                @Override
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
                @Override
                protected void put(String name, int val) {
                    try {
                        super.put(name, val);
                    } catch (PropertyVetoException pve) {
                        putEx(pve);
                    }
                }
                @Override
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
    private static final String GETSET_DBG_HL = "DebugHL";
    
    public static class NbDebugOptions extends OptionsBean.Debug {
        @Override
        public PropertyDescriptor[] getPropertyDescriptors() {
            PropertyDescriptor[]  descriptors = super.getPropertyDescriptors();
            PropertyDescriptor d01 = null;
            PropertyDescriptor d02 = null;
            PropertyDescriptor d03 = null;
            try {
                d01 = createPropertyDescriptor(Module.DBG_MODULE,
                                               GETSET_DBG_MODULE,
                                               NbDebugOptions.class);
                d02 = createPropertyDescriptor(Module.DBG_TC,
                                               GETSET_DBG_TC,
                                               NbDebugOptions.class);
                d03 = createPropertyDescriptor(Module.DBG_HL,
                                               GETSET_DBG_HL,
                                               NbDebugOptions.class);
            } catch (IntrospectionException ex) {
                return descriptors;
            }
            PropertyDescriptor[]  d00
                    = new PropertyDescriptor[descriptors.length +3];
            System.arraycopy(descriptors, 0, d00, 0, descriptors.length);
            d00[descriptors.length] = d01;
            d00[descriptors.length +1] = d02;
            d00[descriptors.length +2] = d03;
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
        
        public void setDebugHL(boolean arg) {
            put(Module.DBG_HL, arg);
        }

        public boolean getDebugHL() {
            return getboolean(Module.DBG_HL);
        }
    }
}
