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
 * Copyright (C) 2000-2010 Ernie Rael.  All Rights Reserved.
 *
 * Contributor(s): Ernie Rael <err@raelity.com>
 */

package org.netbeans.modules.jvi.reflect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.text.JTextComponent;
import org.openide.util.Lookup;

/**
 *
 * @author Ernie Rael <err at raelity.com>
 */
public class NbUtil
{
    private NbUtil() { }

    private static final Logger LOG = Logger.getLogger(NbUtil.class.getName());

    /**
     *
     * :e# file name completion based on NB code completion
     */
    public static boolean EditorRegistryRegister(JTextComponent jtc) {
        boolean done = false;
        Exception ex1 = null;

        try {
            Class<?> c = Lookup.getDefault().lookup(ClassLoader.class).loadClass(
                            "org.netbeans.modules.editor.lib2"
                            + ".EditorApiPackageAccessor");
            Method get = c.getMethod("get");
            Object o = get.invoke(null);
            Method register = c.getMethod("register", JTextComponent.class);
            register.invoke(o, jtc);

/*
            Boolean b = (Boolean) ViManager.HackMap.get(HACK_CC);
            if(b != null && b) {
                c = ((ClassLoader)(Lookup.getDefault()
                            .lookup(ClassLoader.class))).loadClass(
                                "org.netbeans.api.editor"
                                + ".EditorRegistry$Item");
                o = jtc.getClientProperty(c);
                if(o != null) {
                    Field field = c.getDeclaredField("ignoreAncestorChange");
                    field.setAccessible(true);
                    field.setBoolean(o, true);
                }
            }
*/
            done = true;
        } catch(ClassNotFoundException ex) {
            ex1 = ex;
        } catch(InvocationTargetException ex) {
            ex1 = ex;
        } catch(NoSuchMethodException ex) {
            ex1 = ex;
        } catch(IllegalAccessException ex) {
            ex1 = ex;
//      } catch (NoSuchFieldException ex) {
//          ex1 = ex;
        }
        if(ex1 != null)
            LOG.log(Level.SEVERE, null, ex1);

        return done;
    }

}
