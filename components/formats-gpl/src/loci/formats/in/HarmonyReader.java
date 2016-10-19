/*
 * #%L
 * OME Bio-Formats package for reading and converting biological file formats.
 * %%
 * Copyright (C) 2005 - 2016 Open Microscopy Environment:
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
import java.util.HashMap;
import java.util.HashSet;

import loci.common.DataTools;
import loci.common.Location;
import loci.common.RandomAccessInputStream;
import loci.common.xml.XMLTools;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.IHCSReader;
import loci.formats.MetadataTools;
import loci.formats.meta.MetadataStore;
import loci.formats.tiff.IFD;
import loci.formats.tiff.TiffParser;

import ome.units.quantity.Length;
import ome.units.unit.Unit;
import ome.xml.model.enums.AcquisitionMode;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.primitives.Timestamp;

/**
 * HarmonyReader is the file format reader for PerkinElmer Harmony data.
 *
 * @author Melissa Linkert melissa at glencoesoftware.com
 */
public class HarmonyReader extends FormatReader implements IHCSReader {

  // -- Constants --

  private static final String[] XML_FILES = {"Index.idx.xml", "Index.ref.xml"};
  private static final String MAGIC = "Harmony";
  private static final int XML_TAG = 65500;

  // -- Fields --

  private HarmonyColumbusPlane[][] planes;
  private MinimalTiffReader reader;
  private ArrayList<String> metadataFiles = new ArrayList<String>();
  private String plateID;

  // -- Constructor --

  /** Constructs a new Harmony reader. */
  public HarmonyReader() {
    super("PerkinElmer Harmony", new String[] {"xml"});
    domains = new String[] {FormatTools.HCS_DOMAIN};
    suffixSufficient = false;
    hasCompanionFiles = true;
    datasetDescription =
      "Directory with XML file and one .tif/.tiff file per plane";
  }

  // -- IHCSReader API methods --

  /* @see loci.formats.IFormatReader#getPlateIdentifier() */
  public String getPlateIdentifier() throws FormatException, IOException {
    FormatTools.assertId(currentId, true, 1);
    return plateID;
  }

  // -- IFormatReader API methods --

  /* @see loci.formats.IFormatReader#getRequiredDirectories(String[]) */
  @Override
  public int getRequiredDirectories(String[] files)
    throws FormatException, IOException
  {
    return 1;
  }

  /* @see loci.formats.IFormatReader#isSingleFile(String) */
  @Override
  public boolean isSingleFile(String id) throws FormatException, IOException {
    return false;
  }

  /* @see loci.formats.IFormatReader#fileGroupOption(String) */
  @Override
  public int fileGroupOption(String id) throws FormatException, IOException {
    return FormatTools.MUST_GROUP;
  }

