//
// BaseTiffReader.java
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

package loci.formats.in;

import java.io.IOException;

import loci.common.DateTools;
import loci.formats.FormatException;
import loci.formats.MetadataTools;
import loci.formats.meta.FilterMetadata;
import loci.formats.meta.MetadataStore;
import loci.formats.tiff.IFD;
import loci.formats.tiff.PhotoInterp;
import loci.formats.tiff.TiffCompression;
import loci.formats.tiff.TiffRational;

/**
 * BaseTiffReader is the superclass for file format readers compatible with
 * or derived from the TIFF 6.0 file format.
 *
 * <dl><dt><b>Source code:</b></dt>
 * <dd><a href="https://skyking.microscopy.wisc.edu/trac/java/browser/trunk/components/bio-formats/src/loci/formats/in/BaseTiffReader.java">Trac</a>,
 * <a href="https://skyking.microscopy.wisc.edu/svn/java/trunk/components/bio-formats/src/loci/formats/in/BaseTiffReader.java">SVN</a></dd></dl>
 *
 * @author Curtis Rueden ctrueden at wisc.edu
 * @author Melissa Linkert linkert at wisc.edu
 */
public abstract class BaseTiffReader extends MinimalTiffReader {

  // -- Constants --

  public static final String[] DATE_FORMATS = {
    "yyyy:MM:dd HH:mm:ss",
    "dd/MM/yyyy HH:mm:ss.SS",
    "MM/dd/yyyy hh:mm:ss.SSS aa"
  };

  /** EXIF tags. */
  private static final int EXIF_VERSION = 36864;
  private static final int FLASH_PIX_VERSION = 40960;
  private static final int COLOR_SPACE = 40961;
  private static final int COMPONENTS_CONFIGURATION = 37121;
  private static final int COMPRESSED_BITS_PER_PIXEL = 37122;
  private static final int PIXEL_X_DIMENSION = 40962;
  private static final int PIXEL_Y_DIMENSION = 40963;
  private static final int MAKER_NOTE = 37500;
  private static final int USER_COMMENT = 37510;
  private static final int RELATED_SOUND_FILE = 40964;
  private static final int DATE_TIME_ORIGINAL = 36867;
  private static final int DATE_TIME_DIGITIZED = 36868;
  private static final int SUB_SEC_TIME = 37520;
  private static final int SUB_SEC_TIME_ORIGINAL = 37521;
  private static final int SUB_SEC_TIME_DIGITIZED = 37522;
  private static final int EXPOSURE_TIME = 33434;
  private static final int F_NUMBER = 33437;
  private static final int EXPOSURE_PROGRAM = 34850;
  private static final int SPECTRAL_SENSITIVITY = 34852;
  private static final int ISO_SPEED_RATINGS = 34855;
  private static final int OECF = 34856;
  private static final int SHUTTER_SPEED_VALUE = 37377;
  private static final int APERTURE_VALUE = 37378;
  private static final int BRIGHTNESS_VALUE = 37379;
  private static final int EXPOSURE_BIAS_VALUE = 37380;
  private static final int MAX_APERTURE_VALUE = 37381;
  private static final int SUBJECT_DISTANCE = 37382;
  private static final int METERING_MODE = 37383;
  private static final int LIGHT_SOURCE = 37384;
  private static final int FLASH = 37385;
  private static final int FOCAL_LENGTH = 37386;
  private static final int FLASH_ENERGY = 41483;
  private static final int SPATIAL_FREQUENCY_RESPONSE = 41484;
  private static final int FOCAL_PLANE_X_RESOLUTION = 41486;
  private static final int FOCAL_PLANE_Y_RESOLUTION = 41487;
  private static final int FOCAL_PLANE_RESOLUTION_UNIT = 41488;
  private static final int SUBJECT_LOCATION = 41492;
  private static final int EXPOSURE_INDEX = 41493;
  private static final int SENSING_METHOD = 41495;
  private static final int FILE_SOURCE = 41728;
  private static final int SCENE_TYPE = 41729;
  private static final int CFA_PATTERN = 41730;

  // -- Constructors --

  /** Constructs a new BaseTiffReader. */
  public BaseTiffReader(String name, String suffix) { super(name, suffix); }

  /** Constructs a new BaseTiffReader. */
  public BaseTiffReader(String name, String[] suffixes) {
    super(name, suffixes);
  }

