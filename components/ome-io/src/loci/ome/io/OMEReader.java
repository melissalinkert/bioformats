/*
 * #%L
 * OME database I/O package for communicating with OME and OMERO servers.
 * %%
 * Copyright (C) 2005 - 2013 Open Microscopy Environment:
 *   - Board of Regents of the University of Wisconsin-Madison
 *   - Glencoe Software, Inc.
 *   - University of Dundee
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package loci.ome.io;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import loci.common.RandomAccessInputStream;
import loci.common.services.DependencyException;
import loci.common.services.ServiceFactory;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.meta.MetadataStore;

import loci.ome.io.services.OMEReaderWriterService;

/**
 * OMEReader retrieves images on demand from an OME database.
 * Authentication with the OME server is handled, provided the 'id' parameter
 * is properly formed.
 * The 'id' parameter should be:
 *
 * [server]?user=[username]&password=[password]&id=[image id]
 *
 * where [server] is the URL of the OME data server (not the image server).
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/ome-io/src/loci/ome/io/OMEReader.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/ome-io/src/loci/ome/io/OMEReader.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public class OMEReader extends FormatReader {

  // -- Fields --

  /** Authentication credentials. */
  private OMECredentials credentials;

  private OMEReaderWriterService service;

  // -- Constructor --

  /** Constructs a new OME reader. */
  public OMEReader() { super("Open Microscopy Environment (OME)", "*"); }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    if (id.equals(currentId)) return;

    // TODO: construct service

    try {
      ServiceFactory factory = new ServiceFactory();
      service = factory.getInstance(OMEReaderWriterService.class);
    }
    catch (DependencyException e) { }

    if (service == null) {
      throw new FormatException(OMEUtils.NO_OME_MSG);
    }

    credentials = new OMECredentials(id);
    id = String.valueOf(credentials.imageID);

    super.initFile(id);

    // do sanity check on server name
    if (credentials.server.startsWith("http:")) {
      credentials.server = credentials.server.substring(5);
    }
    while (credentials.server.startsWith("/")) {
      credentials.server = credentials.server.substring(1);
    }
    int slash = credentials.server.indexOf("/");
    if (slash >= 0) credentials.server = credentials.server.substring(0, slash);
    int colon = credentials.server.indexOf(":");
    if (colon >= 0) credentials.server = credentials.server.substring(0, colon);

    currentId = credentials.server + ":" + credentials.imageID;

    credentials.omeis = "http://" + credentials.server + "/cgi-bin/omeis";
    credentials.server = "http://" + credentials.server + "/shoola/";
    credentials.isOMERO = false;

    String user = credentials.username;
    String pass = credentials.password;

    CoreMetadata m = service.populateCoreMetadata(credentials);

    HashMap<String, String> originalMetadata =
      service.getOriginalMetadata(credentials);
    for (String key : originalMetadata.keySet()) {
      addGlobalMeta(key, originalMetadata.get(key));
    }

    m.littleEndian = true;
    m.interleaved = false;
    core.set(0, m);

    MetadataStore store = getMetadataStore();
    MetadataTools.populatePixels(store, this);
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessInputStream) */
  public boolean isThisType(RandomAccessInputStream stream) throws IOException {
    return true;
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.assertId(currentId, true, 1);
    FormatTools.checkPlaneNumber(this, no);
    FormatTools.checkBufferSize(this, buf.length);
    int[] indices = getZCTCoords(no);

    return service.getPlane(indices[0], indices[1], indices[2], credentials);
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    service.close();
    if (!fileOnly) currentId = null;
  }

  /* @see loci.formats.IFormatReader#close() */
  public void close() throws IOException {
    close(false);
  }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#isThisType(String) */
  public boolean isThisType(String id) {
    return id.indexOf("id") != -1 && (id.indexOf("password") != -1 ||
      id.indexOf("sessionKey") != -1);
  }

}
