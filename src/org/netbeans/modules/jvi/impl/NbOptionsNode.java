/*
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 *
 * The Original Code is jvi - vi editor clone.
 *
 * The Initial Developer of the Original Code is Ernie Rael.
 * Portions created by Ernie Rael are
 * Copyright (C) 2000 Ernie Rael.  All Rights Reserved.
 *
 * Contributor(s): Ernie Rael <err@raelity.com>
 */
package org.netbeans.modules.jvi.impl;

import com.raelity.jvi.options.OptionsBean;
import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.swing.ui.options.OptionsPanel;
import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.beans.PropertyVetoException;
import org.netbeans.modules.jvi.Module;

public class NbOptionsNode extends OptionsPanel {

    public NbOptionsNode(ChangeNotify changeNotify)
    {
        super(changeNotify);
    }

    @Override
    public BeanInfo createDebugBean() {
        return new NbDebugOptions();
    }

    @Override
    public void addNotify() {
        super.addNotify();
        setMaximumSize(getPreferredSize());
    }

    
    public static class NbDebugOptions extends OptionsBean.Debug {
        @Override
        public PropertyDescriptor[] getPropertyDescriptors() {
            PropertyDescriptor[]  descriptors = super.getPropertyDescriptors();
            PropertyDescriptor d01 = null;
            PropertyDescriptor d02 = null;
            PropertyDescriptor d03 = null;
            try {
                d01 = ViManager.getFactory()
                     .createPropertyDescriptor(Module.DBG_MODULE,
                                               Module.DBG_MODULE,
                                               NbDebugOptions.class);
                d02 = ViManager.getFactory()
                     .createPropertyDescriptor(Module.DBG_TC,
                                               Module.DBG_TC,
                                               NbDebugOptions.class);
                d03 = ViManager.getFactory()
                     .createPropertyDescriptor(Module.DBG_HL,
                                               Module.DBG_HL,
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
        
        public void setDebugNbModule(String arg) throws PropertyVetoException {
            put(Module.DBG_MODULE, arg);
        }

        public String getDebugNbModule() {
            return getString(Module.DBG_MODULE);
        }
        
        public void setDebugNbTopComponent(String arg) throws PropertyVetoException {
            put(Module.DBG_TC, arg);
        }

        public String getDebugNbTopComponent() {
            return getString(Module.DBG_TC);
        }
        
        public void setDebugNbHilight(String arg) throws PropertyVetoException {
            put(Module.DBG_HL, arg);
        }

        public String getDebugNbHilight() {
            return getString(Module.DBG_HL);
        }
    }
}
