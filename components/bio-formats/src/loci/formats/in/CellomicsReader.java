/*
 * #%L
 * OME Bio-Formats package for reading and converting biological file formats.
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

package loci.formats.in;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;

import loci.common.DateTools;
import loci.common.Location;
import loci.common.RandomAccessInputStream;
import loci.common.services.DependencyException;
import loci.common.services.ServiceFactory;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.MetadataTools;
import loci.formats.UnsupportedCompressionException;
import loci.formats.codec.ZlibCodec;
import loci.formats.meta.MetadataStore;
import loci.formats.services.MDBService;

import ome.xml.model.enums.NamingConvention;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveFloat;
import ome.xml.model.primitives.Timestamp;

/**
 * Reader for Cellomics C01 files.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="http://trac.openmicroscopy.org.uk/ome/browser/bioformats.git/components/bio-formats/src/loci/formats/in/CellomicsReader.java">Trac</a>,
 * <a href="http://git.openmicroscopy.org/?p=bioformats.git;a=blob;f=components/bio-formats/src/loci/formats/in/CellomicsReader.java;hb=HEAD">Gitweb</a></dd></dl>
 */
public class CellomicsReader extends FormatReader {

  // -- Constants --

  public static final int C01_MAGIC_BYTES = 16;

  // -- Fields --

  private String[] files;
  private String plateDescription;
  private String acqDate, acqTime;
  private String externalID;
  private String plateName;

  // -- Constructor --

  /** Constructs a new Cellomics reader. */
  public CellomicsReader() {
    super("Cellomics C01", new String[] {"c01", "dib"});
    domains = new String[] {FormatTools.LM_DOMAIN, FormatTools.HCS_DOMAIN};
    datasetDescription = "One or more .c01 files";
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessInputStream) */
  public boolean isThisType(RandomAccessInputStream stream) throws IOException {
    final int blockLen = 4;
    if (!FormatTools.validStream(stream, blockLen, false)) return false;
    return stream.readInt() == C01_MAGIC_BYTES;
  }

  /* @see loci.formats.IFormatReader#getDomains() */
  public String[] getDomains() {
    FormatTools.assertId(currentId, true, 1);
    return new String[] {FormatTools.HCS_DOMAIN};
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);

    int[] coords = getZCTCoords(no);
    int imageCount = files.length / getSeriesCount();
    int image = getIndex(0, coords[1], coords[2]);

    String file = files[getSeries() * imageCount + image];
    RandomAccessInputStream s = getDecompressedStream(file);

    int planeSize = FormatTools.getPlaneSize(this);
    s.seek(52 + coords[0] * planeSize);
    readPlane(s, x, y, w, h, buf);
    s.close();

