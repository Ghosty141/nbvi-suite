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

import com.raelity.jvi.swing.KeyBinding;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Action;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileSystem;
import org.openide.filesystems.FileUtil;
import org.openide.filesystems.MultiFileSystem;
import org.openide.loaders.DataFolder;
import org.openide.loaders.InstanceDataObject;
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

    private static KeyActionsFS INSTANCE;

    @SuppressWarnings("LeakingThisInConstructor")
    public KeyActionsFS() {
        //will be created on startup, exactly once
        assert INSTANCE == null;
        INSTANCE = this;
        // setPropagateMasks(true); // in case you want to use *_hidden masks
    }

    public static void injectKeyActionsLayer() {
        if (INSTANCE.getDelegates().length == 0) {
            try {
                FileSystem fs = FileUtil.createMemoryFileSystem();
                FileObject dir = FileUtil.createFolder(fs.getRoot(), MY_ROOT);
                DataFolder df = DataFolder.findFolder(dir);

                for(Action action : KeyBinding.getActionsList()) {
                    String name = (String)action.getValue(Action.NAME);
                    InstanceDataObject.create(df, name + ".instance",
                                              action, null);
                }

                INSTANCE.setDelegates(fs);
            } catch(IOException ex) {
                Logger.getLogger(KeyActionsFS.class.getName()).
                        log(Level.SEVERE, null, ex);
            }
        }

        //Collection<? extends Action> lookupAll =
        //        Lookups.forPath(MY_ROOT).lookupAll(Action.class);
        //System.err.format("Lookups.forPath(%s)\n", MY_ROOT);
        //for(Action act : lookupAll) {
        //    System.err.println("\t" + act.getValue(Action.NAME));
        //}
        //System.err.format("Lookups.forPath(%s) %s\n", MY_ROOT, lookupAll);
    }
}
