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

package loci.ome.io.services;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import loci.common.DateTools;
import loci.common.services.AbstractService;
import loci.common.services.DependencyException;
import loci.common.services.ServiceFactory;
import loci.common.xml.XMLTools;
import loci.formats.CoreMetadata;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.IFormatWriter;
import loci.formats.ImageTools;
import loci.formats.meta.MetadataRetrieve;
import loci.formats.ome.OMEXMLMetadata;
import loci.formats.services.OMEXMLService;
import loci.ome.io.OMECredentials;
import loci.ome.io.OMEReader;
import loci.ome.io.OMEWriter;
import loci.ome.io.OmeroReader;

import org.openmicroscopy.ds.Criteria;
import org.openmicroscopy.ds.DataFactory;
import org.openmicroscopy.ds.DataServer;
import org.openmicroscopy.ds.DataServices;
import org.openmicroscopy.ds.FieldsSpecification;
import org.openmicroscopy.ds.RemoteCaller;
import org.openmicroscopy.ds.dto.Attribute;
import org.openmicroscopy.ds.dto.Image;
import org.openmicroscopy.ds.dto.ModuleExecution;
import org.openmicroscopy.ds.dto.UserState;
import org.openmicroscopy.ds.managers.ImportManager;
import org.openmicroscopy.ds.st.Experimenter;
import org.openmicroscopy.ds.st.LogicalChannel;
import org.openmicroscopy.ds.st.OriginalFile;
import org.openmicroscopy.ds.st.PixelChannelComponent;
import org.openmicroscopy.ds.st.Pixels;
import org.openmicroscopy.ds.st.Repository;
import org.openmicroscopy.is.CompositingSettings;
import org.openmicroscopy.is.ImageServer;
import org.openmicroscopy.is.ImageServerException;
import org.openmicroscopy.is.PixelsFactory;

/**
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/ome-io/src/loci/ome/io/services/OMEReaderWriterServiceImpl.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/ome-io/src/loci/ome/io/services/OMEReaderWriterServiceImpl.java;hb=HEAD">Gitweb</a></dd></dl>
 *
 * @author callan
 */
