package org.netbeans.modules.jvi;

import com.raelity.jvi.Filemark;
import com.raelity.jvi.Msg;
import com.raelity.jvi.Util;
import com.raelity.jvi.ViBuffer;
import com.raelity.jvi.ViFS;
import com.raelity.jvi.ViManager;
import com.raelity.jvi.ViTextView;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.swing.text.Document;
import org.netbeans.modules.editor.NbEditorUtilities;
import org.openide.actions.SaveAllAction;
import org.openide.cookies.EditorCookie;
import org.openide.cookies.SaveCookie;
import org.openide.filesystems.FileObject;
import org.openide.filesystems.FileUtil;
import org.openide.loaders.DataObject;
import org.openide.loaders.DataObjectNotFoundException;
import org.openide.text.Line;
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

    /** Edit either a File or Filemark or String */
    public void edit(ViTextView tv, boolean force, Object fileThing)
    {
        String msg = null;
        try {
            File f = null;
            Filemark fm = null;

            // get a java File object for the thing
            if(fileThing instanceof Filemark) {
                fm = (Filemark)fileThing;
                f = fm.getFile();
            } else if(fileThing instanceof File) {
                f = (File)fileThing;
            } else if(fileThing instanceof String) {
                f = new File((String)fileThing);
            } else {
                ViManager.dumpStack("unknown fileThing type");
                return;
            }

            // get a netbens FileObject
            if(!f.isAbsolute()) {
                f = f.getAbsoluteFile();
            }
            FileObject fo;
            if(f.exists()) {
                fo = FileUtil.toFileObject(f);
            } else if(force) {
                fo = FileUtil.createData(f);
            } else {
                msg = "'!' required when file does not exist";
                return;
            }
            DataObject dobj = DataObject.find(fo);
            EditorCookie ec = dobj.getCookie(EditorCookie.class);

            // Start bringing it up in the editor
            ec.open();
            // Wait for the document to be available
            Document doc = ec.openDocument();
            //System.err.println("Document Ready");
            if(fm != null) {
                // finishTagPush ??
                int wnum = 0; // window of file mark

                // currently active or not
                // if active use offset

                // Q up goto line for next switch, Q: tc,run

                Line l = NbEditorUtilities.getLine(doc, fm.getOffset(), false);
                l.show(Line.ShowOpenType.OPEN, Line.ShowVisibilityType.FOCUS);
            }
        } catch (DataObjectNotFoundException ex) {
            msg = ex.getLocalizedMessage();
        } catch (IOException ex) {
            msg = ex.getLocalizedMessage();
            //Logger.getLogger(NbFS.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            if(msg != null) {
                Msg.emsg("edit failed: " + msg);
                Util.vim_beep();
            }
        }
    }
}
