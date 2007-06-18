package org.netbeans.modules.jvi;

import com.raelity.jvi.Msg;
import com.raelity.jvi.ViFS;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViTextView;
import java.io.File;
import java.io.IOException;
import javax.swing.text.Document;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.actions.SaveAllAction;
import org.openide.cookies.SaveCookie;
import org.openide.filesystems.FileObject;
import org.openide.loaders.DataObject;
import org.openide.util.actions.SystemAction;
import org.openide.windows.TopComponent;

public class NbFS implements ViFS
{
    private DataObject getDataObject(ViTextView tv) {
	Document doc = tv.getEditorComponent().getDocument();
        DataObject dobj = null;
	if(doc != null) {
	    FileObject fo = NbEditorUtilities.getFileObject(doc);
	    if(fo == null) {
		Msg.emsg("Internal Error: null FileObject??");
                return null;
	    }
	    dobj = NbEditorUtilities.getDataObject(doc);
        }
        return dobj;
    }

    public boolean isModified(ViTextView tv) {
        DataObject dobj = getDataObject(tv);
        return dobj != null ? dobj.isModified() : true;
    }

    public void write(ViTextView tv, boolean force) {
        DataObject dobj = getDataObject(tv);
        if(dobj != null) {
            //SaveAction s = (SaveAction)SystemAction.get(SaveAction.class);
            // NB6.0 s.createContextAwareInstance(dobj.Lookup());
            //s.performAction((Node[])null);
            SaveCookie sc = (SaveCookie)dobj.getCookie(SaveCookie.class);
            if(sc != null) {
                try {
                    sc.save();
                    Msg.smsg(tv.getDisplayFileNameAndSize() + " written");
                } catch (IOException ex) {
                    Msg.emsg("error writing " + tv.getDisplayFileName());
                }
            } else {
                // Msg.wmsg(fo.getNameExt() + " not dirty");
            }
        }
    }

    public void writeAll(boolean force) {
	SaveAllAction sa = (SaveAllAction)SystemAction.get(SaveAllAction.class);
        sa.performAction();
    }

    public void write(ViTextView tv, File file, boolean force) {
	// Will probably never implement
	Msg.emsg("WRITE new_name NOT IMPLEMENTED, " + force);
    }

    public void edit(ViTextView viTextView, int i, boolean force) {
	TopComponent tc = (TopComponent)ViManager.getTextBuffer(i);
	if(tc == null) {
	  Msg.emsg("No alternate file name to substitute for '#" + i + "'");
	  return;
	}
	tc.requestActive();
        Msg.smsg(viTextView.getDisplayFileNameAndSize());
    }    
}