  // -- Internal BaseTiffReader API methods --

  /** Populates the metadata hashtable and metadata store. */
  protected void initMetadata() throws FormatException, IOException {
    initStandardMetadata();
    initMetadataStore();
  }

  /**
   * Parses standard metadata.
   *
   * NOTE: Absolutely <b>no</b> calls to the metadata store should be made in
   * this method or methods that override this method. Data <b>will</b> be
   * overwritten if you do so.
   */
  protected void initStandardMetadata() throws FormatException, IOException {
    IFD firstIFD = ifds.get(0);
    put("ImageWidth", firstIFD, IFD.IMAGE_WIDTH);
    put("ImageLength", firstIFD, IFD.IMAGE_LENGTH);
    put("BitsPerSample", firstIFD, IFD.BITS_PER_SAMPLE);

    // retrieve EXIF values, if available

    long exifOffset = firstIFD.getIFDLongValue(IFD.EXIF, false, 0);
    if (exifOffset != 0) {
      IFD exif = tiffParser.getIFD(1, exifOffset);
      if (exif != null) {
        for (Integer key : exif.keySet()) {
          int k = key.intValue();
          addGlobalMeta(getExifTagName(k), exif.get(key));
        }
      }
    }

    int comp = firstIFD.getCompression();
    put("Compression", TiffCompression.getCodecName(comp));

    int photo = firstIFD.getPhotometricInterpretation();
    String photoInterp = PhotoInterp.getPIName(photo);
    String metaDataPhotoInterp = PhotoInterp.getPIMeta(photo);
    put("PhotometricInterpretation", photoInterp);
    put("MetaDataPhotometricInterpretation", metaDataPhotoInterp);

    putInt("CellWidth", firstIFD, IFD.CELL_WIDTH);
    putInt("CellLength", firstIFD, IFD.CELL_LENGTH);

    int or = firstIFD.getIFDIntValue(IFD.ORIENTATION);

    // adjust the width and height if necessary
    if (or == 8) {
      put("ImageWidth", firstIFD, IFD.IMAGE_LENGTH);
      put("ImageLength", firstIFD, IFD.IMAGE_WIDTH);
    }

    String orientation = null;
    // there is no case 0
    switch (or) {
      case 1:
        orientation = "1st row -> top; 1st column -> left";
        break;
      case 2:
        orientation = "1st row -> top; 1st column -> right";
        break;
      case 3:
        orientation = "1st row -> bottom; 1st column -> right";
        break;
      case 4:
        orientation = "1st row -> bottom; 1st column -> left";
        break;
      case 5:
        orientation = "1st row -> left; 1st column -> top";
        break;
      case 6:
        orientation = "1st row -> right; 1st column -> top";
        break;
      case 7:
        orientation = "1st row -> right; 1st column -> bottom";
        break;
      case 8:
        orientation = "1st row -> left; 1st column -> bottom";
        break;
    }
    put("Orientation", orientation);
    putInt("SamplesPerPixel", firstIFD, IFD.SAMPLES_PER_PIXEL);

    put("Software", firstIFD, IFD.SOFTWARE);
    put("Instrument Make", firstIFD, IFD.MAKE);
    put("Instrument Model", firstIFD, IFD.MODEL);
    put("Document Name", firstIFD, IFD.DOCUMENT_NAME);
    put("DateTime", firstIFD, IFD.DATE_TIME);
    put("Artist", firstIFD, IFD.ARTIST);

    put("HostComputer", firstIFD, IFD.HOST_COMPUTER);
    put("Copyright", firstIFD, IFD.COPYRIGHT);

    put("NewSubfileType", firstIFD, IFD.NEW_SUBFILE_TYPE);

    int thresh = firstIFD.getIFDIntValue(IFD.THRESHHOLDING);
    String threshholding = null;
    switch (thresh) {
      case 1:
        threshholding = "No dithering or halftoning";
        break;
      case 2:
        threshholding = "Ordered dithering or halftoning";
        break;
      case 3:
        threshholding = "Randomized error diffusion";
        break;
    }
    put("Threshholding", threshholding);

    int fill = firstIFD.getIFDIntValue(IFD.FILL_ORDER);
    String fillOrder = null;
    switch (fill) {
      case 1:
        fillOrder = "Pixels with lower column values are stored " +
          "in the higher order bits of a byte";
        break;
      case 2:
        fillOrder = "Pixels with lower column values are stored " +
          "in the lower order bits of a byte";
        break;
    }
    put("FillOrder", fillOrder);

    putInt("Make", firstIFD, IFD.MAKE);
    putInt("Model", firstIFD, IFD.MODEL);
    putInt("MinSampleValue", firstIFD, IFD.MIN_SAMPLE_VALUE);
    putInt("MaxSampleValue", firstIFD, IFD.MAX_SAMPLE_VALUE);
    putInt("XResolution", firstIFD, IFD.X_RESOLUTION);
    putInt("YResolution", firstIFD, IFD.Y_RESOLUTION);

    int planar = firstIFD.getIFDIntValue(IFD.PLANAR_CONFIGURATION);
    String planarConfig = null;
    switch (planar) {
      case 1:
        planarConfig = "Chunky";
        break;
      case 2:
        planarConfig = "Planar";
        break;
    }
    put("PlanarConfiguration", planarConfig);

    putInt("XPosition", firstIFD, IFD.X_POSITION);
    putInt("YPosition", firstIFD, IFD.Y_POSITION);
    putInt("FreeOffsets", firstIFD, IFD.FREE_OFFSETS);
    putInt("FreeByteCounts", firstIFD, IFD.FREE_BYTE_COUNTS);
    putInt("GrayResponseUnit", firstIFD, IFD.GRAY_RESPONSE_UNIT);
    putInt("GrayResponseCurve", firstIFD, IFD.GRAY_RESPONSE_CURVE);
    putInt("T4Options", firstIFD, IFD.T4_OPTIONS);
    putInt("T6Options", firstIFD, IFD.T6_OPTIONS);

    int res = firstIFD.getIFDIntValue(IFD.RESOLUTION_UNIT);
    String resUnit = null;
    switch (res) {
      case 1:
        resUnit = "None";
        break;
      case 2:
        resUnit = "Inch";
        break;
      case 3:
        resUnit = "Centimeter";
        break;
    }
    put("ResolutionUnit", resUnit);

    putInt("PageNumber", firstIFD, IFD.PAGE_NUMBER);
    putInt("TransferFunction", firstIFD, IFD.TRANSFER_FUNCTION);

    int predict = firstIFD.getIFDIntValue(IFD.PREDICTOR);
    String predictor = null;
    switch (predict) {
      case 1:
        predictor = "No prediction scheme";
        break;
      case 2:
        predictor = "Horizontal differencing";
        break;
    }
    put("Predictor", predictor);

    putInt("WhitePoint", firstIFD, IFD.WHITE_POINT);
    putInt("PrimaryChromacities", firstIFD, IFD.PRIMARY_CHROMATICITIES);

    putInt("HalftoneHints", firstIFD, IFD.HALFTONE_HINTS);
    putInt("TileWidth", firstIFD, IFD.TILE_WIDTH);
    putInt("TileLength", firstIFD, IFD.TILE_LENGTH);
    putInt("TileOffsets", firstIFD, IFD.TILE_OFFSETS);
    putInt("TileByteCounts", firstIFD, IFD.TILE_BYTE_COUNTS);

    int ink = firstIFD.getIFDIntValue(IFD.INK_SET);
    String inkSet = null;
    switch (ink) {
      case 1:
        inkSet = "CMYK";
        break;
      case 2:
        inkSet = "Other";
        break;
    }
    put("InkSet", inkSet);

    putInt("InkNames", firstIFD, IFD.INK_NAMES);
    putInt("NumberOfInks", firstIFD, IFD.NUMBER_OF_INKS);
    putInt("DotRange", firstIFD, IFD.DOT_RANGE);
    put("TargetPrinter", firstIFD, IFD.TARGET_PRINTER);
    putInt("ExtraSamples", firstIFD, IFD.EXTRA_SAMPLES);

    int fmt = firstIFD.getIFDIntValue(IFD.SAMPLE_FORMAT);
    String sampleFormat = null;
    switch (fmt) {
      case 1:
        sampleFormat = "unsigned integer";
        break;
      case 2:
        sampleFormat = "two's complement signed integer";
        break;
      case 3:
        sampleFormat = "IEEE floating point";
        break;
      case 4:
        sampleFormat = "undefined";
        break;
    }
    put("SampleFormat", sampleFormat);

    putInt("SMinSampleValue", firstIFD, IFD.S_MIN_SAMPLE_VALUE);
    putInt("SMaxSampleValue", firstIFD, IFD.S_MAX_SAMPLE_VALUE);
    putInt("TransferRange", firstIFD, IFD.TRANSFER_RANGE);

    int jpeg = firstIFD.getIFDIntValue(IFD.JPEG_PROC);
    String jpegProc = null;
    switch (jpeg) {
      case 1:
        jpegProc = "baseline sequential process";
        break;
      case 14:
        jpegProc = "lossless process with Huffman coding";
        break;
    }
    put("JPEGProc", jpegProc);

    putInt("JPEGInterchangeFormat", firstIFD, IFD.JPEG_INTERCHANGE_FORMAT);
    putInt("JPEGRestartInterval", firstIFD, IFD.JPEG_RESTART_INTERVAL);

    putInt("JPEGLosslessPredictors", firstIFD, IFD.JPEG_LOSSLESS_PREDICTORS);
    putInt("JPEGPointTransforms", firstIFD, IFD.JPEG_POINT_TRANSFORMS);
    putInt("JPEGQTables", firstIFD, IFD.JPEG_Q_TABLES);
    putInt("JPEGDCTables", firstIFD, IFD.JPEG_DC_TABLES);
    putInt("JPEGACTables", firstIFD, IFD.JPEG_AC_TABLES);
    putInt("YCbCrCoefficients", firstIFD, IFD.Y_CB_CR_COEFFICIENTS);

    int ycbcr = firstIFD.getIFDIntValue(IFD.Y_CB_CR_SUB_SAMPLING);
    String subSampling = null;
    switch (ycbcr) {
      case 1:
        subSampling = "chroma image dimensions = luma image dimensions";
        break;
      case 2:
        subSampling = "chroma image dimensions are " +
          "half the luma image dimensions";
        break;
      case 4:
        subSampling = "chroma image dimensions are " +
          "1/4 the luma image dimensions";
        break;
    }
    put("YCbCrSubSampling", subSampling);

    putInt("YCbCrPositioning", firstIFD, IFD.Y_CB_CR_POSITIONING);
    putInt("ReferenceBlackWhite", firstIFD, IFD.REFERENCE_BLACK_WHITE);

    // bits per sample and number of channels
    int[] q = firstIFD.getBitsPerSample();
    int bps = q[0];
    int numC = q.length;

    // numC isn't set properly if we have an indexed color image, so we need
    // to reset it here

    if (photo == PhotoInterp.RGB_PALETTE || photo == PhotoInterp.CFA_ARRAY) {
      numC = 3;
    }

    put("BitsPerSample", bps);
    put("NumberOfChannels", numC);

    // TIFF comment
    String comment = firstIFD.getComment();

    int samples = firstIFD.getSamplesPerPixel();
    core[0].rgb = samples > 1 || photo == PhotoInterp.RGB;
    core[0].interleaved = false;
    core[0].littleEndian = firstIFD.isLittleEndian();

    core[0].sizeX = (int) firstIFD.getImageWidth();
    core[0].sizeY = (int) firstIFD.getImageLength();
    core[0].sizeZ = 1;
    core[0].sizeC = isRGB() ? samples : 1;
    core[0].sizeT = ifds.size();
    core[0].metadataComplete = true;
    core[0].indexed = photo == PhotoInterp.RGB_PALETTE &&
      (get8BitLookupTable() != null || get16BitLookupTable() != null);
    if (isIndexed()) {
      core[0].sizeC = 1;
      core[0].rgb = false;
    }
    if (getSizeC() == 1 && !isIndexed()) core[0].rgb = false;
    core[0].falseColor = false;
    core[0].dimensionOrder = "XYCZT";
    core[0].pixelType = firstIFD.getPixelType();
  }