  /* @see loci.formats.IFormatReader#isThisType(String, boolean) */
  @Override
  public boolean isThisType(String name, boolean open) {
    String localName = new Location(name).getName();
    boolean exists = false;
    for (String XML_FILE : XML_FILES) {
      if (localName.equals(XML_FILE)) {
        exists = true;
        break;
      }
    }
    if (!exists) {
      Location parent = new Location(name).getAbsoluteFile().getParentFile();
      for (String XML_FILE : XML_FILES) {
        Location xml = new Location(parent, XML_FILE);
        if (xml.exists()) {
          exists = true;
          break;
        }
      }
    }
    if (!exists) {
      return false;
    }

    return super.isThisType(name, open);
  }

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessInputStream) */
  @Override
  public boolean isThisType(RandomAccessInputStream stream) throws IOException {
    String xml = stream.readString(1024);
    if (xml.indexOf(MAGIC) > 0) {
      return true;
    }
    stream.seek(0);
    TiffParser p = new TiffParser(stream);
    IFD ifd = p.getFirstIFD();
    if (ifd != null) {
      Object s = ifd.getIFDValue(XML_TAG);
      if (s == null) return false;
      xml = s instanceof String[] ? ((String[]) s)[0] : s.toString();
      int index = xml.indexOf(MAGIC);
      return index < 1024 && index >= 0;
    }
    return false;
  }

  /* @see loci.formats.IFormatReader#getUsedFiles(boolean) */
  @Override
  public String[] getUsedFiles(boolean noPixels) {
    FormatTools.assertId(currentId, true, 1);

    ArrayList<String> files = new ArrayList<String>();
    files.addAll(metadataFiles);
    if (!noPixels) {
      for (HarmonyColumbusPlane[] well : planes) {
        for (HarmonyColumbusPlane p : well) {
          if (p != null && p.filename != null) {
            files.add(p.filename);
          }
        }
      }
    }
    return files.toArray(new String[files.size()]);
  }

  /* @see loci.formats.IFormatReader#getSeriesUsedFiles(boolean) */
  @Override
  public String[] getSeriesUsedFiles(boolean noPixels) {
    FormatTools.assertId(currentId, true, 1);

    ArrayList<String> files = new ArrayList<String>();
    files.addAll(metadataFiles);
    if (!noPixels && getSeries() < planes.length) {
      for (HarmonyColumbusPlane p : planes[getSeries()]) {
        files.add(p.filename);
      }
    }

    return files.toArray(new String[files.size()]);
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  @Override
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (!fileOnly) {
      if (reader != null) {
        reader.close();
      }
      reader = null;
      planes = null;
      metadataFiles.clear();
      plateID = null;
    }
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  @Override
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);

    int seriesIndex = lookupSeriesIndex(getSeries());
    LOGGER.trace("series = {}, seriesIndex = {}", getSeries(), seriesIndex);

    if (seriesIndex < planes.length && no < planes[seriesIndex].length) {
      HarmonyColumbusPlane p = planes[seriesIndex][no];

      if (new Location(p.filename).exists()) {
        if (reader == null) {
          reader = new MinimalTiffReader();
        }
        try {
          LOGGER.debug("reading series = {}, no = {} from {}", getSeries(), no, p.filename);
          reader.setId(p.filename);
          reader.openBytes(0, buf, x, y, w, h);
        }
        finally {
          reader.close();
        }
      }
      else {
        LOGGER.debug("Could not find file {}", p.filename);
      }
    }
    else {
      LOGGER.debug("Invalid series ({}) and plane ({})", getSeries(), no);
    }

    return buf;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  @Override
  protected void initFile(String id) throws FormatException, IOException {
    // make sure that we have the XML file and not a TIFF file

    if (!checkSuffix(id, "xml")) {
      LOGGER.info("Checking for corresponding XML file");
      Location parent = new Location(id).getAbsoluteFile().getParentFile();
      Location xml = new Location(parent, XML_FILES[0]);
      if (!xml.exists()) {
        throw new FormatException("Could not find XML file " +
          xml.getAbsolutePath());
      }
      initFile(xml.getAbsolutePath());
      return;
    }
    else {
      super.initFile(id);
    }

    // assemble list of other metadata/analysis results files

    LOGGER.info("Scanning for companion folders");
    Location currentFile = new Location(currentId).getAbsoluteFile();
    metadataFiles.add(currentFile.getAbsolutePath());
    Location parent = currentFile.getParentFile().getParentFile();
    String[] list = parent.list(true);
    Arrays.sort(list);
    for (String f : list) {
      Location path = new Location(parent, f);
      if (path.isDirectory()) {
        String[] companionFolders = path.list(true);
        Arrays.sort(companionFolders);
        for (String folder : companionFolders) {
          LOGGER.trace("Found folder {}", folder);
          if (!f.equals("Images") || !checkSuffix(folder, "tiff")) {
            String metadataFile = new Location(path, folder).getAbsolutePath();
            if (!metadataFile.equals(currentFile.getAbsolutePath())) {
              metadataFiles.add(metadataFile);
              LOGGER.trace("Adding metadata file {}", metadataFile);
            }
          }
        }
      }
      else {
        metadataFiles.add(path.getAbsolutePath());
        LOGGER.trace("Adding metadata file {}", path.getAbsolutePath());
      }
    }

    // parse plate layout and image dimensions from the XML file

    LOGGER.info("Parsing XML metadata");
    String xmlData = DataTools.readFile(id);
    HarmonyColumbusHandler handler = new HarmonyColumbusHandler(currentId);
    XMLTools.parseXML(xmlData, handler);

    // sort the list of images by well and field indices

    LOGGER.info("Assembling plate dimensions");
    ArrayList<HarmonyColumbusPlane> planeList = handler.getPlanes();

    HashSet<Integer> uniqueRows = new HashSet<Integer>();
    HashSet<Integer> uniqueCols = new HashSet<Integer>();
    HashSet<Integer> uniqueFields = new HashSet<Integer>();
    HashSet<Integer> uniqueZs = new HashSet<Integer>();
    HashSet<Integer> uniqueTs = new HashSet<Integer>();
    HashSet<Integer> uniqueCs = new HashSet<Integer>();

    for (HarmonyColumbusPlane p : planeList) {
      uniqueRows.add(p.row);
      uniqueCols.add(p.col);
      uniqueFields.add(p.field);
      uniqueZs.add(p.z);
      uniqueCs.add(p.c);
      uniqueTs.add(p.t);
    }

    Integer[] rows = uniqueRows.toArray(new Integer[uniqueRows.size()]);
    Integer[] cols = uniqueCols.toArray(new Integer[uniqueCols.size()]);
    Integer[] fields = uniqueFields.toArray(new Integer[uniqueFields.size()]);
    Integer[] zs = uniqueZs.toArray(new Integer[uniqueZs.size()]);
    Integer[] cs = uniqueCs.toArray(new Integer[uniqueCs.size()]);
    Integer[] ts = uniqueTs.toArray(new Integer[uniqueTs.size()]);

    Arrays.sort(rows);
    Arrays.sort(cols);
    Arrays.sort(fields);
    Arrays.sort(zs);
    Arrays.sort(ts);
    Arrays.sort(cs);

    int seriesCount = rows.length * cols.length * fields.length;
    core.clear();

    planes = new HarmonyColumbusPlane[seriesCount][zs.length * cs.length * ts.length];

    int nextSeries = 0;
    for (int row=0; row<rows.length; row++) {
      for (int col=0; col<cols.length; col++) {
        for (int field=0; field<fields.length; field++) {
          int nextPlane = 0;
          for (int t=0; t<ts.length; t++) {
            for (int z=0; z<zs.length; z++) {
              for (int c=0; c<cs.length; c++) {
                for (HarmonyColumbusPlane p : planeList) {
                  if (p.row == rows[row] && p.col == cols[col] &&
                    p.field == fields[field] && p.t == ts[t] && p.z == zs[z] &&
                    p.c == cs[c])
                  {
                    planes[nextSeries][nextPlane] = p;
                    break;
                  }
                }
                nextPlane++;
              }
            }
          }
          nextSeries++;
        }
      }
    }

    LOGGER.info("Populating core metadata");
    reader = new MinimalTiffReader();

    for (int i=0; i<seriesCount; i++) {
      if (planes[i][0] == null) {
        continue;
      }
      planes[i][0].image = core.size();
      CoreMetadata ms = new CoreMetadata();
      core.add(ms);
      ms.sizeX = planes[i][0].x;
      ms.sizeY = planes[i][0].y;
      ms.sizeZ = uniqueZs.size();
      ms.sizeC = uniqueCs.size();
      ms.sizeT = uniqueTs.size();
      ms.dimensionOrder = "XYCZT";
      ms.rgb = false;
      ms.imageCount = getSizeZ() * getSizeC() * getSizeT();

      RandomAccessInputStream s =
        new RandomAccessInputStream(planes[i][0].filename, 16);
      TiffParser parser = new TiffParser(s);
      parser.setDoCaching(false);

      IFD firstIFD = parser.getFirstIFD();
      ms.littleEndian = firstIFD.isLittleEndian();
      ms.pixelType = firstIFD.getPixelType();
      s.close();
    }

    HashMap<String, String> xmlMetadata = handler.getMetadataMap();
    for (String key : xmlMetadata.keySet()) {
      addGlobalMeta(key, xmlMetadata.get(key));
    }

    plateID = handler.getPlateIdentifier();

    // populate the MetadataStore

    LOGGER.info("Populating OME metadata");
    MetadataStore store = makeFilterMetadata();
    MetadataTools.populatePixels(store, this, true);

    store.setPlateID(MetadataTools.createLSID("Plate", 0), 0);
    store.setPlateRows(new PositiveInteger(handler.getPlateRows()), 0);
    store.setPlateColumns(new PositiveInteger(handler.getPlateColumns()), 0);

    String plateAcqID = MetadataTools.createLSID("PlateAcquisition", 0, 0);
    store.setPlateAcquisitionID(plateAcqID, 0, 0);

    PositiveInteger fieldCount = FormatTools.getMaxFieldCount(fields.length);
    if (fieldCount != null) {
      store.setPlateAcquisitionMaximumFieldCount(fieldCount, 0, 0);
    }
    String startTime = handler.getMeasurementTime();
    if (startTime != null) {
      store.setPlateAcquisitionStartTime(new Timestamp(startTime), 0, 0);
    }

    for (int row=0; row<rows.length; row++) {
      for (int col=0; col<cols.length; col++) {
        int well = row * cols.length + col;
        LOGGER.debug("Populating well row = {}, col = {}, well = {}", row, col, well);
        store.setWellID(MetadataTools.createLSID("Well", 0, well), 0, well);
        store.setWellRow(new NonNegativeInteger(rows[row]), 0, well);
        store.setWellColumn(new NonNegativeInteger(cols[col]), 0, well);

        for (int field=0; field<fields.length; field++) {
          int planesIndex = well * fields.length + field;
          LOGGER.debug("Populating field = {}, index = {}", field, planesIndex);

          if (planes[planesIndex][0] == null) {
            // variable number of fields; the field was not acquired in this well
            continue;
          }

          int imageIndex = planes[planesIndex][0].image;
          if (imageIndex == -1) {
            continue;
          }
          String wellSampleID =
            MetadataTools.createLSID("WellSample", 0, well, field);
          store.setWellSampleID(wellSampleID, 0, well, field);
          store.setWellSampleIndex(
            new NonNegativeInteger(imageIndex), 0, well, field);
          String imageID = MetadataTools.createLSID("Image", imageIndex);
          store.setImageID(imageID, imageIndex);
          store.setWellSampleImageRef(imageID, 0, well, field);

          String name = "Well Row " + rows[row] + ", Column " + cols[col] + ", Field " + (field + 1);
          store.setImageName(name, imageIndex);
          store.setPlateAcquisitionWellSampleRef(
            wellSampleID, 0, 0, imageIndex);
        }
      }
    }

    if (getMetadataOptions().getMetadataLevel() != MetadataLevel.MINIMUM) {
      store.setPlateName(handler.getPlateName(), 0);
      store.setPlateDescription(handler.getPlateDescription(), 0);
      store.setPlateExternalIdentifier(plateID, 0);

      String experimenterID = MetadataTools.createLSID("Experimenter", 0);
      store.setExperimenterID(experimenterID, 0);
      store.setExperimenterLastName(handler.getExperimenterName(), 0);

      for (int i=0; i<getSeriesCount(); i++) {
        if (planes[i][0] == null) {
          continue;
        }
        store.setImageExperimenterRef(experimenterID, i);
        if (planes[i][0].acqTime != null) {
          store.setImageAcquisitionDate(new Timestamp(planes[i][0].acqTime), i);
        }

        for (int c=0; c<getSizeC(); c++) {
          store.setChannelName(planes[i][c].channelName, i, c);
          store.setChannelAcquisitionMode(getAcquisitionMode(planes[i][c].acqType), i, c);
          if (planes[i][c].channelType != null) {
            store.setChannelContrastMethod(getContrastMethod(planes[i][c].channelType), i, c);
          }
          if (planes[i][c].emWavelength != null) {
            store.setChannelEmissionWavelength(planes[i][c].emWavelength, i, c);
          }
          if (planes[i][c].exWavelength != null) {
            store.setChannelExcitationWavelength(planes[i][c].exWavelength, i, c);
          }
        }

        store.setPixelsPhysicalSizeX(planes[i][0].resolutionX, i);
        store.setPixelsPhysicalSizeY(planes[i][0].resolutionY, i);

        if (getSizeZ() > 1) {
          Unit<Length> firstZUnit = planes[i][0].positionZ.unit();
          double firstZ = planes[i][0].positionZ.value().doubleValue();
          double lastZ = planes[i][planes[i].length - 1].positionZ.value(firstZUnit).doubleValue();
          double averageStep = (lastZ - firstZ) / (getSizeZ() - 1);
          store.setPixelsPhysicalSizeZ(FormatTools.getPhysicalSizeZ(averageStep, firstZUnit), i);
        }

        for (int p=0; p<getImageCount(); p++) {
          store.setPlanePositionX(planes[i][p].positionX, i, p);
          store.setPlanePositionY(planes[i][p].positionY, i, p);
          store.setPlanePositionZ(planes[i][p].positionZ, i, p);
          store.setPlaneDeltaT(planes[i][p].deltaT, i, p);
        }
      }

    }
  }

  private int lookupSeriesIndex(int seriesIndex) {
    int index = 0;
    for (int i=0; i<planes.length; i++) {
      if (planes[i][0] == null) {
        continue;
      }
      if (index == seriesIndex) {
        return i;
      }
      index++;
    }
    return -1;
  }

  @Override
  protected AcquisitionMode getAcquisitionMode(String mode) throws FormatException {
    if (mode == null) {
      return null;
    }
    if (mode.equalsIgnoreCase("nipkowconfocal")) {
      return AcquisitionMode.SPINNINGDISKCONFOCAL;
    }
    else if (mode.equalsIgnoreCase("confocal")) {
      return AcquisitionMode.LASERSCANNINGCONFOCALMICROSCOPY;
    }
    else if (mode.equalsIgnoreCase("nonconfocal")) {
      return AcquisitionMode.WIDEFIELD;
    }
    return super.getAcquisitionMode(mode);
  }

}
