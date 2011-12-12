//
// HDFServiceImpl.java
//

/*
OME Bio-Formats package for reading and converting biological file formats.
Copyright (C) 2005-@year@ UW-Madison LOCI and Glencoe Software, Inc.

This program is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package loci.formats.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;

import loci.common.Location;
import loci.common.services.AbstractService;
import loci.common.services.ServiceException;

import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;
import ncsa.hdf.object.FileFormat;
import ncsa.hdf.object.HObject;
import ncsa.hdf.object.h4.H4File;
import ncsa.hdf.object.h5.H5File;

/**
 * Utility class for working with HDF files.  Uses reflection to
 * call the HDF-Java library.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/services/HDFServiceImpl.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/services/HDFServiceImpl.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public class HDFServiceImpl extends AbstractService implements NetCDFService {

  // -- Constants --

  public static final String NO_HDF_MSG = "";

  // -- Fields --

  private String currentFile;

  private Vector<String> attributeList;

  private Vector<String> variableList;

  /** HDF file instance. */
  private FileFormat hdfFile;

  // -- NetCDFService API methods ---

  /**
   * Default constructor.
   */
  public HDFServiceImpl() {
    // One check from each package
    checkClassDependency(ncsa.hdf.object.FileFormat.class);
  }

  /* (non-Javadoc)
   * @see loci.formats.NetCDFService#setFile(java.lang.String)
   */
  public void setFile(String file) throws IOException {
    this.currentFile = file;

    try {
      hdfFile = new H5File(file);
      hdfFile.open();
    }
    catch (Exception e) {
      hdfFile = new H4File(file);
      try {
        hdfFile.open();
      }
      catch (Exception exc) {
        throw new IOException(exc);
      }
    }
  }

  /* (non-Javadoc)
   * @see loci.formats.NetCDFService#getFile()
   */
  public String getFile() {
    return currentFile;
  }

  /* (non-Javadoc)
   * @see loci.formats.NetCDFService#getAttributeList()
   */
  public Vector<String> getAttributeList() {
    return attributeList;
  }

  /* (non-Javadoc)
   * @see loci.formats.NetCDFService#getVariableList()
   */
  public Vector<String> getVariableList() {
    return variableList;
  }

  /* (non-Javadoc)
   * @see loci.formats.NetCDFService#getAttributeValue(java.lang.String)
   */
  public String getAttributeValue(String path) {
    HObject object = findObject(path);
    return null;
  }

  /* (non-Javadoc)
   * @see loci.formats.NetCDFService#getVariableValue(java.lang.String)
   */
  public Object getVariableValue(String name) throws ServiceException {
    HObject object = findObject(name);
    return null;
  }

  /* (non-Javadoc)
   * @see loci.formats.NetCDFService#getArray(java.lang.String, int[], int[])
   */
  public Object getArray(String path, int[] origin, int[] shape)
    throws ServiceException
  {
    HObject object = findObject(path);
    return null;
  }

  /* (non-Javadoc)
   * @see loci.formats.NetCDFService#getVariableAttributes(java.lang.String)
   */
  public Hashtable<String, Object> getVariableAttributes(String name) {
    HObject object = findObject(name);
    return null;
  }

  public int getDimension(String name) {
    return 0;
  }

  /* (non-Javadoc)
   * @see loci.formats.NetCDFService#close()
   */
  public void close() throws IOException {
    currentFile = null;
    attributeList = null;
    variableList = null;
    hdfFile = null;
  }

  // -- Helper methods --

  private HObject findObject(String name) {
    return findObject(name, hdfFile.getRootNode());
  }

  private HObject findObject(String name, TreeNode root) {
    if (root == null) {
      return null;
    }
    DefaultMutableTreeNode node = null;
    HObject object = null;
    Enumeration children = root.children();
    while (children.hasMoreElements()) {
      node = (DefaultMutableTreeNode) children.nextElement();
      object = (HObject) node.getUserObject();
      String path = object.getFullName();
      if (path.equals(name)) {
        return object;
      }
      else if (name.startsWith(path)) {
        return findObject(name, node);
      }
    }
    return null;
  }

}
