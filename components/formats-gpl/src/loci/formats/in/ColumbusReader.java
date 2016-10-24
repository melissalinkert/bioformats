/*
 * #%L
 * OME Bio-Formats package for reading and converting biological file formats.
 * %%
 * Copyright (C) 2005 - 2014 Open Microscopy Environment:
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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

import loci.common.Constants;
import loci.common.DataTools;
import loci.common.Location;
import loci.common.RandomAccessInputStream;
import loci.common.xml.BaseHandler;
import loci.common.xml.XMLTools;
import loci.formats.CoreMetadata;
import loci.formats.FormatException;
import loci.formats.FormatReader;
import loci.formats.FormatTools;
import loci.formats.IHCSReader;
import loci.formats.MetadataTools;
import loci.formats.meta.MetadataStore;

import ome.xml.model.enums.AcquisitionMode;
import ome.xml.model.primitives.Color;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.primitives.Timestamp;

import org.xml.sax.Attributes;

/**
 * ColumbusReader is the file format reader for screens exported from PerkinElmer Columbus.
 *
 * @author Melissa Linkert melissa at glencoesoftware.com
 */
public class ColumbusReader extends FormatReader implements IHCSReader {

  // -- Constants --

  private static final String XML_FILE = "MeasurementIndex.ColumbusIDX.xml";
  private static final String MAGIC = "ColumbusMeasurementIndex";

  // -- Fields --

  private ArrayList<String> metadataFiles = new ArrayList<String>();
  private ArrayList<String> imageIndexPaths = new ArrayList<String>();
  private ArrayList<HarmonyColumbusPlane> planes = new ArrayList<HarmonyColumbusPlane>();
  private HarmonyColumbusHandler imageHandler;
  private MinimalTiffReader reader;

  private int nFields = 0;
  private String acquisitionDate;
  private String plateID;

  // -- Constructor --

