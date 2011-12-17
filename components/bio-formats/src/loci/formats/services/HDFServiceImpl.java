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

import java.io.File;
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
import ncsa.hdf.object.Attribute;
import ncsa.hdf.object.Datatype;
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

  public static final String NO_HDF_MSG =
    "HDF-Java is required to read HDF file. " +
    "Please obtain the necessary JAR files from " +
    "http://loci.wisc.edu/bio-formats/bio-formats-java-library.\n";

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
    attributeList = new Vector<String>();
    variableList = new Vector<String>();
    buildPathLists();
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
    try {
      int lastSeparator = path.lastIndexOf(File.separator);
      String realPath = path.substring(0, lastSeparator);
      String attribute = path.substring(lastSeparator + 1);
      HObject object = FileFormat.findObject(hdfFile, realPath);

      List attributes = object.getMetadata();
      Attribute attr = null;
      for (Object o : attributes) {
        attr = (Attribute) o;
        if (attr.getName().equals(attribute)) {
          Datatype type = attr.getType();
          Object value = attr.getValue();

          int typeClass = type.getDatatypeClass();

          if (value instanceof String[]) {
            StringBuffer v = new StringBuffer();
            String[] s = (String[]) value;
            for (int i=0; i<s.length; i++) {
              v.append(s[i]);
              if (i < s.length - 1) {
                v.append(", ");
              }
            }
            return v.toString();
          }
          else if (value instanceof double[]) {
            StringBuffer v = new StringBuffer();
            double[] s = (double[]) value;
            for (int i=0; i<s.length; i++) {
              v.append(s[i]);
              if (i < s.length - 1) {
                v.append(", ");
              }
            }
            return v.toString();
          }
          else {
            return value.toString();
          }
        }
      }
    }
    catch (Exception e) {
    }
    return null;
  }

  /* (non-Javadoc)
   * @see loci.formats.NetCDFService#getVariableValue(java.lang.String)
   */
  public Object getVariableValue(String name) throws ServiceException {
    try {
      HObject object = hdfFile.get(name);
    }
    catch (Exception e) {

    }
    return null;
  }

  /* (non-Javadoc)
   * @see loci.formats.NetCDFService#getArray(java.lang.String, int[], int[])
   */
  public Object getArray(String path, int[] origin, int[] shape)
    throws ServiceException
  {
    try {
      HObject object = hdfFile.get(path);
    }
    catch (Exception e) {

    }
    return null;
  }

  /* (non-Javadoc)
   * @see loci.formats.NetCDFService#getVariableAttributes(java.lang.String)
   */
  public Hashtable<String, Object> getVariableAttributes(String name) {
    try {
      HObject object = hdfFile.get(name);
    }
    catch (Exception e) {

    }
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

  private void buildPathLists() {
    TreeNode root = hdfFile.getRootNode();
    buildPathLists(root);
  }

  private void buildPathLists(TreeNode root) {
    if (root == null) {
      return;
    }
    DefaultMutableTreeNode node = null;
    Enumeration children = root.children();
    HObject object = null;
    while (children.hasMoreElements()) {
      node = (DefaultMutableTreeNode) children.nextElement();
      if (node.isLeaf()) {
        object = (HObject) node.getUserObject();
        String path = object.getFullName();

        if (object.hasAttribute()) {
          // add things to attributes list
          try {
            List metadata = object.getMetadata();
            for (Object o : metadata) {
              if (o instanceof Attribute) {
                attributeList.add(
                  path + File.separator + ((Attribute) o).getName());
              }
            }
          }
          catch (Exception e) {

          }
        }
        /*
        else if (object instanceof ) {
          variableList.add(path);
        }
        */
      }
      else {
        buildPathLists(node);
      }
    }
  }

}
