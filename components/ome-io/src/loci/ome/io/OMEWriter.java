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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import loci.common.Constants;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.common.services.ServiceFactory;
import loci.formats.FileStitcher;
import loci.formats.FormatException;
import loci.formats.FormatWriter;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.services.OMEXMLService;

import loci.ome.io.services.OMEReaderWriterService;

/**
 * Uploads images to an OME server.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/ome-io/src/loci/ome/io/OMEWriter.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/ome-io/src/loci/ome/io/OMEWriter.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public class OMEWriter extends FormatWriter {

  // -- Static fields --

  private static boolean hasOMEJava = true;

  // -- Fields --

  private OMEReaderWriterService service;

  /** Authentication credentials. */
  private OMECredentials credentials;

  /** Number of planes written. */
  private int planesWritten = 0;

  private String[] originalFiles;

  // -- Constructor --

  public OMEWriter() {
    super("Open Microscopy Environment", "");
  }

  // -- IFormatWriter API methods --

  /**
   * @see loci.formats.IFormatWriter#saveBytes(int, byte[], int, int, int, int)
   */
  public void saveBytes(int no, byte[] bytes, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    if (service == null) {
      try {
        ServiceFactory factory = new ServiceFactory();
        service = factory.getInstance(OMEReaderWriterService.class);
      }
      catch (DependencyException e) {
        throw new FormatException(e);
      }
    }

    if (!hasOMEJava) throw new FormatException(OMEUtils.NO_OME_MSG);
    if (currentId != null && credentials == null) {
      // parse the ID string to get the server, user name and password

      credentials = new OMECredentials(currentId);
      login();
      credentials.imageID = -1;

      // initialize necessary services

      service.initializeImportRepository(credentials);

      if (metadataRetrieve == null) {
        throw new FormatException("Metadata store not specified.");
      }
    }

    service.uploadOriginalFiles(originalFiles);
    planesWritten = service.uploadPlane(
      credentials, metadataRetrieve, series, bytes, planesWritten);

    if (series == metadataRetrieve.getImageCount() - 1) {
      service.finalizeImport(credentials, metadataRetrieve, series);

      planesWritten = 0;
      credentials.imageID = -1;
    }
  }

  /* @see loci.formats.IFormatWriter#canDoStacks(String) */
  @Override
  public boolean canDoStacks() { return true; }

  // -- IFormatHandler API methods --

  /* @see loci.formats.IFormatHandler#close() */
  @Override
  public void close() throws IOException {
    if (service != null) {
      service.close();
    }
    service = null;
    credentials = null;
    planesWritten = 0;
    if (currentId != null) metadataRetrieve = null;
    currentId = null;
  }

  // -- Helper methods --

  private void login() throws FormatException, IOException {
    while (credentials.server.lastIndexOf("/") > 7) {
      int slash = credentials.server.lastIndexOf("/");
      credentials.server = credentials.server.substring(0, slash);
    }
    credentials.omeis = credentials.server + "/cgi-bin/omeis";
    credentials.server += "/shoola";
    if (!credentials.server.startsWith("http://")) {
      credentials.server = "http://" + credentials.server;
      credentials.omeis = "http://" + credentials.omeis;
    }

    LOGGER.info("Logging in to {}", credentials.server);

    service.doLogin(credentials);
  }

  // -- OMEWriter API methods --

  public void setOriginalFiles(String[] filenames) {
    originalFiles = filenames;
  }

  // -- Main method --

  public static void main(String[] args) throws Exception {
    String server = null, user = null, pass = null;
    String id = null;
    int series = -1;

    // parse command-line arguments
    boolean doUsage = false;
    if (args.length == 0) doUsage = true;
    for (int i=0; i<args.length; i++) {
      if (args[i].startsWith("-")) {
        // argument is a command line flag
        String param = args[i];
        try {
          if (param.equalsIgnoreCase("-s")) server = args[++i];
          else if (param.equalsIgnoreCase("-u")) user = args[++i];
          else if (param.equalsIgnoreCase("-p")) pass = args[++i];
          else if (param.equalsIgnoreCase("-series")) {
            series = Integer.parseInt(args[++i]);
          }
          else if (param.equalsIgnoreCase("-h") || param.equalsIgnoreCase("-?"))
          {
            doUsage = true;
          }
          else {
            LOGGER.warn("Unknown flag: {}", param);
            doUsage = true;
            break;
          }
        }
        catch (ArrayIndexOutOfBoundsException exc) {
          if (i == args.length - 1) {
            LOGGER.warn(
              "Flag {} must be followed by a parameter value.", param);
            doUsage = true;
            break;
          }
          else throw exc;
        }
      }
      else {
        if (id == null) id = args[i];
        else {
          LOGGER.warn("Unknown argument: {}", args[i]);
        }
      }
    }

    if (id == null) doUsage = true;
    if (doUsage) {
      LOGGER.info("Usage: omeul [-s server.address] " +
        "[-u username] [-p password] [-series series.number] filename");
      System.exit(1);
    }

    // ask for information if necessary
    BufferedReader cin = new BufferedReader(
      new InputStreamReader(System.in, Constants.ENCODING));
    if (server == null) {
      LOGGER.info("Server address? ");
      try { server = cin.readLine(); }
      catch (IOException exc) { }
    }
    if (user == null) {
      LOGGER.info("Username? ");
      try { user = cin.readLine(); }
      catch (IOException exc) { }
    }
    if (pass == null) {
      LOGGER.info("Password? ");
      try { pass = cin.readLine(); }
      catch (IOException exc) { }
    }

    if (server == null || user == null || pass == null) {
      LOGGER.error("Could not obtain server login information");
      System.exit(2);
    }
    LOGGER.info("Using server {} as user {}", server, user);

    // create image uploader
    OMEWriter uploader = new OMEWriter();

    FileStitcher reader = new FileStitcher();
    reader.setOriginalMetadataPopulated(true);

    try {
      ServiceFactory factory = new ServiceFactory();
      OMEXMLService service = factory.getInstance(OMEXMLService.class);
      reader.setMetadataStore(service.createOMEXMLMetadata());
    }
    catch (DependencyException e) {
      LOGGER.warn("OMEXMLService not available", e);
    }
    catch (ServiceException e) {
      LOGGER.warn("Could not parse OME-XML", e);
    }

    reader.setId(id);

    uploader.setMetadataRetrieve((MetadataRetrieve) reader.getMetadataStore());
    uploader.setOriginalFiles(reader.getUsedFiles());
    uploader.setId(server + "?user=" + user + "&password=" + pass);

    int start = series == -1 ? 0 : series;
    int end = series == -1 ? reader.getSeriesCount() : series + 1;

    for (int s=start; s<end; s++) {
      reader.setSeries(s);
      uploader.setSeries(s);
      for (int i=0; i<reader.getImageCount(); i++) {
        uploader.saveBytes(i, reader.openBytes(i));
      }
    }
    reader.close();
    uploader.close();
  }

}
