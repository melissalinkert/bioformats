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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import javax.xml.parsers.ParserConfigurationException;

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

import ome.xml.model.primitives.Color;
import ome.xml.model.primitives.NonNegativeInteger;
import ome.xml.model.primitives.PositiveFloat;
import ome.xml.model.primitives.PositiveInteger;
import ome.xml.model.primitives.Timestamp;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;



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
  private ArrayList<HarmonyColumbusPlane> planes = new ArrayList<HarmonyColumbusPlane>();
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
        plane.c == zct[1])
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
            if (!metadataFiles.contains(file + File.separator + f)) {
              metadataFiles.add(file + File.separator + f);
            }
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
      if (checkSuffix(path, "columbusidx.xml")) {
        int timepoint = timepointDirs.indexOf(timepointPath);
        if (timepointDirs.size() == 0) {
          timepoint = 0;
        }
        parseImageXML(path, timepoint);
      }
    }

    // process plane list to determine plate size

    HarmonyColumbusPlane[] tmpPlanes = planes.toArray(new HarmonyColumbusPlane[planes.size()]);
    Arrays.sort(tmpPlanes);
    planes.clear();

    reader = new MinimalTiffReader();
    reader.setId(tmpPlanes[0].filename);
    core = reader.getCoreMetadataList();

    CoreMetadata m = core.get(0);

    m.sizeC = 0;
    m.sizeT = 0;

    ArrayList<Integer> uniqueSamples = new ArrayList<Integer>();
    ArrayList<Integer> uniqueRows = new ArrayList<Integer>();
    ArrayList<Integer> uniqueCols = new ArrayList<Integer>();
    for (HarmonyColumbusPlane p : tmpPlanes) {
      planes.add(p);

      int sampleIndex = p.row * handler.getPlateColumns() + p.col;
      if (!uniqueSamples.contains(sampleIndex)) {
        uniqueSamples.add(sampleIndex);
      }
      if (!uniqueRows.contains(p.row)) {
        uniqueRows.add(p.row);
      }
      if (!uniqueCols.contains(p.col)) {
        uniqueCols.add(p.col);
      }

      // missing wells are allowed, but the field/channel/timepoint
      // counts are assumed to be non-sparse
      if (p.field >= nFields) {
        nFields = p.field + 1;
      }
      if (p.c >= getSizeC()) {
        m.sizeC = p.c + 1;
      }
      if (p.t >= getSizeT()) {
        m.sizeT = p.t + 1;
      }

    }

    m.sizeZ = 1;
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

    store.setScreenID(MetadataTools.createLSID("Screen", 0), 0);
    store.setScreenName(handler.getScreenName(), 0);
    store.setPlateID(MetadataTools.createLSID("Plate", 0), 0);
    store.setPlateName(handler.getPlateName(), 0);
    store.setPlateExternalIdentifier(plateID, 0);
    store.setPlateRows(new PositiveInteger(handler.getPlateRows()), 0);
    store.setPlateColumns(new PositiveInteger(handler.getPlateColumns()), 0);

    String imagePrefix = handler.getPlateName() + " Well ";
    int wellSample = 0;

    int nextWell = -1;
    Timestamp date = new Timestamp(acquisitionDate);
    long timestampSeconds = date.asInstant().getMillis() / 1000;

    for (Integer row : uniqueRows) {
      for (Integer col : uniqueCols) {
        if (!uniqueSamples.contains(row * handler.getPlateColumns() + col)) {
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
          }

          String imageID = MetadataTools.createLSID("Image", wellSample);
          store.setImageID(imageID, wellSample);
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
                store.setChannelName(p.channelName, p.series, p.c);
                if (p.emWavelength != null) {
                  store.setChannelEmissionWavelength(p.emWavelength, p.series, p.c);
                }
                if (p.exWavelength != null) {
                  store.setChannelExcitationWavelength(p.exWavelength, p.series, p.c);
                }
              }

              for (int t=0; t<getSizeT(); t++) {
                p = lookupPlane(row, col, field, t, c);
                if (p != null) {
                  p.series = wellSample;
                  store.setPlaneDeltaT(p.deltaT, p.series, getIndex(0, c, t));
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

    Element root = null;
    try {
      ByteArrayInputStream s =
        new ByteArrayInputStream(xml.getBytes(Constants.ENCODING));
      root = XMLTools.parseDOM(s).getDocumentElement();
      s.close();
    }
    catch (ParserConfigurationException e) {
      throw new FormatException(e);
    }
    catch (SAXException e) {
      throw new FormatException(e);
    }

    NodeList plates = root.getElementsByTagName("Plates");
    if (plates == null) {
      LOGGER.debug("Plates node not found");
      return;
    }
    plates = ((Element) plates.item(0)).getElementsByTagName("Plate");
    if (plates == null) {
      LOGGER.debug("Plate nodes not found");
      return;
    }

    Element firstPlate = (Element) plates.item(0);
    NodeList children = firstPlate.getChildNodes();
    for (int q=0; q<children.getLength(); q++) {
      Node child = children.item(q);
      String name = child.getNodeName();
      String value = child.getTextContent();
      /* debug */ System.out.println(name + " = " + value);
      if ("PlateID".equals(name)) {
        plateID = value;
      }
    }

    NodeList timestamps = firstPlate.getElementsByTagName("MeasurementStartTime");
    if (externalTime <= 0) {
      acquisitionDate = ((Element) timestamps.item(0)).getTextContent();
    }

    NodeList images = root.getElementsByTagName("Images");
    if (images == null) {
      LOGGER.debug("Images node not found");
      return;
    }
    images = ((Element) images.item(0)).getElementsByTagName("Image");
    if (images == null) {
      LOGGER.debug("Image nodes not found");
      return;
    }

    LOGGER.debug("Found {} image definitions", images.getLength());
    for (int i=0; i<images.getLength(); i++) {
      Element image = (Element) images.item(i);
      HarmonyColumbusPlane p = new HarmonyColumbusPlane();

      children = image.getChildNodes();
      for (int q=0; q<children.getLength(); q++) {
        Node child = children.item(q);
        String name = child.getNodeName();
        String value = child.getTextContent();
        NamedNodeMap attrs = child.getAttributes();
        Node unitNode = attrs.getNamedItem("Unit");
        String unit = unitNode == null ? null : unitNode.getNodeValue();
        if (name.equals("URL")) {
          p.filename = new Location(parent, value).getAbsolutePath();

          String buffer = attrs.getNamedItem("BufferNo").getNodeValue();
          p.fileIndex = Integer.parseInt(buffer);
        }
        else if (name.equals("Row")) {
          p.row = Integer.parseInt(value) - 1;
        }
        else if (name.equals("Col")) {
          p.col = Integer.parseInt(value) - 1;
        }
        else if (name.equals("FieldID")) {
          p.field = Integer.parseInt(value) - 1;
        }
        else if (name.equals("TimepointID")) {
          p.t = Integer.parseInt(value) - 1;
          if (p.t == 0) {
            p.t = externalTime;
          }
        }
        else if (name.equals("ChannelID")) {
          p.c = Integer.parseInt(value) - 1;
        }
        else if (name.equals("ChannelName")) {
          p.channelName = value;
        }
        else if (name.equals("MeasurementTimeOffset")) {
          p.setDeltaT(value, unit);
        }
        else if (name.equals("AbsTime")) {
          p.acqTime = value;
        }
        else if (name.equals("MainEmissionWavelength")) {
          p.setEmWavelength(value, unit);
        }
        else if (name.equals("MainExcitationWavelength")) {
          p.setExWavelength(value, unit);
        }
        else if (name.equals("ImageResolutionX")) {
          p.setResolutionX(value, unit);
        }
        else if (name.equals("ImageResolutionY")) {
          p.setResolutionY(value, unit);
        }
        else if (name.equals("PositionX")) {
          p.setPositionX(value, unit);
        }
        else if (name.equals("PositionY")) {
          p.setPositionY(value, unit);
        }
        else if (name.equals("PositionZ")) {
          p.setPositionZ(value, unit);
        }
      }

      planes.add(p);
    }

  }

  private HarmonyColumbusPlane lookupPlane(int row, int col, int field, int t, int c) {
    for (HarmonyColumbusPlane p : planes) {
      if (p.row == row && p.col == col && p.field == field &&
        p.t == t && p.c == c)
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

}