public class OMEReaderWriterServiceImpl extends AbstractService
  implements OMEReaderWriterService
{

  // -- Fields --

  private RemoteCaller rc;
  private DataFactory df;
  private PixelsFactory pf;
  private ImportManager im;
  private Pixels pixels;
  private Experimenter experimenter;
  private Repository repository;
  private Image img;
  private ModuleExecution of;
  private ImageServer is;
  private ModuleExecution ii;

  public OMEReaderWriterServiceImpl() {
    // Just being thorough with these dependencies.
    checkClassDependency(OMEReader.class);
    checkClassDependency(OMEWriter.class);
    checkClassDependency(OmeroReader.class);
    checkClassDependency(omero.model.Image.class);
    checkClassDependency(Image.class);
  }

  /* (non-Javadoc)
   * @see loci.formats.OMEReaderWriterService#newOMEROReader()
   */
  public IFormatReader newOMEROReader() {
    return new OmeroReader();
  }

  /* (non-Javadoc)
   * @see loci.formats.OMEReaderWriterService#newOMEROWriter()
   */
  public IFormatWriter newOMEROWriter() {
    throw new IllegalArgumentException("Unavailable OMERO writer.");
  }

  /* (non-Javadoc)
   * @see loci.formats.OMEReaderWriterService#newOMEReader()
   */
  public IFormatReader newOMEReader() {
    return new OMEReader();
  }

  /* (non-Javadoc)
   * @see loci.formats.OMEReaderWriterService#newOMEWriter()
   */
  public IFormatWriter newOMEWriter() {
    return new OMEWriter();
  }

  public void doLogin(OMECredentials credentials) throws IOException {
    try {
      DataServices rs = DataServer.getDefaultServices(credentials.server);
      rc = rs.getRemoteCaller();
      rc.login(credentials.username, credentials.password);

      df = (DataFactory) rs.getService(DataFactory.class);
      pf = (PixelsFactory) rs.getService(PixelsFactory.class);
      im = (ImportManager) rs.getService(ImportManager.class);
    }
    catch (Exception e) {
      throw new IOException(e);
    }
  }

  public void initializeImportRepository(OMECredentials credentials)
    throws IOException
  {
    FieldsSpecification fields = new FieldsSpecification();
    fields.addWantedField("id");
    fields.addWantedField("experimenter");
    fields.addWantedField("experimenter", "id");
    UserState state = df.getUserState(fields);
    experimenter = state.getExperimenter();
    try {
      repository = pf.findRepository(0);
      repository.setImageServerURL(credentials.omeis);
    }
    catch (ImageServerException e) {
      throw new IOException(e);
    }
  }

  public void uploadOriginalFiles(String[] files) throws IOException {
    im.startImport(experimenter);
    img = (Image) df.createNew(Image.class);
    of = (ModuleExecution) im.getOriginalFilesMEX();
    for (String file : files) {
      try {
        pf.uploadFile(repository, of, new File(file));
      }
      catch (ImageServerException e) {
        throw new IOException(e);
      }
    }
    df.updateMarked();
    of.setImage(img);
  }

  public int uploadPlane(OMECredentials credentials, MetadataRetrieve retrieve,
    int series, byte[] buf, int written)
    throws IOException
  {
    int x = retrieve.getPixelsSizeX(series).getValue().intValue();
    int y = retrieve.getPixelsSizeY(series).getValue().intValue();
    int z = retrieve.getPixelsSizeZ(series).getValue().intValue();
    int c = retrieve.getPixelsSizeC(series).getValue().intValue();
    int t = retrieve.getPixelsSizeT(series).getValue().intValue();
    String pixelTypeString = retrieve.getPixelsType(series).toString();
    int pixelType = FormatTools.pixelTypeFromString(pixelTypeString);
    int bpp = FormatTools.getBytesPerPixel(pixelType);
    boolean bigEndian = retrieve.getPixelsBigEndian(series).booleanValue();
    boolean isFloat = pixelTypeString.equals("float");
    String order = retrieve.getPixelsDimensionOrder(series).toString();

    is = ImageServer.getHTTPImageServer(credentials.omeis, rc.getSessionKey());
    if (credentials.imageID == -1) {
      try {
        credentials.imageID = is.newPixels(x, y, z, c, t, bpp, bigEndian, isFloat);
      }
      catch (ImageServerException e) {
        throw new IOException(e);
      }
    }

    int planeLength = x * y * bpp;
    int nChannels = buf.length / planeLength;

    for (int ch=0; ch<nChannels; ch++) {
      int[] coords = FormatTools.getZCTCoords(order, z, c, t, z * c * t, written);
      byte[] b = ImageTools.splitChannels(buf, ch, nChannels, bpp, false, true);
      try {
        is.setPlane(
          credentials.imageID, coords[0], coords[1], coords[2], b, bigEndian);

        written++;
      }
      catch (ImageServerException e) {
        throw new IOException(e);
      }
    }

    return written;
  }

  public void finalizeImport(OMECredentials credentials, MetadataRetrieve retrieve,
    int series)
    throws IOException
  {
    try {
      credentials.imageID = is.finishPixels(credentials.imageID);
    }
    catch (ImageServerException e) {
      throw new IOException(e);
    }

    String creationDate = retrieve.getImageAcquisitionDate(series).getValue();
    if (creationDate == null) {
      creationDate =
        DateTools.convertDate(System.currentTimeMillis(), DateTools.UNIX);
    }

    String imageName = retrieve.getImageName(series);
    if (imageName == null) {
      imageName = "new image " + credentials.imageID;
    }

    img.setOwner(experimenter);
    img.setInserted("now");
    img.setCreated(creationDate);
    img.setName(imageName);
    img.setDescription(retrieve.getImageDescription(series));
    df.update(img);

    ii = im.getImageImportMEX(img);
    ii.setExperimenter(experimenter);
    df.updateMarked();
    df.update(ii);

    pixels = (Pixels) df.createNew("Pixels");
    pixels.setRepository(repository);
    pixels.setImage(img);
    pixels.setModuleExecution(ii);
    pixels.setImageServerID(credentials.imageID);
    pixels.setSizeX(retrieve.getPixelsSizeX(series).getValue().intValue());
    pixels.setSizeY(retrieve.getPixelsSizeY(series).getValue().intValue());

    int z = retrieve.getPixelsSizeZ(series).getValue().intValue();
    int c = retrieve.getPixelsSizeC(series).getValue().intValue();
    int t = retrieve.getPixelsSizeT(series).getValue().intValue();

    pixels.setSizeZ(z);
    pixels.setSizeC(c);
    pixels.setSizeT(t);
    pixels.setPixelType(retrieve.getPixelsType(series).toString());

    CompositingSettings settings =
      CompositingSettings.createDefaultPGISettings(z, c, t);
    try {
      pf.setThumbnail(pixels, settings);
    }
    catch (ImageServerException e) {
      throw new IOException(e);
    }
    df.update(pixels);

    LogicalChannel logical = (LogicalChannel) df.createNew("LogicalChannel");
    logical.setImage(img);
    logical.setModuleExecution(ii);
    logical.setFluor("Gray 00");
    logical.setPhotometricInterpretation("monochrome");
    df.update(logical);

    PixelChannelComponent physical =
      (PixelChannelComponent) df.createNew("PixelChannelComponent");
    physical.setImage(img);
    physical.setPixels(pixels);
    physical.setIndex(new Integer(0));
    physical.setLogicalChannel(logical);
    physical.setModuleExecution(ii);
    df.update(physical);

    // upload original metadata, if available

    boolean isOMEXML = false;
    OMEXMLService service = null;
    try {
      ServiceFactory factory = new ServiceFactory();
      service = factory.getInstance(OMEXMLService.class);
      isOMEXML = service.isOMEXMLMetadata(retrieve);
    }
    catch (DependencyException e) {

    }

    if (isOMEXML) {
      Hashtable meta = service.getOriginalMetadata((OMEXMLMetadata) retrieve);
      String[] keys = (String[]) meta.keySet().toArray(new String[meta.size()]);
      for (String key : keys) {
        Attribute attribute = (Attribute) df.createNew("OriginalMetadata");
        attribute.setStringElement("Name", XMLTools.sanitizeXML(key));
        attribute.setStringElement("Value",
          XMLTools.sanitizeXML(meta.get(key).toString()));
        attribute.setImage(img);
        attribute.setModuleExecution(ii);
        df.update(attribute);
      }
    }

    // set state to finished

    of.setStatus("FINISHED");
    df.update(of);
    ii.setStatus("FINISHED");
    df.update(ii);
    img.setDefaultPixels(pixels);
    df.update(img);
    im.finishImport();
  }

  public CoreMetadata populateCoreMetadata(OMECredentials credentials)
    throws IOException
  {
    Criteria c = new Criteria();
    c.addWantedField("id");
    c.addWantedField("default_pixels");
    c.addWantedField("default_pixels", "PixelType");
    c.addWantedField("default_pixels", "SizeX");
    c.addWantedField("default_pixels", "SizeY");
    c.addWantedField("default_pixels", "SizeZ");
    c.addWantedField("default_pixels", "SizeC");
    c.addWantedField("default_pixels", "SizeT");
    c.addWantedField("default_pixels", "ImageServerID");
    c.addWantedField("default_pixels", "Repository");
    c.addWantedField("default_pixels.Repository", "ImageServerURL");
    c.addFilter("id", "=", String.valueOf(credentials.imageID));

    FieldsSpecification fs = new FieldsSpecification();
    fs.addWantedField("Repository");
    fs.addWantedField("Repository", "ImageServerURL");
    c.addWantedFields("default_pixels", fs);

    List images = df.retrieveList(Image.class, c);
    Image img = (Image) images.get(0);
    pixels = img.getDefaultPixels();
    Repository repository = pixels.getRepository();
    repository.setImageServerURL(credentials.omeis);

    BufferedImage thumb = null;
    try {
      thumb = pf.getThumbnail(pixels);
    }
    catch (ImageServerException e) {
      throw new IOException(e);
    }

    CoreMetadata core = new CoreMetadata();
    core.sizeX = pixels.getSizeX();
    core.sizeY = pixels.getSizeY();
    core.sizeZ = pixels.getSizeZ();
    core.sizeC = pixels.getSizeC();
    core.sizeT = pixels.getSizeT();
    core.pixelType = FormatTools.pixelTypeFromString(pixels.getPixelType());
    core.dimensionOrder = "XYZCT";
    core.imageCount = core.sizeZ * core.sizeC * core.sizeT;
    core.rgb = false;

    if (thumb != null) {
      core.thumbSizeX = thumb.getWidth();
      core.thumbSizeY = thumb.getHeight();
    }

    return core;
  }

  public HashMap<String, String> getOriginalMetadata(OMECredentials credentials)
    throws IOException
  {
    Criteria c = new Criteria();
    c.addWantedField("id");
    c.addWantedField("Name");
    c.addWantedField("Value");
    c.addWantedField("image_id");
    c.addFilter("image_id", "=", String.valueOf(credentials.imageID));
    List original = df.retrieveList("OriginalMetadata", c);

    HashMap<String, String> metadata = new HashMap<String, String>();
    for (Object item : original) {
      Attribute o = (Attribute) item;
      metadata.put(o.getStringElement("Name"), o.getStringElement("Value"));
    }
    return metadata;
  }

  public byte[] getPlane(int z, int c, int t, OMECredentials credentials)
    throws IOException
  {
    if (credentials.imageID != pixels.getID()) {
      populateCoreMetadata(credentials);
    }

    try {
      return pf.getPlane(pixels, z, c, t, false);
    }
    catch (ImageServerException e) {
      throw new IOException(e);
    }
  }

  public void close() {
    rc.logout();
    rc = null;
    df = null;
    pf = null;
    pixels = null;
    im = null;
    experimenter = null;
    repository = null;
    img = null;
    of = null;
    is = null;
  }


}
