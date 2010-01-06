package org.netbeans.modules.jvi;

import com.raelity.jvi.Msg;
import com.raelity.jvi.ViBuffer;
import com.raelity.jvi.ViFS;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViTextView;
import java.io.IOException;
import java.util.Iterator;
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
    private DataObject getDataObject(ViBuffer buf) {
	Document doc = (Document)buf.getDocument();
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

    public String getDisplayFileName(ViBuffer buf) {
        Document doc = (Document)buf.getDocument();
        if(doc != null) {
            FileObject fo = NbEditorUtilities.getFileObject(doc);
            /*System.err.println("getPath = " + fo.getPath());
            System.err.println("getFileDisplayName = "
                               + FileUtil.getFileDisplayName(fo));
            System.err.println("toFileAbs = " + FileUtil.toFile(fo).getAbsolutePath());
            System.err.println("toFileRel = " + FileUtil.toFile(fo).getPath());
            System.err.println("toFileParent = " + FileUtil.toFile(fo).getParent());*/
            if(fo != null)
                return fo.getNameExt();
        }
        return "UNKNOWN";
    }

    public boolean isModified(ViBuffer buf) {
        DataObject dobj = getDataObject(buf);
        return dobj != null ? dobj.isModified() : true;
    }

    public boolean isReadOnly(ViBuffer buf) {
        //DataObject dobj = getDataObject(buf);
        //return dobj != null ? dobj.isReadOnly() : true;
        return false;
    }

    private boolean write(ViTextView tv, boolean force) {
        boolean ok = true;
        ViBuffer buf = tv.getBuffer();
        DataObject dobj = getDataObject(buf);
        if(dobj != null) {
            //SaveAction s = (SaveAction)SystemAction.get(SaveAction.class);
            // NB6.0 s.createContextAwareInstance(dobj.Lookup());
            //s.performAction((Node[])null);
            SaveCookie sc = dobj.getCookie(SaveCookie.class);
            if(sc != null) {
                try {
                    sc.save();
                    Msg.smsg(buf.getDisplayFileNameAndSize() + " written");
                } catch (IOException ex) {
                    Msg.emsg("error writing " + buf.getDisplayFileName());
                    ok = false;
                }
            } else {
                // Msg.wmsg(fo.getNameExt() + " not dirty");
            }
        }
        return ok;
    }

    private boolean write(ViTextView tv, String fName, boolean force) {
	// Will probably never implement
	Msg.emsg("WRITE new_name NOT IMPLEMENTED, " + force);
        return false;
    }

    public boolean write(
            ViTextView tv, boolean force, Object writeTarget, Integer[] range)
    {
        if(range.length == 0) {
            if(writeTarget == null) {
                return write(tv, force);
            } else if(writeTarget instanceof String) {
                return write(tv, (String) writeTarget, force);
            } else {
                Msg.emsg("WRITE TO " + writeTarget.getClass().getName()
                        + " NOT IMPLEMENTED");
                return false;
            }
        }
	Msg.emsg("WRITE RANGE NOT IMPLEMENTED, ");
        return false;
    }

    public boolean writeAll(boolean force) {
	SaveAllAction sa = SystemAction.get(SaveAllAction.class);
        sa.performAction();
        return true;
    }

    public void edit(ViTextView tv, boolean force, int i) {
        TopComponent tc = null;
        if(i >= 0) {
            Iterator iter = ViManager.getTextBufferIterator();
            while(iter.hasNext()) {
                TopComponent tc01 = (TopComponent)iter.next();
                if(i == ViManager.getViFactory().getWNum(tc01)) {
                    tc = tc01;
                    break;
                }
            }
        } else {
            tc = (TopComponent)ViManager.getMruBuffer(-i);
        }
	if(tc == null) {
	  Msg.emsg("No alternate file name to substitute for '#" + i + "'");
	  return;
	}
	tc.requestActive();
        Msg.smsg(tv.getBuffer().getDisplayFileNameAndSize());
    }

    public void edit(ViTextView tv, boolean force, String fName)
    {
	Msg.emsg("edit fname NOT IMPLEMENTED ");
    }
}