  /**
   * Populates the metadata store using the data parsed in
   * {@link #initStandardMetadata()} along with some further parsing done in
   * the method itself.
   *
   * All calls to the active <code>MetadataStore</code> should be made in this
   * method and <b>only</b> in this method. This is especially important for
   * sub-classes that override the getters for pixel set array size, etc.
   */
  protected void initMetadataStore() throws FormatException {
    status("Populating OME metadata");

    // the metadata store we're working with
    MetadataStore store =
      new FilterMetadata(getMetadataStore(), isMetadataFiltered());
    MetadataTools.populatePixels(store, this);

    IFD firstIFD = ifds.get(0);

    // populate Experimenter
    String artist = null;
    Object o = firstIFD.getIFDValue(IFD.ARTIST);
    if (o instanceof String) artist = (String) o;
    else if (o instanceof String[]) {
      String[] s = (String[]) o;
      for (int i=0; i<s.length; i++) {
        artist += s[i];
        if (i < s.length - 1) artist += "\n";
      }
    }
    if (artist != null) {
      String firstName = null, lastName = null;
      int ndx = artist.indexOf(" ");
      if (ndx < 0) lastName = artist;
      else {
        firstName = artist.substring(0, ndx);
        lastName = artist.substring(ndx + 1);
      }
      String email = (String) firstIFD.getIFDValue(IFD.HOST_COMPUTER);
      store.setExperimenterFirstName(firstName, 0);
      store.setExperimenterLastName(lastName, 0);
      store.setExperimenterEmail(email, 0);
    }

    // format the creation date to ISO 8061

    String creationDate = getImageCreationDate();
    String date = DateTools.formatDate(creationDate, DATE_FORMATS);
    if (creationDate != null && date == null) {
      warnDebug("unknown creation date format: " + creationDate);
    }
    creationDate = date;

    // populate Image

    if (creationDate != null) {
      store.setImageCreationDate(creationDate, 0);
    }
    else {
       MetadataTools.setDefaultCreationDate(store, getCurrentFile(), 0);
    }
    store.setImageDescription(firstIFD.getComment(), 0);

    // set the X and Y pixel dimensions

    int resolutionUnit = firstIFD.getIFDIntValue(IFD.RESOLUTION_UNIT);
    TiffRational xResolution = firstIFD.getIFDRationalValue(
      IFD.X_RESOLUTION, false);
    TiffRational yResolution = firstIFD.getIFDRationalValue(
      IFD.Y_RESOLUTION, false);
    float pixX = xResolution == null ? 0f : 1 / xResolution.floatValue();
    float pixY = yResolution == null ? 0f : 1 / yResolution.floatValue();

    switch (resolutionUnit) {
      case 2:
        // resolution is expressed in pixels per inch
        pixX *= 25400;
        pixY *= 25400;
        break;
      case 3:
        // resolution is expressed in pixels per centimeter
        pixX *= 10000;
        pixY *= 10000;
        break;
    }

    store.setDimensionsPhysicalSizeX(new Float(pixX), 0, 0);
    store.setDimensionsPhysicalSizeY(new Float(pixY), 0, 0);
    store.setDimensionsPhysicalSizeZ(new Float(0), 0, 0);

    // populate StageLabel
    Object x = firstIFD.getIFDValue(IFD.X_POSITION);
    Object y = firstIFD.getIFDValue(IFD.Y_POSITION);
    Float stageX;
    Float stageY;
    if (x instanceof TiffRational) {
      stageX = x == null ? null : new Float(((TiffRational) x).floatValue());
      stageY = y == null ? null : new Float(((TiffRational) y).floatValue());
    }
    else {
      stageX = x == null ? null : new Float((String) x);
      stageY = y == null ? null : new Float((String) y);
    }
    // populate Instrument
    //String make = ifd.getIFDStringValue(IFD.MAKE, false);
    //String model = ifd.getIFDStringValue(IFD.MODEL, false);
    //store.setInstrumentModel(model, 0);
    //store.setInstrumentManufacturer(make, 0);
  }