  /** Constructs a new Columbus reader. */
  public ColumbusReader() {
    super("PerkinElmer Columbus", new String[] {"xml"});
    domains = new String[] {FormatTools.HCS_DOMAIN};
    suffixSufficient = false;
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
  public int getRequiredDirectories(String[] files)
    throws FormatException, IOException
  {
    return 2;
  }

  /* @see loci.formats.IFormatReader#isSingleFile(String) */
  public boolean isSingleFile(String id) throws FormatException, IOException {
    return false;
  }

  /* @see loci.formats.IFormatReader#fileGroupOption(String) */
  public int fileGroupOption(String id) throws FormatException, IOException {
    return FormatTools.MUST_GROUP;
  }

  /* @see loci.formats.IFormatReader#isThisType(String, boolean) */
  public boolean isThisType(String name, boolean open) {
    String localName = new Location(name).getName();
    if (localName.equals(XML_FILE)) {
      return true;
    }
    Location parent = new Location(name).getAbsoluteFile().getParentFile();
    parent = parent.getParentFile();
    Location xml = new Location(parent, XML_FILE);
    if (!xml.exists()) {
      return false;
    }

    return super.isThisType(name, open);
  }

  /* @see loci.formats.IFormatReader#isThisType(RandomAccessInputStream) */
  public boolean isThisType(RandomAccessInputStream stream) throws IOException {
    String check = stream.readString(1024);
    return check.indexOf(MAGIC) > 0;
  }

  /* @see loci.formats.IFormatReader#getSeriesUsedFiles(boolean) */
  public String[] getSeriesUsedFiles(boolean noPixels) {
    FormatTools.assertId(currentId, true, 1);

    ArrayList<String> files = new ArrayList<String>();
    files.add(currentId);
    for (String file : metadataFiles) {
      if (new Location(file).exists()) {
        files.add(file);
      }
    }

    if (!noPixels) {
      for (HarmonyColumbusPlane p : planes) {
        if (p.series == getSeries() && !files.contains(p.filename)) {
          if (new Location(p.filename).exists()) {
            files.add(p.filename);
          }
        }
      }
    }

    return files.toArray(new String[files.size()]);
  }

  /* @see loci.formats.IFormatReader#close(boolean) */
  public void close(boolean fileOnly) throws IOException {
    super.close(fileOnly);
    if (!fileOnly) {
      if (reader != null) {
        reader.close();
      }
      reader = null;
      metadataFiles.clear();
      planes.clear();
      nFields = 0;
      acquisitionDate = null;
      plateID = null;
      imageHandler = null;
      imageIndexPaths.clear();
    }
  }

  /**
   * @see loci.formats.IFormatReader#openBytes(int, byte[], int, int, int, int)
   */
  public byte[] openBytes(int no, byte[] buf, int x, int y, int w, int h)
    throws FormatException, IOException
  {
    FormatTools.checkPlaneParameters(this, no, buf.length, x, y, w, h);

    int[] zct = getZCTCoords(no);
    HarmonyColumbusPlane p = null;
    for (HarmonyColumbusPlane plane : planes) {
      if (plane.series == getSeries() && plane.t == zct[2] &&
        plane.c - 1 == zct[1])
      {
        p = plane;
        break;
      }
    }

    if (p != null && new Location(p.filename).exists()) {
      reader.setId(p.filename);
      reader.openBytes(p.fileIndex, buf, x, y, w, h);
    }
    else {
      Arrays.fill(buf, (byte) 0);
    }
    return buf;
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    // make sure that we have the XML file and not a TIFF file

    if (!checkSuffix(id, "xml")) {
      Location parent = new Location(id).getAbsoluteFile().getParentFile();
      Location xml = new Location(parent, XML_FILE);
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

    Location parent = new Location(currentId).getAbsoluteFile().getParentFile();

    // parse plate layout and image dimensions from the XML files

    String xmlData = DataTools.readFile(id);
    MeasurementHandler handler = new MeasurementHandler();
    XMLTools.parseXML(xmlData, handler);

    String[] parentDirectories = parent.list(true);
    Arrays.sort(parentDirectories);
    ArrayList<String> timepointDirs = new ArrayList<String>();
    for (String file : parentDirectories) {
      Location absFile = new Location(parent, file);
      if (absFile.isDirectory()) {
        timepointDirs.add(absFile.getAbsolutePath());
        for (String f : absFile.list(true)) {
          if (!checkSuffix(f, "tif")) {
            metadataFiles.add(file + File.separator + f);
          }
        }
      }
    }

    for (int i=0; i<metadataFiles.size(); i++) {
      String metadataFile = metadataFiles.get(i);
      int end = metadataFile.indexOf(File.separator);
      String timepointPath =
        end < 0 ? "" : parent + File.separator + metadataFile.substring(0, end);
      Location f = new Location(parent + File.separator + metadataFile);
      if (!f.exists()) {
        metadataFile = metadataFile.substring(end + 1);
        f = new Location(parent, metadataFile);
      }
      String path = f.getAbsolutePath();
      metadataFiles.set(i, path);
    }

    for (String path : imageIndexPaths) {
      int timepoint = timepointDirs.indexOf(path);
      if (timepointDirs.size() == 0) {
        timepoint = 0;
      }
      parseImageXML(path, timepoint);
    }

    // process plane list to determine plate size

    HarmonyColumbusPlane[] tmpPlanes = planes.toArray(new HarmonyColumbusPlane[planes.size()]);
    Arrays.sort(tmpPlanes);
    planes.clear();

    reader = new MinimalTiffReader();
    reader.setId(tmpPlanes[0].filename);
    core = reader.getCoreMetadataList();

    CoreMetadata m = core.get(0);

    HashSet<Integer> uniqueSamples = new HashSet<Integer>();
    HashSet<Integer> uniqueRows = new HashSet<Integer>();
    HashSet<Integer> uniqueCols = new HashSet<Integer>();
    HashSet<Integer> uniqueZs = new HashSet<Integer>();
    HashSet<Integer> uniqueCs = new HashSet<Integer>();
    HashSet<Integer> uniqueTs = new HashSet<Integer>();
    HashSet<Integer> uniqueFields = new HashSet<Integer>();

    for (HarmonyColumbusPlane p : tmpPlanes) {
      planes.add(p);

      int sampleIndex = p.row * imageHandler.getPlateColumns() + p.col;
      uniqueSamples.add(sampleIndex);
      uniqueFields.add(p.field);
      uniqueRows.add(p.row);
      uniqueCols.add(p.col);
      uniqueZs.add(p.z);
      uniqueCs.add(p.c);
      uniqueTs.add(p.t);
    }

    nFields = uniqueFields.size();
    m.sizeZ = uniqueZs.size();
    m.sizeC = uniqueCs.size();
    m.sizeT = uniqueTs.size();
    m.imageCount = getSizeZ() * getSizeC() * getSizeT();
    m.dimensionOrder = "XYCTZ";
    m.rgb = false;

    int seriesCount = uniqueSamples.size() * nFields;
    for (int i=1; i<seriesCount; i++) {
      core.add(m);
    }

    // populate the MetadataStore

    MetadataStore store = makeFilterMetadata();
    MetadataTools.populatePixels(store, this, true);

    String experimenterID = MetadataTools.createLSID("Experimenter", 0);
    store.setExperimenterID(experimenterID, 0);
    store.setExperimenterLastName(imageHandler.getExperimenterName(), 0);

    store.setScreenID(MetadataTools.createLSID("Screen", 0), 0);
    store.setScreenName(handler.getScreenName(), 0);
    store.setPlateID(MetadataTools.createLSID("Plate", 0), 0);
    store.setPlateName(imageHandler.getPlateName(), 0);
    store.setPlateDescription(imageHandler.getPlateDescription(), 0);
    store.setPlateExternalIdentifier(plateID, 0);
    store.setPlateRows(new PositiveInteger(imageHandler.getPlateRows()), 0);
    store.setPlateColumns(new PositiveInteger(imageHandler.getPlateColumns()), 0);

    String plateAcqID = MetadataTools.createLSID("PlateAcquisition", 0, 0);
    store.setPlateAcquisitionID(plateAcqID, 0, 0);
    store.setPlateAcquisitionMaximumFieldCount(FormatTools.getMaxFieldCount(nFields), 0, 0);
    String startTime = imageHandler.getMeasurementTime();
    if (startTime != null) {
      store.setPlateAcquisitionStartTime(new Timestamp(startTime), 0, 0);
    }

    String imagePrefix = imageHandler.getPlateName() + " Well ";
    int wellSample = 0;

    int nextWell = -1;
    Timestamp date = new Timestamp(acquisitionDate);
    long timestampSeconds = date.asInstant().getMillis() / 1000;

    for (Integer row : uniqueRows) {
      for (Integer col : uniqueCols) {
        if (!uniqueSamples.contains(row * imageHandler.getPlateColumns() + col)) {
          continue;
        }

        nextWell++;
        store.setWellID(MetadataTools.createLSID("Well", 0, nextWell), 0, nextWell);
        store.setWellRow(new NonNegativeInteger(row), 0, nextWell);
        store.setWellColumn(new NonNegativeInteger(col), 0, nextWell);

        for (int field=0; field<nFields; field++) {
          HarmonyColumbusPlane p = lookupPlane(row, col, field, 0, 0);
          String wellSampleID = MetadataTools.createLSID("WellSample", 0, nextWell, field);
          store.setWellSampleID(wellSampleID, 0, nextWell, field);
          store.setWellSampleIndex(new NonNegativeInteger(wellSample), 0, nextWell, field);

          if (p != null) {
            store.setWellSamplePositionX(p.positionX, 0, nextWell, field);
            store.setWellSamplePositionY(p.positionY, 0, nextWell, field);
            store.setPlateAcquisitionWellSampleRef(wellSampleID, 0, 0, wellSample);
          }

          String imageID = MetadataTools.createLSID("Image", wellSample);
          store.setImageID(imageID, wellSample);
          store.setImageExperimenterRef(experimenterID, wellSample);
          store.setWellSampleImageRef(imageID, 0, nextWell, field);

          store.setImageName(
            imagePrefix + (char) (row + 'A') + (col + 1) + " Field #" + (field + 1), wellSample);
          store.setImageAcquisitionDate(date, wellSample);
          if (p != null) {
            p.series = wellSample;

            store.setPixelsPhysicalSizeX(p.resolutionX, p.series);
            store.setPixelsPhysicalSizeY(p.resolutionY, p.series);

            for (int c=0; c<getSizeC(); c++) {
              p = lookupPlane(row, col, field, 0, c);
              if (p != null) {
                p.series = wellSample;
                store.setChannelName(p.channelName, p.series, c);
                if (p.acqType != null) {
                  store.setChannelAcquisitionMode(getAcquisitionMode(p.acqType), p.series, c);
                }
                if (p.channelType != null) {
                  store.setChannelContrastMethod(getContrastMethod(p.channelType), p.series, c);
                }
                if (p.emWavelength != null) {
                  store.setChannelEmissionWavelength(p.emWavelength, p.series, c);
                }
                if (p.exWavelength != null) {
                  store.setChannelExcitationWavelength(p.exWavelength, p.series, c);
                }
              }

              for (int t=0; t<getSizeT(); t++) {
                p = lookupPlane(row, col, field, t, c);
                if (p != null) {
                  p.series = wellSample;
                  int index = getIndex(0, c, t);
                  store.setPlaneDeltaT(p.deltaT, p.series, index);
                  store.setPlanePositionX(p.positionX, p.series, index);
                  store.setPlanePositionY(p.positionY, p.series, index);
                  store.setPlanePositionZ(p.positionZ, p.series, index);
                }
              }
            }
          }
          wellSample++;
        }
      }
    }
  }

  // -- Helper methods --

  private void parseImageXML(String filename, int externalTime) throws FormatException, IOException {
    LOGGER.info("Parsing image data from {} with timepoint {}", filename, externalTime);
    String xml = DataTools.readFile(filename);
    Location parent = new Location(filename).getParentFile();

    imageHandler = new HarmonyColumbusHandler(filename);
    XMLTools.parseXML(xml, imageHandler);

    plateID = imageHandler.getPlateIdentifier();
    acquisitionDate = imageHandler.getMeasurementTime();

    ArrayList<HarmonyColumbusPlane> planeList = imageHandler.getPlanes();
    for (HarmonyColumbusPlane p : planeList) {
      if (p.t == 0) {
        p.t = externalTime;
      }
      else {
        p.t--;
      }
      planes.add(p);
    }
  }

  private HarmonyColumbusPlane lookupPlane(int row, int col, int field, int t, int c) {
    for (HarmonyColumbusPlane p : planes) {
      if (p.row == row && p.col == col && p.field - 1 == field &&
        p.t == t && p.c - 1 == c)
      {
        return p;
      }
    }
    LOGGER.warn("Could not find plane for row={}, column={}, field={}, t={}, c={}",
      new Object[] {row, col, field, t, c});
    return null;
  }

  // -- Helper classes --


  class MeasurementHandler extends BaseHandler {
    // -- Fields --

    private String currentName;

    private String screenName;
    private String plateName;
    private String plateType;
    private String measurementID;
    private String measurementName;
    private Integer plateRows;
    private Integer plateColumns;
    private String type;

    private StringBuffer currentValue;

    // -- MeasurementHandler API methods --

    public String getScreenName() {
      return screenName;
    }

    public String getPlateName() {
      return plateName;
    }

    public String getPlateType() {
      return plateType;
    }

    public String getMeasurementID() {
      return measurementID;
    }

    public String getMeasurementName() {
      return measurementName;
    }

    public Integer getPlateRows() {
      return plateRows;
    }

    public Integer getPlateColumns() {
      return plateColumns;
    }

    // -- DefaultHandler API methods --

    public void characters(char[] ch, int start, int length) {
      String value = new String(ch, start, length);
      currentValue.append(value);
    }

    public void startElement(String uri, String localName, String qName,
      Attributes attributes)
    {
      currentName = qName;

      for (int i=0; i<attributes.getLength(); i++) {
        String name = attributes.getQName(i);
        String value = attributes.getValue(i);
        if (currentName.equals("Measurement") && name.equals("MeasurementID")) {
          measurementID = value;
        }
        else if (name.equals("Class")) {
          type = value;
        }
      }
      currentValue = new StringBuffer();

    }

    public void endElement(String uri, String localName, String qName) {
      if (currentName == null) {
        return;
      }

      String value = currentValue.toString();
      addGlobalMeta(currentName, value);

      if (currentName.equals("ScreenName")) {
        screenName = value;
      }
      else if (currentName.equals("PlateName")) {
        plateName = value;
      }
      else if (currentName.equals("PlateType")) {
        plateType = value;
      }
      else if (currentName.equals("Measurement")) {
        measurementName = value;
      }
      else if (currentName.equals("Reference")) {
        metadataFiles.add(value);
        if (type != null && type.equals("IMAGEINDEX") && value.endsWith(".xml")) {
          String path = new Location(currentId).getAbsoluteFile().getParent() + File.separator + value;
          imageIndexPaths.add(new Location(path).getAbsolutePath());
        }
      }
      else if (currentName.equals("PlateRows")) {
        plateRows = new Integer(value);
      }
      else if (currentName.equals("PlateColumns")) {
        plateColumns = new Integer(value);
      }

      currentName = null;
    }

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
