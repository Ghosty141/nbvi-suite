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

package org.netbeans.modules.jvi;

import com.raelity.jvi.manager.ViManager;
import com.raelity.jvi.swing.KeyBinding;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.MultiFileSystem;
import org.openide.util.lookup.ServiceProvider;

/**
 * All the keystrokes that jVi might receive
 * must be registered in Editors/Actions.
 * Do it here programmatically.
 *
 * @author Ernie Rael <err at raelity.com>
 */
@ServiceProvider (service=FileSystem.class)
public class KeyActionsFS extends MultiFileSystem
{
    private static final String MY_ROOT = "Editors/Actions";
    //private static final String MY_ROOT = "Editors/Actions/test";

    private static KeyActionsFS INSTANCE;

    @SuppressWarnings("LeakingThisInConstructor")
    public KeyActionsFS() {
        //will be created on startup, exactly once
        assert INSTANCE == null;
        INSTANCE = this;
        // setPropagateMasks(true); // in case you want to use *_hidden masks
    }

    private static void
    createActionFileObject(FileObject dir, String name, Method creator)
    {
        try {
            FileObject fo = dir.createData(name, "instance");
            fo.setAttribute("instanceOf", "javax.swing.Action");
            fo.setAttribute("action-name", name);
            //fo.setAttribute("methodvalue:instanceCreate", creator);
            fo.setAttribute("instanceCreate", KeyBinding.getAction(name));
        } catch(IOException ex) {
            Logger.getLogger(KeyActionsFS.class.getName()).
                    log(Level.SEVERE, null, ex);
        }
    }

    public static void injectKeyActionsLayer() {
        if (INSTANCE.getDelegates().length == 0) {
            try {
                FileSystem fs = FileUtil.createMemoryFileSystem();
                FileObject dir = FileUtil.createFolder(fs.getRoot(), MY_ROOT);
                Class clazz = ViManager.getFactory().loadClass(
                        "org.netbeans.modules.jvi.KeyBindings");
                @SuppressWarnings("unchecked")
                Method creator = clazz.getDeclaredMethod(
                        "createKBA", Map.class);
                for(Action a : KeyBinding.getActionsList()) {
                    String name = (String)a.getValue(Action.NAME);
                    createActionFileObject(dir, name, creator);
                }
                INSTANCE.setDelegates(fs);
            } catch(ClassNotFoundException ex) {
                Logger.getLogger(KeyActionsFS.class.getName()).
                        log(Level.SEVERE, null, ex);
            } catch(NoSuchMethodException ex) {
                Logger.getLogger(KeyActionsFS.class.getName()).
                        log(Level.SEVERE, null, ex);
            } catch(SecurityException ex) {
                Logger.getLogger(KeyActionsFS.class.getName()).
                        log(Level.SEVERE, null, ex);
            } catch(IOException ex) {
                Logger.getLogger(KeyActionsFS.class.getName()).
                        log(Level.SEVERE, null, ex);
            }
            // dump(MY_ROOT, "ViUpKey.instance");
            // dump("Editors/Actions", "ViUpKey.instance");
        }
    }

    private static void dump(String dirname, String fname)
    {
        System.err.format("DUMP: %s/%s\n", dirname, fname);
        //FileObject fo = FileUtil.getConfigFile(dirname + "/" + fname);
        FileObject dir = FileUtil.getConfigFile(dirname);
        FileObject fo = dir.getFileObject(fname);
        if(fo == null)
            System.err.println("\tNOT FOUND");
        else {
            Object iOf = fo.getAttribute("instanceOf");
            Object an = fo.getAttribute("action-name");
            Object ic = fo.getAttribute("instanceCreate");
            System.err.format("\tiOf: %s\n\tan: %s\n\tval: %s\n",
                              iOf, an, ic);
        }
    }
}