  /**
   * Retrieves the image creation date.
   * @return the image creation date.
   */
  protected String getImageCreationDate() {
    Object o = ifds.get(0).getIFDValue(IFD.DATE_TIME);
    if (o instanceof String) return (String) o;
    if (o instanceof String[]) return ((String[]) o)[0];
    return null;
  }

  // -- Internal FormatReader API methods - metadata convenience --

  // TODO : the 'put' methods that accept primitive types could probably be
  // removed, as there are now 'addGlobalMeta' methods that accept
  // primitive types

  protected void put(String key, Object value) {
    if (value == null) return;
    if (value instanceof String) value = ((String) value).trim();
    addGlobalMeta(key, value);
  }

  protected void put(String key, int value) {
    if (value == -1) return; // indicates missing value
    addGlobalMeta(key, value);
  }

  protected void put(String key, boolean value) {
    put(key, new Boolean(value));
  }
  protected void put(String key, byte value) { put(key, new Byte(value)); }
  protected void put(String key, char value) { put(key, new Character(value)); }
  protected void put(String key, double value) { put(key, new Double(value)); }
  protected void put(String key, float value) { put(key, new Float(value)); }
  protected void put(String key, long value) { put(key, new Long(value)); }
  protected void put(String key, short value) { put(key, new Short(value)); }