    return buf;
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (!fileOnly) {
      files = null;
      plateDescription = null;
      acqDate = null;
      acqTime = null;
      externalID = null;
      plateName = null;
    }
  }

  /* @see loci.formats.IFormatReader#getSeriesUsedFiles(boolean) */
  public String[] getSeriesUsedFiles(boolean noPixels) {
    FormatTools.assertId(currentId, true, 1);
    return files;
  }

  /* @see loci.formats.IFormatReader#fileGroupOption(String) */
  public int fileGroupOption(String id) throws FormatException, IOException {
    return FormatTools.MUST_GROUP;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    super.initFile(id);

    // look for files with similar names
    Location baseFile = new Location(id).getAbsoluteFile();
    Location parent = baseFile.getParentFile();
    ArrayList<String> pixelFiles = new ArrayList<String>();

    plateName = getPlateName(baseFile.getName());

    if (plateName != null && isGroupFiles()) {
      String[] list = parent.list(true);
      for (String f : list) {
        if (plateName.equals(getPlateName(f)) &&
          (checkSuffix(f, "c01") || checkSuffix(f, "dib")))
        {
          pixelFiles.add(new Location(parent, f).getAbsolutePath());
        }
      }
    }
    else pixelFiles.add(id);

    files = pixelFiles.toArray(new String[pixelFiles.size()]);
    Arrays.sort(files);

    int wellRows = 0;
    int wellColumns = 0;
    int fields = 0;

    ArrayList<String> uniqueRows = new ArrayList<String>();
    ArrayList<String> uniqueCols = new ArrayList<String>();
    ArrayList<String> uniqueFields = new ArrayList<String>();
    ArrayList<String> uniqueTimepoints = new ArrayList<String>();
    for (String f : files) {
      String wellRow = getWellRow(f);
      String wellCol = getWellColumn(f);
      String field = getField(f);
      String timepoint = getTimepoint(f);

      if (!uniqueRows.contains(wellRow)) uniqueRows.add(wellRow);
      if (!uniqueCols.contains(wellCol)) uniqueCols.add(wellCol);
      if (!uniqueFields.contains(field)) uniqueFields.add(field);
      if (!uniqueTimepoints.contains(timepoint)) {
        uniqueTimepoints.add(timepoint);
      }
    }

    fields = uniqueFields.size();
    wellRows = uniqueRows.size();
    wellColumns = uniqueCols.size();

    if (fields * wellRows * wellColumns > files.length) {
      files = new String[] {id};
    }

    core = new CoreMetadata[files.length / uniqueTimepoints.size()];

    for (int i=0; i<core.length; i++) {
      core[i] = new CoreMetadata();
      core[i].sizeT = uniqueTimepoints.size();
    }

    in = getDecompressedStream(id);

    LOGGER.info("Reading header data");

    in.order(true);
    in.skipBytes(4);

    int x = in.readInt();
    int y = in.readInt();
    int nPlanes = in.readShort();
    int nBits = in.readShort();

    int compression = in.readInt();

    if (x * y * nPlanes * (nBits / 8) + 52 > in.length()) {
      throw new UnsupportedCompressionException(
        "Compressed pixel data is not yet supported.");
    }

    in.skipBytes(4);
    int pixelWidth = 0, pixelHeight = 0;

    if (getMetadataOptions().getMetadataLevel() != MetadataLevel.MINIMUM) {
      pixelWidth = in.readInt();
      pixelHeight = in.readInt();
      int colorUsed = in.readInt();
      int colorImportant = in.readInt();

      LOGGER.info("Populating metadata hashtable");

      addGlobalMeta("Image width", x);
      addGlobalMeta("Image height", y);
      addGlobalMeta("Number of planes", nPlanes);
      addGlobalMeta("Bits per pixel", nBits);
      addGlobalMeta("Compression", compression);
      addGlobalMeta("Pixels per meter (X)", pixelWidth);
      addGlobalMeta("Pixels per meter (Y)", pixelHeight);
      addGlobalMeta("Color used", colorUsed);
      addGlobalMeta("Color important", colorImportant);
    }

    LOGGER.info("Populating core metadata");

    for (int i=0; i<getSeriesCount(); i++) {
      core[i].sizeX = x;
      core[i].sizeY = y;
      core[i].sizeZ = nPlanes;
      core[i].sizeC = 1;
      core[i].imageCount = getSizeZ() * getSizeT();
      core[i].littleEndian = true;
      core[i].dimensionOrder = "XYCZT";
      core[i].pixelType =
        FormatTools.pixelTypeFromBytes(nBits / 8, false, false);
    }

    LOGGER.info("Looking for database file");

    String mdb = findMDB();
    if (mdb != null) {
      LOGGER.info("Parsing database file");
      parseMDB(mdb);
    }

    LOGGER.info("Populating metadata store");

    MetadataStore store = makeFilterMetadata();
    MetadataTools.populatePixels(store, this);

    store.setPlateID(MetadataTools.createLSID("Plate", 0), 0);
    store.setPlateName(plateName, 0);
    store.setPlateRowNamingConvention(NamingConvention.LETTER, 0);
    store.setPlateColumnNamingConvention(NamingConvention.NUMBER, 0);
    store.setPlateDescription(plateDescription, 0);
    store.setPlateExternalIdentifier(externalID, 0);

    int realRows = wellRows;
    int realCols = wellColumns;

    if (files.length == 1) {
      realRows = 1;
      realCols = 1;
    }
    else if (realRows <= 8 && realCols <= 12) {
      realRows = 8;
      realCols = 12;
    }
    else {
      realRows = 16;
      realCols = 24;
    }

    for (int row=0; row<realRows; row++) {
      for (int col=0; col<realCols; col++) {
        int well = row * realCols + col;

        if (files.length == 1) {
          String wellRow = getWellRow(files[0]);
          String wellColumn = getWellColumn(files[0]);
          row = wellRow.toUpperCase().charAt(0) - 'A';
          col = Integer.parseInt(wellColumn) - 1;
        }

        store.setWellID(MetadataTools.createLSID("Well", 0, well), 0, well);
        store.setWellRow(new NonNegativeInteger(row), 0, well);
        store.setWellColumn(new NonNegativeInteger(col), 0, well);
      }
    }

    for (int i=0; i<getSeriesCount(); i++) {
      String file = files[i];

      String field = getField(file);
      String wellRow = getWellRow(file);
      String wellColumn = getWellColumn(file);

      int row = wellRow.toUpperCase().charAt(0) - 'A';
      int col = Integer.parseInt(wellColumn) - 1;

      if (files.length == 1) {
        row = 0;
        col = 0;
      }

      String imageID = MetadataTools.createLSID("Image", i);
      store.setImageID(imageID, i);
      if (row < realRows && col < realCols) {

        int wellIndex = row * realCols + col;
        int fieldIndex = i % fields;

        String wellSampleID =
          MetadataTools.createLSID("WellSample", 0, wellIndex, fieldIndex);
        store.setWellSampleID(wellSampleID, 0, wellIndex, fieldIndex);
        store.setWellSampleIndex(
          new NonNegativeInteger(i), 0, wellIndex, fieldIndex);

        store.setWellSampleImageRef(imageID, 0, wellIndex, fieldIndex);
      }
      store.setImageName(
        "Well " + wellRow + wellColumn + ", Field #" + field, i);

      if (acqDate != null && acqTime != null) {
        String timestamp = acqDate.substring(0, acqDate.indexOf(" ")) + " " +
          acqTime.substring(acqTime.indexOf(" ") + 1);
        timestamp = DateTools.formatDate(timestamp, "M/d/yyyy HH:mm:ss");
        try {
          store.setImageAcquisitionDate(new Timestamp(timestamp), i);
        }
        catch (NullPointerException e) {
        }
      }
    }

    if (getMetadataOptions().getMetadataLevel() != MetadataLevel.MINIMUM) {
      // physical dimensions are stored as pixels per meter - we want them
      // in microns per pixel
      double width = pixelWidth == 0 ? 0.0 : 1000000.0 / pixelWidth;
      double height = pixelHeight == 0 ? 0.0 : 1000000.0 / pixelHeight;

      for (int i=0; i<getSeriesCount(); i++) {
        if (width > 0) {
          store.setPixelsPhysicalSizeX(new PositiveFloat(width), 0);
        }
        else {
          LOGGER.warn("Expected positive value for PhysicalSizeX; got {}",
            width);
        }
        if (height > 0) {
          store.setPixelsPhysicalSizeY(new PositiveFloat(height), 0);
        }
        else {
          LOGGER.warn("Expected positive value for PhysicalSizeY; got {}",
            height);
        }
      }
    }
  }

  // -- Helper methods --

  private String getPlateName(String filename) {
    int underscore = filename.lastIndexOf("_");
    if (underscore < 0) return null;
    return filename.substring(0, underscore);
  }

  private String getWellName(String filename) {
    String wellName = filename.substring(filename.lastIndexOf("f") - 3);
    while (!Character.isLetter(wellName.charAt(0)) ||
      !Character.isDigit(wellName.charAt(1)))
    {
      wellName = wellName.substring(1, wellName.length());
    }
    return wellName;
  }

  private String getWellRow(String filename) {
    return getWellName(filename).substring(0, 1);
  }

  private String getWellColumn(String filename) {
    return getWellName(filename).substring(1, 3);
  }

  private String getField(String filename) {
    String well = getWellName(filename);
    int start = well.indexOf("f") + 1;
    int end = start + 2;
    return well.substring(start, end);
  }

  private String getTimepoint(String filename) {
    int fieldIndex = filename.lastIndexOf("f");
    if (filename.charAt(fieldIndex - 7) == 't') {
      return filename.substring(fieldIndex - 6, fieldIndex);
    }
    return "0";
  }

  private RandomAccessInputStream getDecompressedStream(String filename)
    throws FormatException, IOException
  {
    RandomAccessInputStream s = new RandomAccessInputStream(filename);
    if (checkSuffix(filename, "c01")) {
      LOGGER.info("Decompressing file");

      s.seek(4);
      ZlibCodec codec = new ZlibCodec();
      byte[] file = codec.decompress(s, null);
      s.close();

      return new RandomAccessInputStream(file);
    }
    return s;
  }

  private String findMDB() {
    Location file = new Location(currentId).getAbsoluteFile();
    Location parent = file.getParentFile();

    String[] list = parent.list(true);
    for (String f : list) {
      if (checkSuffix(f, "mdb")) {
        String name = f.substring(0, f.lastIndexOf("."));
        if (file.getName().startsWith(name)) {
          return new Location(parent, f).getAbsolutePath();
        }
      }
    }
    return null;
  }

  private void parseMDB(String filename) throws FormatException, IOException {
    MDBService mdbService = null;
    try {
      ServiceFactory factory = new ServiceFactory();
      mdbService = factory.getInstance(MDBService.class);
    }
    catch (DependencyException e) {
      LOGGER.warn("MDB Tools Java library not found", e);
      return;
    }

    try {
      mdbService.initialize(filename);
    }
    catch (Exception e) {
      LOGGER.debug("", e);
      return;
    }

    Vector<String[]> asnPlate = mdbService.readTable("asnPlate");
    Vector<String[]> asnWell = mdbService.readTable("asnWell");
    Vector<String[]> asnFormFactor = mdbService.readTable("asnFormFactor");
    Vector<String[]> fImage = mdbService.readTable("FImage");

    mdbService.close();

    if (asnPlate != null) {
      addTable(asnPlate);
    }

    if (asnFormFactor != null) {
      addTable(asnFormFactor);
    }
  }

  private void addTable(Vector<String[]> table) {
    String[] columns = table.get(0);
    for (int row=1; row<table.size(); row++) {
      String[] rowData = table.get(row);
      for (int col=1; col<columns.length; col++) {
        String key = columns[0] + " " + columns[col];
        if (table.size() > 2) {
          key += " #" + row;
        }
        addGlobalMeta(key, rowData[col - 1]);

        if (key.equals("asnFormFactor Description")) {
          plateDescription = rowData[col - 1];
        }
        else if (key.equals("asnPlate CreationDate")) {
          acqDate = rowData[col - 1];
        }
        else if (key.equals("asnPlate CreationTime")) {
          acqTime = rowData[col - 1];
        }
        else if (key.equals("asnPlate ScanID")) {
          externalID = rowData[col - 1];
        }
        else if (key.equals("asnPlate Name")) {
          plateName = rowData[col - 1];
        }
      }
    }
  }

}
