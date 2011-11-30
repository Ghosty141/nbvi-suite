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
                    // See: http://netbeans.org/bugzilla/show_bug.cgi?id=205336
                    //      ClassCastException: org.netbeans.modules.xml
                    //      .XMLDataObject cannot be cast to org.openide
                    //      .loaders.InstanceDataObject
                    // And: http://sourceforge.net/tracker/?func=detail&aid=3440698&group_id=3653&atid=103653
                    // InstanceDataObject.create(df, name + ".instance",
                    //                           action, null);

                    FileObject fo = dir.createData(name + ".instance");
                    fo.setAttribute("instanceCreate", action);
                    fo.setAttribute("instanceClass", action.getClass().getName());
                    // That last one: "instanceClass", action.getClass()
                    // is to avoid the following warning
                    // WARNING [org.openide.loaders.InstanceDataObject]:
                    //Instance file MultiFileObject@1de993a[Editors/Actions
                    ///ViCtrl-TKey.instance] uses instanceCreate attribute,
                    //but doesn't define instanceOf attribute. Please add
                    // instanceOf attr to avoid multiple instances creation,
                    //see details at
                    //http://www.netbeans.org/issues/show_bug.cgi?id=131951
                    //fo.setAttribute("instanceOf", "javax.swing.Action");
                    //NOTE: the WARNING is flawed, using instanceClass works.
                    //fo.setAttribute("instanceClass", Action.class);
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