  protected void put(String key, IFD ifd, int tag) {
    put(key, ifd.getIFDValue(tag));
  }

  protected void putInt(String key, IFD ifd, int tag) {
    put(key, ifd.getIFDIntValue(tag));
  }

  // -- Internal FormatReader API methods --

  /* @see loci.formats.FormatReader#initFile(String) */
  protected void initFile(String id) throws FormatException, IOException {
    debug("BaseTiffReader.initFile(" + id + ")");
    super.initFile(id);
    initMetadata();
  }

  // -- Helper methods --

  public static String getExifTagName(int tag) {
    switch (tag) {
      case EXIF_VERSION:
        return "EXIF Version";
      case FLASH_PIX_VERSION:
        return "FlashPix Version";
      case COLOR_SPACE:
        return "Color Space";
      case COMPONENTS_CONFIGURATION:
        return "Components Configuration";
      case COMPRESSED_BITS_PER_PIXEL:
        return "Compressed Bits Per Pixel";
      case PIXEL_X_DIMENSION:
        return "Image width";
      case PIXEL_Y_DIMENSION:
        return "Image height";
      case MAKER_NOTE:
        return "Maker Note";
      case USER_COMMENT:
        return "User comment";
      case RELATED_SOUND_FILE:
        return "Related sound file";
      case DATE_TIME_ORIGINAL:
        return "Original date/time";
      case DATE_TIME_DIGITIZED:
        return "Date/time digitized";
      case SUB_SEC_TIME:
        return "Date/time subseconds";
      case SUB_SEC_TIME_ORIGINAL:
        return "Original date/time subseconds";
      case SUB_SEC_TIME_DIGITIZED:
        return "Digitized date/time subseconds";
      case EXPOSURE_TIME:
        return "Exposure time";
      case F_NUMBER:
        return "F Number";
      case EXPOSURE_PROGRAM:
        return "Exposure program";
      case SPECTRAL_SENSITIVITY:
        return "Spectral sensitivity";
      case ISO_SPEED_RATINGS:
        return "ISO speed ratings";
      case OECF:
        return "Optoelectric conversion factor";
      case SHUTTER_SPEED_VALUE:
        return "Shutter speed";
      case APERTURE_VALUE:
        return "Aperture value";
      case BRIGHTNESS_VALUE:
        return "Brightness value";
      case EXPOSURE_BIAS_VALUE:
        return "Exposure Bias value";
      case MAX_APERTURE_VALUE:
        return "Max aperture value";
      case SUBJECT_DISTANCE:
        return "Subject distance";
      case METERING_MODE:
        return "Metering mode";
      case LIGHT_SOURCE:
        return "Light source";
      case FLASH:
        return "Flash";
      case FOCAL_LENGTH:
        return "Focal length";
      case FLASH_ENERGY:
        return "Flash energy";
      case SPATIAL_FREQUENCY_RESPONSE:
        return "Spatial frequency response";
      case FOCAL_PLANE_X_RESOLUTION:
        return "Focal plane X resolution";
      case FOCAL_PLANE_Y_RESOLUTION:
        return "Focal plane Y resolution";
      case FOCAL_PLANE_RESOLUTION_UNIT:
        return "Focal plane resolution unit";
      case SUBJECT_LOCATION:
        return "Subject location";
      case EXPOSURE_INDEX:
        return "Exposure index";
      case SENSING_METHOD:
        return "Sensing method";
      case FILE_SOURCE:
        return "File source";
      case SCENE_TYPE:
        return "Scene type";
      case CFA_PATTERN:
        return "CFA Pattern";
    }
    return null;
  }

}